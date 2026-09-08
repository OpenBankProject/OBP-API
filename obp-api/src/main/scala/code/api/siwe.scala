/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

  */
package code.api

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

import code.api.cache.Redis
import code.api.util.APIUtil.HTTPParam
import code.api.util._
import code.consumer.Consumers
import code.model.{TokenType, UserX}
import code.token.Tokens
import code.util.Helper.MdcLoggable
import com.nimbusds.jwt.JWTClaimsSet
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import net.liftweb.common._
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo
import org.web3j.crypto.{Keys, Sign}
import org.web3j.utils.Numeric

import scala.concurrent.Future

/**
 * SIWE — Sign-In With Ethereum (EIP-4361) authentication method.
 *
 * A new sibling to DAuth. Where DAuth's relay merely *vouches for* an asserted
 * smart-contract address, SIWE makes the live caller *cryptographically prove*
 * control of a wallet. Phase 1 supports Externally-Owned Accounts (EOA) only —
 * verification is pure local `ecrecover` with zero RPC dependency. Smart-contract
 * / multisig wallets (EIP-1271) are a planned, additive Phase 2.
 *
 * Flow (modelled on DirectLogin's exchange-once-for-a-token shape):
 *   1. POST /my/logins/siwe/challenge  → server issues { nonce, message, expires_at }
 *   2. client signs the EIP-4361 message off-OBP
 *   3. POST /my/logins/siwe            → server verifies + mints an OBP token
 *   4. subsequent calls carry  `SIWE: token=...`  (validated here)
 *
 * The feature is OFF by default; everything below is inert unless `allow_siwe=true`.
 *
 * Routes live in [[code.api.SIWERoutes]]; the subsequent-request branch is wired
 * into `APIUtil.getUserAndSessionContextFuture`.
 */
object SIWE extends MdcLoggable {

  // ---- request / response bodies -------------------------------------------

  /** POST /my/logins/siwe/challenge body. */
  case class SiweChallengeRequest(
    address: String,
    chain_id: Long,
    uri: Option[String],
    statement: Option[String]
  )

  /** POST /my/logins/siwe/challenge response. */
  case class SiweChallengeResponse(
    nonce: String,
    message: String,
    expires_at: String
  )

  /** POST /my/logins/siwe body. */
  case class SiweLoginRequest(
    message: String,
    signature: String,
    consumer_key: Option[String]
  )

  /** Fields extracted from a parsed EIP-4361 message. */
  case class SiweParsedMessage(
    domain: String,
    address: String,
    chainId: Long,
    nonce: String,
    issuedAt: Option[String],
    expirationTime: Option[String],
    uri: Option[String]
  )

  // ---- props ---------------------------------------------------------------

  // OFF by default. Nothing in this object does anything until this is true.
  def isEnabled: Boolean = APIUtil.getPropsAsBoolValue("allow_siwe", false)

  /** Expected `domain` field of the SIWE message (anti-phishing). Required when enabled. */
  def domainProp: Box[String] = APIUtil.getPropsValue("siwe.domain").filter(_.trim.nonEmpty)

  /** Comma-separated EVM chain IDs accepted, e.g. "1,11155111". */
  def allowedChainIds: List[Long] =
    APIUtil.getPropsValue("siwe.allowed_chain_ids", "")
      .split(",").map(_.trim).filter(_.nonEmpty)
      .flatMap(s => tryo(s.toLong).toList).toList

  /** Challenge nonce lifetime in seconds (default 300 = 5 min). */
  def nonceTtlSeconds: Int = APIUtil.getPropsAsIntValue("siwe.nonce.ttl", 300)

  // ---- header constants & parsing ------------------------------------------

  val SiweHeaderKey = "SIWE"

  def hasSiweHeader(requestHeaders: List[HTTPParam]): Boolean =
    requestHeaders.exists(_.name.equalsIgnoreCase(SiweHeaderKey))

  /** Parse `SIWE: token=<key>` → Some(key). Mirrors DirectLogin's `token=` parsing. */
  def getSiweToken(requestHeaders: List[HTTPParam]): Option[String] = {
    val raw = requestHeaders
      .find(_.name.equalsIgnoreCase(SiweHeaderKey))
      .flatMap(_.values.headOption)
      .getOrElse("")
    raw.split(",").map(_.trim).flatMap { entry =>
      if (entry.startsWith("token")) {
        val parts = entry.split("=", 2)
        if (parts.length == 2) Some(parts(1).replaceAll("^\"|\"$", "")) else None
      } else None
    }.headOption.filter(_.nonEmpty)
  }

  // ---- validation helpers --------------------------------------------------

  private val ethAddressRegex = "^0x[0-9a-fA-F]{40}$".r

  def isValidEthAddress(address: String): Boolean =
    Option(address).exists(a => ethAddressRegex.findFirstIn(a.trim).isDefined)

  /** EIP-55 checksummed form of an address (stable, canonical username). */
  def toChecksum(address: String): String = Keys.toChecksumAddress(address.trim)

  /** True only when an expiration time is present AND already in the past. */
  def isExpired(expirationTime: Option[String]): Boolean = expirationTime match {
    case Some(s) => tryo(Instant.parse(s.trim)) match {
      case Full(instant) => instant.isBefore(Instant.now())
      case _             => false // unparseable → leave to message-malformed handling, don't reject here
    }
    case None => false
  }

  // ---- nonce lifecycle (Redis, single-use) ---------------------------------

  private def nonceKey(nonce: String): String = s"siwe:nonce:$nonce"

  def generateNonce(): String = Helpers.randomString(32)

  /** Store nonce → claimed address (checksummed) with a short TTL. */
  def storeNonce(nonce: String, checksumAddress: String): Unit =
    Redis.use(JedisMethod.SET, nonceKey(nonce), Some(nonceTtlSeconds), Some(checksumAddress))

  /** Consume a nonce single-use: return the address it was issued for, then delete it. */
  def consumeNonce(nonce: String): Option[String] = {
    val stored = Redis.use(JedisMethod.GET, nonceKey(nonce))
    stored.foreach(_ => Redis.use(JedisMethod.DELETE, nonceKey(nonce)))
    stored
  }

  // ---- EIP-4361 message build & parse --------------------------------------

  def buildMessage(
    domain: String,
    checksumAddress: String,
    chainId: Long,
    nonce: String,
    uri: String,
    statement: String,
    issuedAt: String,
    expirationTime: String
  ): String =
    s"""$domain wants you to sign in with your Ethereum account:
       |$checksumAddress
       |
       |$statement
       |
       |URI: $uri
       |Version: 1
       |Chain ID: $chainId
       |Nonce: $nonce
       |Issued At: $issuedAt
       |Expiration Time: $expirationTime""".stripMargin

  def parseMessage(message: String): Box[SiweParsedMessage] = tryo {
    def field(label: String): Option[String] =
      s"(?m)^${java.util.regex.Pattern.quote(label)}: (.+)$$".r
        .findFirstMatchIn(message).map(_.group(1).trim)

    val firstLine = message.split("\n", -1).headOption.getOrElse("")
    val domain = firstLine.replace(" wants you to sign in with your Ethereum account:", "").trim
    val address = "(?m)^(0x[0-9a-fA-F]{40})\\s*$".r
      .findFirstMatchIn(message).map(_.group(1))
      .getOrElse(throw new RuntimeException("SIWE message has no address line"))
    val chainId = field("Chain ID").map(_.toLong)
      .getOrElse(throw new RuntimeException("SIWE message has no Chain ID"))
    val nonce = field("Nonce")
      .getOrElse(throw new RuntimeException("SIWE message has no Nonce"))

    SiweParsedMessage(
      domain = domain,
      address = address,
      chainId = chainId,
      nonce = nonce,
      issuedAt = field("Issued At"),
      expirationTime = field("Expiration Time"),
      uri = field("URI")
    )
  }

  // ---- EOA signature recovery (Phase 1) ------------------------------------

  /**
   * Recover the EIP-191 personal_sign signer from a 65-byte secp256k1 signature.
   * `signedPrefixedMessageToKey` applies the "\x19Ethereum Signed Message:\n<len>"
   * prefix, matching what wallets sign for an EIP-4361 message.
   * @return the recovered address, EIP-55 checksummed.
   */
  def recoverEoaAddress(message: String, signatureHex: String): Box[String] = tryo {
    val sig = Numeric.hexStringToByteArray(signatureHex.trim)
    if (sig.length != 65) throw new RuntimeException("signature must be 65 bytes")
    val r = java.util.Arrays.copyOfRange(sig, 0, 32)
    val s = java.util.Arrays.copyOfRange(sig, 32, 64)
    val vRaw = sig(64) & 0xff
    val v = (if (vRaw < 27) vRaw + 27 else vRaw).toByte
    val signatureData = new Sign.SignatureData(v, r, s)
    val publicKey = Sign.signedPrefixedMessageToKey(
      message.getBytes(StandardCharsets.UTF_8), signatureData)
    Keys.toChecksumAddress("0x" + Keys.getAddress(publicKey))
  }

  // ---- user + token --------------------------------------------------------

  /** Get-or-create the OBP user for this wallet, namespaced by chain so SIWE and
   *  DAuth identities (and different chains) never collide. */
  def getOrCreateUser(chainId: Long, checksumAddress: String): Box[User] = {
    val provider = "siwe." + chainId
    AuthRateLimiter.check(APIUtil.getRemoteIpAddress(), provider, checksumAddress) match {
      case Left(_)  => Failure(ErrorMessages.TooManyRequestsAuth)
      case Right(_) => UserX.getOrCreateDauthResourceUser(provider, checksumAddress)
    }
  }

  /** Mint an OBP token using the same store DirectLogin uses (token table + JWT/HMAC). */
  def mintToken(userId: Long, consumerKey: Option[String]): Box[String] = tryo {
    val secret = Helpers.randomString(40)
    val jwtClaims = JWTClaimsSet.parse("""{"":""}""")
    val tokenKey = CertificateUtil.jwtWithHmacProtection(jwtClaims, secret)
    val consumerId = consumerKey.flatMap { key =>
      Consumers.consumers.vend.getConsumerByConsumerKey(key) match {
        case Full(consumer) => Some(consumer.id.get)
        case _              => None
      }
    }
    val currentTime = System.currentTimeMillis()
    val tokenDuration = Helpers.weeks(APIUtil.getPropsAsIntValue("token_expiration_weeks", 4))
    Tokens.tokens.vend.createToken(
      TokenType.Access,
      consumerId,
      Some(userId),
      Some(tokenKey),
      Some(secret),
      Some(tokenDuration),
      Some(new Date(currentTime + tokenDuration)),
      Some(new Date(currentTime)),
      None
    ) match {
      case Full(_) => tokenKey
      case _       => throw new RuntimeException("Could not persist SIWE token")
    }
  }

  // ---- subsequent-request validation (the `SIWE: token=...` header) ---------

  /**
   * Resolve the User behind a SIWE-minted session token. The token lives in the
   * shared `token` table (same machinery as DirectLogin), so we look it up by key
   * and resolve the user already created at login.
   */
  def getUserFromSiweHeaderFuture(cc: CallContext): Future[(Box[User], Option[CallContext])] = {
    getSiweToken(cc.requestHeaders) match {
      case Some(tokenKey) =>
        Tokens.tokens.vend.getTokenByKeyAndTypeFuture(tokenKey, TokenType.Access) map {
          case Full(token) =>
            val user = token.user
            (user, Some(cc.copy(user = user, consumer = token.consumer)))
          case _ =>
            (Failure(ErrorMessages.DirectLoginInvalidToken + tokenKey), Some(cc))
        }
      case None =>
        Future.successful((Failure(ErrorMessages.DirectLoginInvalidToken), Some(cc)))
    }
  }
}
