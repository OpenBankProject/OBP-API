package code.api.util

import code.api.util.CertificateUtil.{privateKey, publicKey}
import code.util.Helper.MdcLoggable
import javax.crypto.Cipher

object RSAUtil  extends MdcLoggable {

  val cryptoSystem = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

  def encrypt(text: String): String = {
    import org.apache.commons.codec.binary.Base64
    val cipher = Cipher.getInstance(cryptoSystem)
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    val res = cipher.doFinal(text.getBytes("UTF-8"))
    Base64.encodeBase64String(res)
  }
  def decrypt(encrypted: String): String = {
    import org.apache.commons.codec.binary.Base64
    import javax.crypto.Cipher
    val bytes = Base64.decodeBase64(encrypted)
    val cipher = Cipher.getInstance(cryptoSystem)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    new String(cipher.doFinal(bytes), "utf-8")
  }

  def main(args: Array[String]): Unit = {
    val db = "jdbc:postgresql://localhost:5432/obp_mapped?user=obp&password=f"
    val res = encrypt(db)
    println("db.url: " + db)
    println("encrypt: " + res)
    println("decrypt: " + decrypt(res))
  }

}
