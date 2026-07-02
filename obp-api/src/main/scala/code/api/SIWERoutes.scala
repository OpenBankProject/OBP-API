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

import java.time.Instant

import cats.effect.IO
import code.api.SIWE.{SiweChallengeRequest, SiweChallengeResponse, SiweLoginRequest}
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sRequestAttributes}
import code.api.util.{CallContext, CustomJsonFormats}
import code.util.Helper.booleanToFuture
import com.openbankproject.commons.ExecutionContext.Implicits.global
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

/**
 * Native http4s routes for the non-versioned SIWE (Sign-In With Ethereum) endpoints.
 *
 *   POST /my/logins/siwe/challenge   (anonymous) → { nonce, message, expires_at }
 *   POST /my/logins/siwe             (anonymous) → { token }
 *
 * Sibling to [[code.api.DirectLoginRoutes]]; wired into `Http4sApp.baseServices`.
 * Both endpoints are anonymous (no SIWE header is carried), so the central auth
 * dispatch in `APIUtil.getUserAndSessionContextFuture` skips them — exactly as it
 * skips `/my/logins/direct`.
 *
 * The whole route is inert (`HttpRoutes.empty`) unless `allow_siwe=true`, so the
 * feature is OFF by default. The verification logic lives in [[code.api.SIWE]].
 */
object SIWERoutes {

  private implicit val formats: Formats = CustomJsonFormats.formats

  val routes: HttpRoutes[IO] = if (!SIWE.isEnabled) HttpRoutes.empty[IO] else HttpRoutes.of[IO] {

    // ---- 1. CHALLENGE: issue a nonce + a ready-to-sign EIP-4361 message -----
    case req @ POST -> Root / "my" / "logins" / "siwe" / "challenge" =>
      Http4sCallContextBuilder.fromRequest(req, apiVersion = "").flatMap { cc =>
        val reqWithCC = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
        EndpointHelpers.executeFutureWithBody[SiweChallengeRequest, SiweChallengeResponse](reqWithCC) {
          (body, cc) =>
            val domainBox = SIWE.domainProp
            for {
              _ <- booleanToFuture(SiweNotEnabled, cc = Some(cc)) { SIWE.isEnabled }
              _ <- booleanToFuture(SiweConfigMissing + "siwe.domain", cc = Some(cc)) { domainBox.isDefined }
              _ <- booleanToFuture(SiweInvalidAddress, cc = Some(cc)) { SIWE.isValidEthAddress(body.address) }
              _ <- booleanToFuture(SiweChainIdNotAllowed, cc = Some(cc)) { SIWE.allowedChainIds.contains(body.chain_id) }
            } yield {
              val domain = domainBox.openOr("")
              val checksumAddress = SIWE.toChecksum(body.address)
              val nonce = SIWE.generateNonce()
              SIWE.storeNonce(nonce, checksumAddress)
              val issuedAt = Instant.now()
              val expiresAt = issuedAt.plusSeconds(SIWE.nonceTtlSeconds.toLong)
              val uri = body.uri.filter(_.trim.nonEmpty).getOrElse(s"https://$domain")
              val statement = body.statement.filter(_.trim.nonEmpty)
                .getOrElse("Sign in to the Open Bank Project with your Ethereum account.")
              val message = SIWE.buildMessage(
                domain, checksumAddress, body.chain_id, nonce, uri, statement,
                issuedAt.toString, expiresAt.toString)
              SiweChallengeResponse(nonce, message, expiresAt.toString)
            }
        }
      }

    // ---- 2. LOGIN: verify the signed message, then mint a token -------------
    case req @ POST -> Root / "my" / "logins" / "siwe" =>
      Http4sCallContextBuilder.fromRequest(req, apiVersion = "").flatMap { cc =>
        val reqWithCC = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
        EndpointHelpers.executeFutureWithBodyCreated[SiweLoginRequest, JSONFactory.TokenJSON](reqWithCC) {
          (body, cc) =>
            val domainBox = SIWE.domainProp
            val parsedBox = SIWE.parseMessage(body.message)
            for {
              _ <- booleanToFuture(SiweNotEnabled, cc = Some(cc)) { SIWE.isEnabled }
              _ <- booleanToFuture(SiweConfigMissing + "siwe.domain", cc = Some(cc)) { domainBox.isDefined }
              _ <- booleanToFuture(SiweMessageMalformed, cc = Some(cc)) { parsedBox.isDefined }
              parsed = parsedBox.openOrThrowException(SiweMessageMalformed)
              _ <- booleanToFuture(SiweInvalidAddress, cc = Some(cc)) { SIWE.isValidEthAddress(parsed.address) }
              _ <- booleanToFuture(SiweDomainMismatch, cc = Some(cc)) { parsed.domain.equalsIgnoreCase(domainBox.openOr("")) }
              _ <- booleanToFuture(SiweChainIdNotAllowed, cc = Some(cc)) { SIWE.allowedChainIds.contains(parsed.chainId) }
              _ <- booleanToFuture(SiweMessageExpired, cc = Some(cc)) { !SIWE.isExpired(parsed.expirationTime) }
              // Recover the signer BEFORE consuming the nonce, so a bad signature
              // doesn't burn a still-valid nonce.
              recoveredBox = SIWE.recoverEoaAddress(body.message, body.signature)
              _ <- booleanToFuture(SiweSignatureInvalid, cc = Some(cc)) {
                     recoveredBox.toList.exists(_.equalsIgnoreCase(parsed.address)) }
              // Single-use: consume now. The nonce must have been issued for this address.
              consumedAddress = SIWE.consumeNonce(parsed.nonce)
              _ <- booleanToFuture(SiweNonceInvalid, cc = Some(cc)) {
                     consumedAddress.exists(_.equalsIgnoreCase(parsed.address)) }
              userBox = SIWE.getOrCreateUser(parsed.chainId, SIWE.toChecksum(parsed.address))
              _ <- booleanToFuture(CannotGetOrCreateUser, cc = Some(cc)) { userBox.isDefined }
              user = userBox.openOrThrowException(CannotGetOrCreateUser)
              tokenBox = SIWE.mintToken(user.userPrimaryKey.value, body.consumer_key)
              _ <- booleanToFuture(InvalidDirectLoginParameters, cc = Some(cc)) { tokenBox.isDefined }
            } yield JSONFactory.createTokenJSON(tokenBox.openOrThrowException(InvalidDirectLoginParameters))
        }
      }
  }
}
