package code.api.util

import code.api.util.CertificateUtil.{privateKey, publicKey}
import code.util.Helper.MdcLoggable
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import javax.crypto.Cipher
import net.liftweb.util.SecurityHelpers

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
    import javax.crypto.Cipher
    import org.apache.commons.codec.binary.Base64
    val bytes = Base64.decodeBase64(encrypted)
    val cipher = Cipher.getInstance(cryptoSystem)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    new String(cipher.doFinal(bytes), "utf-8")
  }

  def computeHash(input: String): String = SecurityHelpers.hash256(input)
  def computeHexHash(input: String): String = {
    SecurityHelpers.hexDigest256(input.getBytes("UTF-8"))
  }
  
  def signWithRsa256(payload: String): String = {
    // Prepare JWS object with simple string as payload
    val jwsObject = new JWSObject(
      new JWSHeader.Builder(JWSAlgorithm.RS256).build, 
      new Payload(payload)
    )
    // Compute the RSA signature
    jwsObject.sign(CertificateUtil.rsaSigner)

    // To serialize to compact form, produces something like
    // eyJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.IRMQENi4nJyp4er2L
    // mZq3ivwoAjqa1uUkSBKFIX7ATndFF5ivnt-m8uApHO4kfIFOrW7w2Ezmlg3Qd
    // maXlS9DhN0nUk_hGI3amEjkKd0BWYCB8vfUbUv0XGjQip78AI4z1PrFRNidm7
    // -jPDm5Iq0SZnjKjCNS5Q15fokXZc8u0A
    val s = jwsObject.serialize
    s
  }
  
  def computeXSign(input: String) = {
    logger.debug("Input: " + input)
    logger.debug("Hash: " + computeHash(input))
    logger.debug("HEX hash: " + computeHexHash(input))
    // Compute JWS token
    val jws = signWithRsa256(computeHexHash(input))
    logger.debug("RSA 256 signature: " + jws)
    // Get the last i.e. 3rd part of JWS token
    val xSign = jws.split('.').toList.last
    logger.debug("x-sign: " + xSign)
    xSign
  }

  def main(args: Array[String]): Unit = {
    val db = "jdbc:postgresql://localhost:5432/obp_mapped?user=obp&password=f"
    val res = encrypt(db)
    println("db.url: " + db)
    println("encrypt: " + res)
    println("decrypt: " + decrypt(res))
    
    val inputMessage = """hello world\n"""
    computeXSign(inputMessage)
  }

}
