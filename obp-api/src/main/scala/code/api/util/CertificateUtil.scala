package code.api.util

import code.api.CertificateConstants
import code.api.util.CryptoSystem.CryptoSystem
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import code.util.Helper.MdcLoggable
import com.nimbusds.jose.{EncryptionMethod, JWEAlgorithm, JWEHeader, JWSAlgorithm, JWSHeader, JWSSigner}
import com.nimbusds.jose.crypto.{MACSigner, RSAEncrypter, RSASSASigner}
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.{EncryptedJWT, JWTClaimsSet}
import net.liftweb.util.Props

import java.io.{FileInputStream, IOException}
import java.security._
import java.security.cert.{Certificate, CertificateException, X509Certificate}
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}


object CryptoSystem extends Enumeration {
  type CryptoSystem = Value
  val RSA = Value
  val AES = Value
}

object CertificateUtil extends MdcLoggable {

  // your-at-least-256-bit-secret
  val sharedSecret: String = ApiPropsWithAlias.jwtTokenSecret
  final val jkspath = APIUtil.getPropsValue("keystore.path").getOrElse("")
  final val jkspasswd = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)
  final val keypasswd = APIUtil.getPropsValue("keystore.passphrase").getOrElse(APIUtil.initPasswd)
  final val alias = APIUtil.getPropsValue("keystore.alias").getOrElse("")

  lazy val (publicKey: RSAPublicKey, privateKey: RSAPrivateKey) = APIUtil.getPropsAsBoolValue("jwt.use.ssl", false) match  {
    case true =>
      getKeyPair(
        jkspath = jkspath, 
        jkspasswd = jkspasswd, 
        keypasswd = keypasswd, 
        alias = alias
      )
    case false =>
      val keyPair = buildKeyPair(CryptoSystem.RSA)
      val pubKey = keyPair.getPublic
      val privateKey = keyPair.getPrivate
      (pubKey, privateKey)
  }

  def getKeyPair(jkspath: String, jkspasswd: String, keypasswd: String, alias: String): (PublicKey, Key) = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val inputStream = new FileInputStream(jkspath)
    keyStore.load(inputStream, jkspasswd.toArray)
    inputStream.close()
    val privateKey: Key = keyStore.getKey(alias, keypasswd.toCharArray())
    if (privateKey.isInstanceOf[PrivateKey]) {
      // Get certificate of public key
      val cert: java.security.cert.Certificate = keyStore.getCertificate(alias)

      // Get public key
      val publicKey: PublicKey = cert.getPublicKey

      // Return a key pair
      (publicKey, privateKey)
    }
    else throw new RuntimeException("No private key")
  }

  @throws[IOException]
  @throws[NoSuchAlgorithmException]
  @throws[CertificateException]
  @throws[RuntimeException]
  def getKeyStoreCertificate() = {
    // TODO SENSITIVE DATA LOGGING
    logger.debug("getKeyStoreCertificate says hello.")
    val jkspath = APIUtil.getPropsValue("keystore.path").getOrElse("")
    logger.debug("getKeyStoreCertificate says jkspath is: " + jkspath)
    val jkspasswd = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)
    logger.debug("getKeyStoreCertificate says jkspasswd is: " + jkspasswd)
    val keypasswd = APIUtil.getPropsValue("keystore.passphrase").getOrElse(APIUtil.initPasswd)
    logger.debug("getKeyStoreCertificate says keypasswd is: " + keypasswd)
    // This is used for QWAC certificate. Alias needs to be of that certificate.
    val alias = APIUtil.getPropsValue("keystore.alias").getOrElse("")
    logger.debug("getKeyStoreCertificate says alias is: " + alias)
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val inputStream = new FileInputStream(jkspath)
    logger.debug("getKeyStoreCertificate says before keyStore.load inputStream")
    keyStore.load(inputStream, jkspasswd.toArray)
    inputStream.close()
    val privateKey: Key = keyStore.getKey(alias, keypasswd.toCharArray())
    if (privateKey.isInstanceOf[PrivateKey]) {
      // Get certificate of public key
      val cert: java.security.cert.Certificate = keyStore.getCertificate(alias)

      // Return a private key and certificate
      (privateKey, cert)
    }
    else throw new RuntimeException("No private key")
    
  }
  

  @throws[NoSuchAlgorithmException]
  def buildKeyPair(cryptoSystem: CryptoSystem): KeyPair = {
    val keySize = 2048
    val keyPairGenerator = KeyPairGenerator.getInstance(cryptoSystem.toString)
    keyPairGenerator.initialize(keySize)
    keyPairGenerator.genKeyPair
  }
  
  def convertRSAPublicKeyToAnRSAJWK(): String = {
    import com.nimbusds.jose.jwk._
    // Convert to JWK format
    val jwk: RSAKey  = new RSAKey.Builder(publicKey)
      .keyUse(KeyUse.SIGNATURE)
      .keyIDFromThumbprint()
      .build()
    jwk.toJSONString()
  }

  /**
   * This is used for QWAC certificate.
   * x5s is the part of te JOSE Protected header we use it in case of Java Web Signature.
   * We sign response with rsaSigner and send it via "x-jws-signature" response header.
   * it's verified via x5c value at third party app.
   */
  lazy val (rsaSigner, x5c, rsaPublicKey) = {
    val (privateKey: PrivateKey, certificate: Certificate) =
      Props.mode match {
        case Props.RunModes.Development | Props.RunModes.Test => generateSelfSignedCert("test.tesobe.com")
        case _ => getKeyStoreCertificate()
      }
    val publicKey: RSAPublicKey = certificate.getPublicKey.asInstanceOf[RSAPublicKey]
    import com.nimbusds.jose.jwk._
    // Convert to JWK format
    val jwk: RSAKey  = new RSAKey.Builder(publicKey)
      .privateKey(privateKey.asInstanceOf[RSAPrivateKey])
      .keyUse(KeyUse.SIGNATURE)
      .keyIDFromThumbprint()
      .build()
    val x5c = X509CertUtils.toPEMString(certificate.asInstanceOf[X509Certificate], false)
      .replace(X509CertUtils.PEM_BEGIN_MARKER, "")
      .replace(X509CertUtils.PEM_END_MARKER, "")
    (new RSASSASigner(jwk), x5c, publicKey)
  }
  

  def jwtWithHmacProtection(claimsSet: JWTClaimsSet): String = {
    // Create HMAC signer
    val  signer: JWSSigner = new MACSigner(sharedSecret)
    import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
    import com.nimbusds.jwt.SignedJWT
    val signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet)
    // Apply the HMAC protection
    signedJWT.sign(signer)
    // Serialize to compact form, produces something like
    // eyJhbGciOiJIUzI1NiJ9.SGVsbG8sIHdvcmxkIQ.onO9Ihudz3WkiauDO2Uhyuz0Y18UASXlSc1eS0NkWyA
    val s: String = signedJWT.serialize()
    // logger.info("jwtWithHmacProtection: " + s)
    s
  }
  
  def jwtWithHmacProtection(claimsSet: JWTClaimsSet, sharedSecret: String): String = {
    // Create HMAC signer
    val  signer: JWSSigner = new MACSigner(sharedSecret)
    import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
    import com.nimbusds.jwt.SignedJWT
    val signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet)
    // Apply the HMAC protection
    signedJWT.sign(signer)
    // Serialize to compact form, produces something like
    // eyJhbGciOiJIUzI1NiJ9.SGVsbG8sIHdvcmxkIQ.onO9Ihudz3WkiauDO2Uhyuz0Y18UASXlSc1eS0NkWyA
    val s: String = signedJWT.serialize()
    // logger.info("jwtWithHmacProtection: " + s)
    s
  }

  def verifywtWithHmacProtection(jwt: String): Boolean = {
    import com.nimbusds.jose.crypto.MACVerifier
    import com.nimbusds.jwt.SignedJWT
    val signedJWT: SignedJWT = SignedJWT.parse(jwt)
    // your-at-least-256-bit-secret
    val verifier = new MACVerifier(sharedSecret)
    signedJWT.verify(verifier)
  }
  
  def verifywtWithHmacProtection(jwt: String, sharedSecret: String): Boolean = {
    import com.nimbusds.jose.crypto.MACVerifier
    import com.nimbusds.jwt.SignedJWT
    val signedJWT: SignedJWT = SignedJWT.parse(jwt)
    // your-at-least-256-bit-secret
    val verifier = new MACVerifier(sharedSecret)
    signedJWT.verify(verifier)
  }

  def parseJwtWithHmacProtection(jwt: String) = {
    import com.nimbusds.jwt.SignedJWT
    val signedJWT: SignedJWT = SignedJWT.parse(jwt)
    val claimsSet = signedJWT.getJWTClaimsSet()
    // logger.debug("signedJWT.getJWTClaimsSet(): " + claimsSet)
    claimsSet
  }

  def encryptJwtWithRsa(jwtClaims: JWTClaimsSet) = {
    // Request JWT encrypted with RSA-OAEP-256 and 128-bit AES/GCM
    val header = new JWEHeader(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A128GCM)
    // Create an encrypter with the specified public RSA key
    val encrypter = new RSAEncrypter(publicKey)
    // Create the encrypted JWT object
    val encryptedJWT = new EncryptedJWT(header, jwtClaims)
    // Do the actual encryption
    encryptedJWT.encrypt(encrypter)
    // logger.debug("encryptedJwtWithRsa: " + encryptedJWT.serialize())
    // logger.debug("jwtClaims: " + jwtClaims)
    // Serialise to JWT compact form
    encryptedJWT.serialize()
  }

  def decryptJwtWithRsa(encryptedJwtWithRsa: String) = {
    import com.nimbusds.jose.crypto.RSADecrypter
    import com.nimbusds.jwt.EncryptedJWT
    // Parse back
    val jwtParsed = EncryptedJWT.parse(encryptedJwtWithRsa)
    // Create a decrypter with the specified private RSA key
    val decrypter = new RSADecrypter(privateKey)
    jwtParsed.decrypt(decrypter)
    // logger.debug("encryptedJwtWithRsa: " + encryptedJwtWithRsa)
    // logger.debug("getState: " + jwtParsed.getState)
    // logger.debug("getJWTClaimsSet: " + jwtParsed.getJWTClaimsSet)
    jwtParsed.getJWTClaimsSet
  }

  // Remove all whitespace characters including spaces, tabs, newlines, and carriage returns
  def normalizePemX509Certificate(pem: String): String = {
    val pemHeader = CertificateConstants.BEGIN_CERT
    val pemFooter = CertificateConstants.END_CERT

    def extractContent(pem: String): Option[String] = {
      val start = pem.indexOf(pemHeader)
      val end = pem.indexOf(pemFooter)

      if (start >= 0 && end > start) {
        Some(pem.substring(start + pemHeader.length, end))
      } else {
        None
      }
    }

    extractContent(pem).map { content => // Extract content from PEM representation of X509 certificate
      val normalizedContent = content.replaceAll("\\s+", "")
      s"$pemHeader$normalizedContent$pemFooter"
    }.getOrElse(pem) // In case the extraction cannot be done default the input value we try to normalize
  }

  def comparePemX509Certificates(pem1: String, pem2: String): Boolean = {
    val normalizedPem1 = normalizePemX509Certificate(pem1)
    val normalizedPem2 = normalizePemX509Certificate(pem2)

    val result = normalizedPem1 == normalizedPem2
    if(!result) {
      logger.debug(s"normalizedPem1: ${normalizedPem1}")
      logger.debug(s"normalizedPem2: ${normalizedPem2}")
    }
    result
  }

  // Canonical PEM: 64-column base64 between the standard header and footer, "\n" separated. This is
  // the single form every part of OBP should see, whoever terminated TLS — see canonicalizePemX509Certificate.
  private val canonicalPemEncoder =
    java.util.Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))

  /** An X509 certificate rendered as canonical PEM. */
  def toPem(certificate: X509Certificate): String =
    s"${CertificateConstants.BEGIN_CERT}\n${canonicalPemEncoder.encodeToString(certificate.getEncoded)}\n${CertificateConstants.END_CERT}"

  /**
   * Percent-decoding that leaves '+' alone.
   *
   * `java.net.URLDecoder` decodes '+' to a space, which is correct for form encoding and wrong
   * here: '+' is a base64 alphabet character, so a certificate carrying one would be corrupted.
   * nginx's `$ssl_client_escaped_cert` percent-escapes everything it needs to, '+' included, so
   * there is nothing to gain from the form-encoding rule and a certificate to lose.
   */
  private def percentDecode(value: String): String = {
    val out = new java.io.ByteArrayOutputStream(value.length)
    var i = 0
    while (i < value.length) {
      value.charAt(i) match {
        case '%' if i + 2 < value.length =>
          try {
            out.write(Integer.parseInt(value.substring(i + 1, i + 3), 16))
            i += 3
          } catch {
            case _: NumberFormatException => out.write('%'.toInt); i += 1 // not an escape, keep it
          }
        case c =>
          out.write(c.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8))
          i += 1
      }
    }
    new String(out.toByteArray, java.nio.charset.StandardCharsets.UTF_8)
  }

  /**
   * Whatever representation of an X509 certificate arrived, rendered as canonical PEM — or None if
   * it is not a certificate at all.
   *
   * The same certificate reaches OBP in several encodings depending on who terminated TLS: nginx's
   * `$ssl_client_escaped_cert` is percent-encoded, HAProxy rebuilds a single-line PEM, the dev-mode
   * in-process terminator injects canonical PEM, and a hand-built client may send bare base64 with
   * no PEM markers at all. Downstream code then compares certificates as strings, so each encoding
   * silently behaves like a different certificate — which is how a deployment works in development
   * and fails in production.
   *
   * Normalising once on ingress makes every one of those comparisons exact. Note this is stricter
   * than [[normalizePemX509Certificate]], which only rewrites whitespace and cannot tell a
   * certificate from any other string: this parses, and so also rejects non-certificates.
   */
  def canonicalizePemX509Certificate(raw: String): Option[String] = {
    val trimmed = raw.trim
    if (trimmed.isEmpty) None
    else {
      val decoded = if (trimmed.contains('%')) percentDecode(trimmed) else trimmed
      // X509CertUtils.parse wants the PEM markers; supply them for bare base64.
      val withMarkers =
        if (decoded.contains(CertificateConstants.BEGIN_CERT)) decoded
        else s"${CertificateConstants.BEGIN_CERT}\n$decoded\n${CertificateConstants.END_CERT}"
      Option(X509CertUtils.parse(withMarkers)).map(toPem)
    }
  }



  def main(args: Array[String]): Unit = {
    System.out.println("Public key:" + publicKey.getEncoded)
    System.out.println("Private key:" + privateKey.getEncoded)

    val jwwtPayloadAsJson =
      """{
           "login_user_name":"simonr",
           "is_first":false,
           "app_id":"593450734587345",
           "app_name":"myapp4",
           "time_stamp":"19-06-2017:22:27:11:100",
           "cbs_token":"",
           "cbs_id":"",
           "session_id":"123"
         }"""

    val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwwtPayloadAsJson)

    // 1.1 Encryption - JWT with RSA encryption
    val encryptTokenWithRsa = encryptJwtWithRsa(jwtClaims)
    logger.info(s"encryptTokenWithRsa =$encryptTokenWithRsa")
    
    // 1.2  Decryption - JWT with RSA encryption
    val decryptToken = decryptJwtWithRsa(encryptTokenWithRsa)
    logger.info(s"decryptToken = $decryptToken")

    // 2.1 JWT with HMAC protection
    val hmacJwt = jwtWithHmacProtection(jwtClaims)
    logger.info(s"hmacJwt = $hmacJwt")

    parseJwtWithHmacProtection(hmacJwt)
    
    println(convertRSAPublicKeyToAnRSAJWK())

  }

}