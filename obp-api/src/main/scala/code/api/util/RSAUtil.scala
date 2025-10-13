package code.api.util

import java.nio.file.{Files, Paths}
import java.security.Signature
import code.api.util.CertificateUtil.{privateKey, publicKey}
import code.util.Helper.MdcLoggable
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import javax.crypto.Cipher
import net.liftweb.util.SecurityHelpers
import net.liftweb.util.SecurityHelpers.base64EncodeURLSafe
import java.time.Instant

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
  
  def signWithRsa256(payload: String, jwk: JWK): String = {
    // Prepare JWS object with simple string as a payload
    val jwsObject = new JWSObject(
      new JWSHeader.Builder(JWSAlgorithm.RS256).build, 
      new Payload(payload)
    )
    
    val rsaSigner = new RSASSASigner(jwk.toRSAKey)
    // Compute the RSA signature
    jwsObject.sign(rsaSigner)

    // To serialize to compact form, produces something like
    // eyJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.IRMQENi4nJyp4er2L
    // mZq3ivwoAjqa1uUkSBKFIX7ATndFF5ivnt-m8uApHO4kfIFOrW7w2Ezmlg3Qd
    // maXlS9DhN0nUk_hGI3amEjkKd0BWYCB8vfUbUv0XGjQip78AI4z1PrFRNidm7
    // -jPDm5Iq0SZnjKjCNS5Q15fokXZc8u0A
    val s = jwsObject.serialize
    s
  }
  
  def computeXSign(input: String, jwk: JWK) = {
    logger.debug("Input: " + input)
    logger.debug("Hash: " + computeHash(input))
    logger.debug("HEX hash: " + computeHexHash(input))
    // Compute the signature
    val data = input.getBytes("UTF8")
    val sig = Signature.getInstance("SHA256WithRSA")
    sig.initSign(jwk.toRSAKey.toPrivateKey)
    sig.update(data)
    val signatureBytes = sig.sign
    val xSign = base64EncodeURLSafe(signatureBytes)
    logger.debug("x-sign: " + xSign)
    xSign
  }
  
  def getPrivateKeyFromFile(path: String): JWK = {
    val pathOfFile = Paths.get(path)
    val pemEncodedRSAPrivateKey = Files.readAllLines(pathOfFile).toArray.toList.mkString("\n")
    logger.debug(pemEncodedRSAPrivateKey)
    // Parse PEM-encoded key to RSA public / private JWK
    val jwk: JWK  = JWK.parseFromPEMEncodedObjects(pemEncodedRSAPrivateKey);
    logger.debug("Key is private: " + jwk.isPrivate)
    jwk
  }

  def getPrivateKeyFromString(privateKeyValue: String): JWK = {
    val pemEncodedRSAPrivateKey = privateKeyValue
    logger.debug(privateKeyValue)
    // Parse PEM-encoded key to RSA public / private JWK
    val jwk: JWK  = JWK.parseFromPEMEncodedObjects(pemEncodedRSAPrivateKey);
    logger.debug("Key is private: " + jwk.isPrivate)
    jwk
  }
  
  def main(args: Array[String]): Unit = {
    // Use props configuration or generate random password to avoid hard-coded credentials
    val randomString = APIUtil.getPropsValue("DEMO_DB_PASSWORD", net.liftweb.util.Helpers.randomString(16) + "!A1")
    val db = "jdbc:postgresql://localhost:5432/obp_mapped?user=obp&password=%s".format(randomString)
    val res = encrypt(db)
    println("db.url: " + db)
    println("encrypt: " + res)
    println("decrypt: " + decrypt(res))
    
    val timestamp = Instant.now.getEpochSecond
    val uri = "https://api.qredo.network/api/v1/p/company"
    val body = """{"name":"Tesobe GmbH","city":"Berlin","country":"DE","domain":"tesobe.com","ref":"9827feec-4eae-4e80-bda3-daa7c3b97ad1"}"""
    val inputMessage = s"""${timestamp}${uri}${body}"""
    val privateKey = getPrivateKeyFromFile("obp-api/src/test/resources/cert/private.pem")
    computeXSign(inputMessage, privateKey)
    logger.debug("timestamp: " +  timestamp)

    
  }

}
