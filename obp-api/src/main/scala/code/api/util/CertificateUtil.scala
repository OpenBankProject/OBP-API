package code.api.util

import java.io.{FileInputStream, IOException}
import java.security.cert.{Certificate, CertificateException, X509Certificate}
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{PublicKey, _}

import code.api.util.CryptoSystem.CryptoSystem
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import code.util.Helper.MdcLoggable
import com.nimbusds.jose._
import com.nimbusds.jose.crypto.{MACSigner, RSAEncrypter, RSASSASigner}
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.{EncryptedJWT, JWTClaimsSet}
import net.liftweb.util.Props
import org.bouncycastle.operator.OperatorCreationException


object CryptoSystem extends Enumeration {
  type CryptoSystem = Value
  val RSA = Value
  val AES = Value
}

object CertificateUtil extends MdcLoggable {

  // your-at-least-256-bit-secret
  val sharedSecret: String = ApiPropsWithAlias.jwtTokenSecret

  lazy val (publicKey: RSAPublicKey, privateKey: RSAPrivateKey) = APIUtil.getPropsAsBoolValue("jwt.use.ssl", false) match  {
    case true =>
      getKeyPair(
        jkspath = APIUtil.getPropsValue("keystore.path").getOrElse(""),
        jkspasswd = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd),
        keypasswd = APIUtil.getPropsValue("keystore.passphrase").getOrElse(APIUtil.initPasswd),
        alias = APIUtil.getPropsValue("keystore.alias").getOrElse("")
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
    val jkspath = APIUtil.getPropsValue("keystore.path").getOrElse("")
    val jkspasswd = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)
    val keypasswd = APIUtil.getPropsValue("keystore.passphrase").getOrElse(APIUtil.initPasswd)
    // This is used for QWAC certificate. Alias needs to be of that certificate.
    val alias = APIUtil.getPropsValue("keystore.alias").getOrElse("")
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val inputStream = new FileInputStream(jkspath)
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
        case _ => getKeyStoreCertificate
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
    
    println(convertRSAPublicKeyToAnRSAJWK)

  }

}