package code.api.util

import java.security.interfaces.RSAPublicKey
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util

import com.nimbusds.jose.crypto.{RSASSASigner, RSASSAVerifier}
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json
import net.liftweb.util.SecurityHelpers

import scala.collection.immutable.List


object JwsUtil {
  implicit val formats = CustomJsonFormats.formats
  case class JwsProtectedHeader(b64: Boolean,
                                `x5t#S256`: String,
                                crit: List[String],
                                sigT: String,
                                sigD: sigD,
                                alg: String
                               )

  case class sigD(pars: List[String], mId: String)

  /**
   * Rebuilds detached payload from request's headers in accordance to the JWS header
   * @param s JWS Header in form of JSON string
   * @param requestHeaders List of HTTP Request parameters
   * @return The detached payload used for JWS verification
   *         
   * More info: JSON Web Signature (JWS) Unencoded Payload Option (RFC 7797)
   */
  def rebuildDetachedPayload(s: String, requestHeaders: List[HTTPParam]): String = {
    json.parse(s).extractOpt[JwsProtectedHeader] match {
      case Some(header) =>
        val headers = header.sigD.pars.flatMap( i =>
          requestHeaders.find(_.name == i).map(i => s"${i.name}: ${i.values.mkString}")
        )
        headers.mkString("\n") + "\n" // Add new line after each item
      case None => "Cannot extract JWS Header"
    }
  }
  
  def computeDigest(input: String): String = SecurityHelpers.hash256(input)
  def createDigestHeader(input: String): String = s"digest: SHA-256=$input"
  private def getDeferredCriticalHeaders() = {
    val deferredCriticalHeaders  = new util.HashSet[String]()
    deferredCriticalHeaders.add("sigT")
    deferredCriticalHeaders.add("sigD")
    deferredCriticalHeaders
  }
  
  def verifyJws(jws: String, publicKey: RSAPublicKey, requestHeaders: List[HTTPParam] = Nil): Boolean = {
    // Rebuild detached header
    val jwsProtectedHeaderAsString = JWSObject.parse(jws).getHeader().toString()
    val rebuiltDetachedPayload = rebuildDetachedPayload(jwsProtectedHeaderAsString, requestHeaders)

    // Parse JWS with detached payload
    val parsedJWSObject: JWSObject = JWSObject.parse(jws, new Payload(rebuiltDetachedPayload));
    // Verify the RSA
    val verifier = new RSASSAVerifier(publicKey, getDeferredCriticalHeaders)
    val isVerified = parsedJWSObject.verify(verifier)
    isVerified
  }
  
  def main(args: Array[String]): Unit = {
    val before = System.currentTimeMillis()
    // RSA signatures require a public and private RSA key pair,
    // the public key must be made known to the JWS recipient to
    // allow the signatures to be verified
    val rsaJWK: RSAKey = new RSAKeyGenerator(2048).keyID("123").generate

    // Create RSA-signer with the private key
    val signer = new RSASSASigner(rsaJWK)


    val httpBody =
      s"""{
         |"instructedAmount": {"currency": "EUR", "amount": "123.50"},
         |"debtorAccount": {"iban": "DE40100100103307118608"},
         |"creditorName": "Merchant123",
         |"creditorAccount": {"iban": "DE02100100109307118603"},
         |"remittanceInformationUnstructured": "Ref Number Merchant"
         |}
         |""".stripMargin
    
    // digest: SHA-256=+xeh7JAayYPh8K13UnQCBBcniZzsyat+KDiuy8aZYdI
    val digest = computeDigest(httpBody)
    
    // The payload which will not be encoded and must be passed to
    // the JWS consumer in a detached manner
    val  detachedPayload: Payload = new Payload(
      s"""(request-target): post /v1/payments/sepa-credit-transfers
          |host: api.testbank.com
          |content-type: application/json
          |psu-ip-address: 192.168.8.78
          |psu-geo-location: GEO:52.506931,13.144558
          |digest: SHA-256=$digest
          |""".stripMargin);

    import java.util.Set
    val deferredCriticalHeaders  = new util.HashSet[String]()
    deferredCriticalHeaders.add("sigT")
    deferredCriticalHeaders.add("sigD")
    val criticalParams: Set[String] = new util.HashSet[String]()
    criticalParams.add("b64")
    criticalParams.addAll(deferredCriticalHeaders)
    
    val sigD = """{
                 |    "pars": [
                 |      "(request-target)",
                 |      "host",
                 |      "content-type",
                 |      "psu-ip-address",
                 |      "psu-geo-location",
                 |      "digest"
                 |    ],
                 |    "mId": "http://uri.etsi.org/19182/HttpHeaders"
                 |  }
                 |  """.stripMargin

    // We create the time in next format: '2011-12-03T10:15:30Z' 
    val sigT = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
    
    // Create and sign JWS
    val jwsProtectedHeader: JWSHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
      .base64URLEncodePayload(false)
      .x509CertSHA256Thumbprint(rsaJWK.computeThumbprint())
      .criticalParams(criticalParams)
      .customParam("sigT", sigT)
      .customParam("sigD", JSONObjectUtils.parse(sigD))
      .build();
    
    org.scalameta.logger.elem(jwsProtectedHeader)
  
    val jwsObject: JWSObject = new JWSObject(jwsProtectedHeader, detachedPayload);

    // Compute the RSA signature
    jwsObject.sign(signer)
  
    val isDetached = true;
    val jws: String = jwsObject.serialize(isDetached)
    org.scalameta.logger.elem(jws)
  
    // The resulting JWS, note the payload is not encoded (empty second part)
    // eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJIUzI1NiJ9..
    // 5rPBT_XW-x7mjc1ubf4WwW1iV2YJyc4CCFxORIEaAEk

    val requestHeaders = List(
      HTTPParam("(request-target)", List("post /v1/payments/sepa-credit-transfers")),
      HTTPParam("host", List("api.testbank.com")),
      HTTPParam("content-type", List("application/json")),
      HTTPParam("psu-ip-address", List("192.168.8.78")),
      HTTPParam("psu-geo-location", List("GEO:52.506931,13.144558")),
      HTTPParam("digest", List(s"SHA-256=$digest"))
    )
    
    // Rebuild detached header
    val jwsProtectedHeaderAsString = JWSObject.parse(jws).getHeader().toString()
    val rebuiltDetachedPayload = rebuildDetachedPayload(jwsProtectedHeaderAsString, requestHeaders)
    
    // Parse JWS with detached payload
    val parsedJWSObject: JWSObject = JWSObject.parse(jws, new Payload(rebuiltDetachedPayload));
    // Verify the RSA
    val verifier = new RSASSAVerifier(rsaJWK.toRSAPublicKey, deferredCriticalHeaders)
    val isVerified = parsedJWSObject.verify(verifier)
    
    org.scalameta.logger.elem(isVerified)
  }

}
