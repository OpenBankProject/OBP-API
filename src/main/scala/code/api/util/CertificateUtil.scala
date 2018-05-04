package code.api.util

import java.io.FileInputStream
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{PublicKey, _}
import javax.crypto.Cipher
import com.nimbusds.jose.jwk.RSAKey

import code.api.util.CryptoSystem.CryptoSystem
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.{EncryptionMethod, JOSEObject, JWEAlgorithm, JWEHeader}
import com.nimbusds.jwt.EncryptedJWT
import code.util.Helper.MdcLoggable
import net.liftweb.util.{Helpers, Props}


object CryptoSystem extends Enumeration {
  type CryptoSystem = Value
  val RSA = Value
}

object CertificateUtil extends MdcLoggable {

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

  @throws[NoSuchAlgorithmException]
  def buildKeyPair(cryptoSystem: CryptoSystem): KeyPair = {
    val keySize = 2048
    val keyPairGenerator = KeyPairGenerator.getInstance(cryptoSystem.toString)
    keyPairGenerator.initialize(keySize)
    keyPairGenerator.genKeyPair
  }

  @throws[Exception]
  def sign(privateKey: PrivateKey, message: String, cryptoSystem: CryptoSystem): Array[Byte] = {
    val cipher = Cipher.getInstance(cryptoSystem.toString)
    cipher.init(Cipher.ENCRYPT_MODE, privateKey)
    cipher.doFinal(message.getBytes)
  }
  @throws[Exception]
  def encrypt(publicKey: PublicKey, message: String, cryptoSystem: CryptoSystem): Array[Byte] = {
    val cipher = Cipher.getInstance(cryptoSystem.toString)
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    cipher.doFinal(message.getBytes)
  }
  @throws[Exception]
  def encrypt(privateKey: PrivateKey, message: String, cryptoSystem: CryptoSystem): Array[Byte] = {
    val cipher = Cipher.getInstance(cryptoSystem.toString)
    cipher.init(Cipher.ENCRYPT_MODE, privateKey)
    cipher.doFinal(message.getBytes)
  }
  @throws[Exception]
  def decrypt(privateKey: PrivateKey, encrypted: Array[Byte], cryptoSystem: CryptoSystem): Array[Byte] = {
    val cipher = Cipher.getInstance(cryptoSystem.toString)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    cipher.doFinal(encrypted)
  }
  @throws[Exception]
  def decrypt(publicKey: PublicKey, encrypted: Array[Byte], cryptoSystem: CryptoSystem): Array[Byte] = {
    val cipher = Cipher.getInstance(cryptoSystem.toString)
    cipher.init(Cipher.DECRYPT_MODE, publicKey)
    cipher.doFinal(encrypted)
  }
  @throws[Exception]
  def validate(privateKey: PrivateKey, encrypted: Array[Byte], cryptoSystem: CryptoSystem): Array[Byte] = {
    val cipher = Cipher.getInstance(cryptoSystem.toString)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    cipher.doFinal(encrypted)
  }

  def getClaimSet(jwt: String) = {
    import com.nimbusds.jose.util.Base64URL
    import com.nimbusds.jwt.PlainJWT
    // {"alg":"none"}// {"alg":"none"}
    val header = "eyJhbGciOiJub25lIn0"
    val parts: Array[Base64URL] = JOSEObject.split(jwt)
    val plainJwt = new PlainJWT(new Base64URL(header), (parts(1)))
    plainJwt.getJWTClaimsSet
  }
  def encryptJwtWithRsa(jwt: String) = {
    // Request JWT encrypted with RSA-OAEP-256 and 128-bit AES/GCM
    val header = new JWEHeader(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A128GCM)
    // Create an encrypter with the specified public RSA key
    val encrypter = new RSAEncrypter(publicKey)
    // Create the encrypted JWT object
    val encryptedJWT = new EncryptedJWT(header, CertificateUtil.getClaimSet(jwt))
    // Do the actual encryption
    encryptedJWT.encrypt(encrypter)
    logger.debug("encryptedJWT.serialize(): " + encryptedJWT.serialize())
    // Return JWT
    encryptedJWT.serialize()
  }
  def decryptJwtWithRsa(jwt: String) = {
    import com.nimbusds.jose.crypto.RSADecrypter
    import com.nimbusds.jwt.EncryptedJWT
    // Parse back
    val jwtParsed = EncryptedJWT.parse(jwt)
    System.out.println("decryptJwtWithRsa: " + jwtParsed.serialize())
    // Create a decrypter with the specified private RSA key
    val decrypter = new RSADecrypter(privateKey)
    jwtParsed.decrypt(decrypter)
    logger.debug("jwt: " + jwt)
    logger.debug("getState: " + jwtParsed.getState)
    logger.debug("getJWTClaimsSet: " + jwtParsed.getJWTClaimsSet)
    logger.debug("getCipherText: " + jwtParsed.getCipherText)
    logger.debug("getAuthTag: " + jwtParsed.getAuthTag)
    jwtParsed.serialize()
  }

    System.out.println("Public key:" + publicKey.getEncoded)
    System.out.println("Private key:" + privateKey.getEncoded)

    // 1.1 Encrypt the token with public key
    val encryptedWithPublicReceived = encrypt(publicKey, "This is a secret message we should receive", CryptoSystem.RSA)
    System.out.println("Encrypted token with public key:")
    val encryptedString = Helpers.base64Encode(encryptedWithPublicReceived)
    System.out.println(encryptedString) // <<encrypted message>>

    // 1.2 Decrypt the token with private key
    val decryptedToken = decrypt(privateKey, Helpers.base64Decode(encryptedString), CryptoSystem.RSA)
    System.out.println("Decrypted token with private key:") // This is a secret message
    System.out.println(new String(decryptedToken)) // This is a secret message

    // Convert to JWK format
    val jwk: RSAKey = new RSAKey.Builder(publicKey.asInstanceOf[RSAPublicKey]).privateKey(privateKey.asInstanceOf[RSAPrivateKey]).keyID("rsa1").build // Give the key some ID (optional)

    // Output
    println(jwk.toJSONObject.toJSONString)


}