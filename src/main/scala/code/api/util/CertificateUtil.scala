package code.api.util

import java.io.FileInputStream
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{PublicKey, _}
import javax.crypto.Cipher

import code.api.util.CryptoSystem.CryptoSystem
import net.liftweb.util.{Helpers, Props}


object CryptoSystem extends Enumeration {
  type CryptoSystem = Value
  val RSA = Value
}

object CertificateUtil {

  lazy val (publicKey: RSAPublicKey, privateKey: RSAPrivateKey) = APIUtil.getPropsAsBoolValue("jwt.use.ssl", false) match  {
    case true =>
      getKeyPair(
        jkspath = Props.get("keystore.path").getOrElse(""),
        jkspasswd = Props.get("keystore.password").getOrElse(APIUtil.initPasswd),
        keypasswd = Props.get("keystore.passphrase").getOrElse(APIUtil.initPasswd),
        alias = Props.get("keystore.alias").getOrElse("")
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


  @throws[Exception]
  def main(args: Array[String]): Unit = {

    print("Enter the Password for the SSL Certificate Stores: ")
    //As most IDEs do not provide a Console, we fall back to readLine
    code.api.util.APIUtil.initPasswd =
      if (Props.get("kafka.use.ssl").getOrElse("") == "true" ||
          Props.get("jwt.use.ssl").getOrElse("") == "true")
      {
        try {
          System.console.readPassword().toString
        } catch {
          case e: NullPointerException => scala.io.StdIn.readLine()
        }
      } else {"notused"}

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


  }


}