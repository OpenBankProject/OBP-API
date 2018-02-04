package code.api.util

import java.io.FileInputStream
import java.security.{PrivateKey, PublicKey, _}
import javax.crypto.Cipher

import code.api.util.CryptoSystem.CryptoSystem
import net.liftweb.util.Props


object CryptoSystem extends Enumeration {
  type CryptoSystem = Value
  val RSA = Value
}

object CertificateUtil {

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


  @throws[Exception]
  def main(args: Array[String]): Unit = {

    val (publicKey: PublicKey, privateKey: PrivateKey) = Props.getBool("jwt.use.ssl", false) match  {
      case true =>
        getKeyPair(
          jkspath = Props.get("keystore.path").getOrElse(""),
          jkspasswd = "redf1234",
          keypasswd = "redf1234",
          alias = "localhost"
        )
      case false =>
        val keyPair = buildKeyPair(CryptoSystem.RSA)
        val pubKey = keyPair.getPublic
        val privateKey = keyPair.getPrivate
        (pubKey, privateKey)
    }

    // 1. We get from the gw encrypted token with public key
    val encryptedWithPublic = encrypt(publicKey, "This is a secret message", CryptoSystem.RSA)
    System.out.println("Encrypted token with public key:") //
    System.out.println(new String(encryptedWithPublic)) // <<encrypted message>>

    // 2. Decrypt the token with private key
    val decryptedToken = decrypt(privateKey, encryptedWithPublic, CryptoSystem.RSA)
    System.out.println("Decrypted token with private key:") // This is a secret message
    System.out.println(new String(decryptedToken)) // This is a secret message

  }


}