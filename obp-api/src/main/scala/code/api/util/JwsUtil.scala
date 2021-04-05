package code.api.util

import java.security.interfaces.RSAPublicKey
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util
import java.util.Set

import code.api.util.X509.{pemEncodedCertificate, pemEncodedRSAPrivateKey, validate}
import com.nimbusds.jose.crypto.{RSASSASigner, RSASSAVerifier}
import com.nimbusds.jose.jwk.{JWK, RSAKey}
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json
import net.liftweb.util.SecurityHelpers

import scala.collection.immutable.{HashMap, List}


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
  def rebuildDetachedPayload(s: String, requestHeaders: List[HTTPParam], verb: String, url: String): String = {
    json.parse(s).extractOpt[JwsProtectedHeader] match {
      case Some(header) =>
        val headers = header.sigD.pars.flatMap( i =>
          requestHeaders.find(_.name.toLowerCase() == i.toLowerCase()).map(i => s"${i.name.toLowerCase()}: ${i.values.mkString}")
        )
        val requestTarget = s"""(request-target): ${verb.toLowerCase()} ${url}\n"""
        requestTarget + headers.mkString("\n") + "\n" // Add new line after each item
      case None => "Cannot extract JWS Header"
    }
  }
  def verifySigningTime(jwsProtectedHeader: String): Boolean = {
    json.parse(jwsProtectedHeader).extractOpt[JwsProtectedHeader] match {
      case Some(header) =>
        val signingTime = ZonedDateTime.parse(header.sigT, DateTimeFormatter.ISO_ZONED_DATE_TIME)
        val verifyingTime = ZonedDateTime.now(ZoneOffset.UTC)
        val criteriaOneFailed = signingTime.isAfter(verifyingTime.plusSeconds(2))
        val criteriaTwoFailed = signingTime.plusSeconds(60).isBefore(verifyingTime)
        !criteriaOneFailed && !criteriaTwoFailed
      case None => false
    }
  }
  def computeDigest(input: String): String = SecurityHelpers.hash256(input)
  def verifyDigestHeader(headerValue: String, httpBody: String): Boolean = {
    headerValue == s"SHA-256=${computeDigest(httpBody)}"
  }
  def getDigestHeaderValue(requestHeaders: List[HTTPParam]): String = {
    requestHeaders.find(_.name == "digest").map(_.values.mkString).getOrElse("None")
  }
  def getJwsHeaderValue(requestHeaders: List[HTTPParam]): String = {
    requestHeaders.find(_.name == "x-jws-signature").map(_.values.mkString).getOrElse("None")
  }
  def createDigestHeader(input: String): String = s"digest: SHA-256=$input"
  private def getDeferredCriticalHeaders() = {
    val deferredCriticalHeaders  = new util.HashSet[String]()
    deferredCriticalHeaders.add("sigT")
    deferredCriticalHeaders.add("sigD")
    deferredCriticalHeaders
  }
  
  def verifyJws(publicKey: RSAPublicKey, httpBody: String, requestHeaders: List[HTTPParam], verb: String, url: String): Boolean = {
    // Verify digest header
    val isVerifiedDigestHeader = verifyDigestHeader(getDigestHeaderValue(requestHeaders), httpBody)
    val xJwsSignature = getJwsHeaderValue(requestHeaders)
    // Rebuild detached header
    val jwsProtectedHeaderAsString = JWSObject.parse(xJwsSignature).getHeader().toString()
    val rebuiltDetachedPayload = rebuildDetachedPayload(jwsProtectedHeaderAsString, requestHeaders, verb, url)
    // Parse JWS with detached payload
    val parsedJWSObject: JWSObject = JWSObject.parse(xJwsSignature, new Payload(rebuiltDetachedPayload));
    // Verify the RSA
    val verifier = new RSASSAVerifier(publicKey, getDeferredCriticalHeaders)
    val isVerifiedJws = parsedJWSObject.verify(verifier)
    isVerifiedJws && isVerifiedDigestHeader && verifySigningTime(jwsProtectedHeaderAsString)
  }

  /**
   * Verifies Signed Request. It assumes that Customers has a sored certificate.
   * @param body of the signed request
   * @param verb GET, POST, DELETE, etc.
   * @param url of the the signed request. For example: /berlin-group/v1.3/payments/sepa-credit-transfers
   * @param reqHeaders All request headers of the signed request
   * @param forwardResult Propagated result of calling function
   * @return Propagated result of calling function or signing request error
   */
  def verifySignedRequest(body: Box[String], verb: String, url: String, reqHeaders: List[HTTPParam], forwardResult: (Box[User], Option[CallContext])) = {
    val standards: List[String] = APIUtil.getPropsValue(nameOfProperty="force_jws", "None").split(",").map(_.trim).toList
    val pathOfStandard = HashMap("BGv1.3"->"berlin-group/v1.3", "OBPv4.0.0"->"obp/v4.0.0", "OBPv3.1.0"->"obp/v3.1.0", "UKv1.3"->"open-banking/v3.1").withDefaultValue("{Not found any standard to match}")
    if(standards.exists(standard => url.contains(pathOfStandard(standard)))){
      val pem: String = getPem(forwardResult)
      X509.validate(pem) match {
        case Full(true) => // PEM certificate is ok
          val jwkPublic: JWK = X509.pemToRsaJwk(pem)
          val isVerified = JwsUtil.verifyJws(jwkPublic.toRSAKey.toRSAPublicKey, body.getOrElse(""), reqHeaders, verb, url)
          if (isVerified) forwardResult else (Failure(ErrorMessages.X509PublicKeyCannotVerify), forwardResult._2)
        case Failure(msg, t, c) => (Failure(msg, t, c), forwardResult._2) // PEM certificate is not valid
        case _ => (Failure(ErrorMessages.X509GeneralError), forwardResult._2) // PEM certificate cannot be validated
      }
    } else {
      forwardResult
    }
    
  }

  private def getPem(forwardResult: (Box[User], Option[CallContext])): String = {
    val requestHeaders = forwardResult._2.map(_.requestHeaders).getOrElse(Nil)
    forwardResult._2.flatMap(_.consumer.map(_.clientCertificate.get)) match {
      case Some(certificate) if !(certificate == null || certificate.isEmpty) => certificate
      case _ => APIUtil.`getPSD2-CERT`(requestHeaders).getOrElse("None")
    }
  }

  def main(args: Array[String]): Unit = {
    // RSA signatures require a public and private RSA key pair,
    // the public key must be made known to the JWS recipient to
    // allow the signatures to be verified
    val jwk: JWK = JWK.parseFromPEMEncodedObjects(pemEncodedRSAPrivateKey)
    val rsaJWK: RSAKey = jwk.toRSAKey
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
      s"""(request-target): post /berlin-group/v1.3/payments/sepa-credit-transfers
          |host: api.testbank.com
          |content-type: application/json
          |psu-ip-address: 192.168.8.78
          |psu-geo-location: GEO:52.506931,13.144558
          |digest: SHA-256=$digest
          |""".stripMargin);

    
    val criticalParams: Set[String] = new util.HashSet[String]()
    criticalParams.add("b64")
    criticalParams.addAll(getDeferredCriticalHeaders)
    
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
    val sigT = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    
    // Create and sign JWS
    val jwsProtectedHeader: JWSHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
      .base64URLEncodePayload(false)
      .x509CertSHA256Thumbprint(rsaJWK.computeThumbprint())
      .criticalParams(criticalParams)
      .customParam("sigT", sigT)
      .customParam("sigD", JSONObjectUtils.parse(sigD))
      .build();
  
    val jwsObject: JWSObject = new JWSObject(jwsProtectedHeader, detachedPayload);

    // Compute the RSA signature
    jwsObject.sign(signer)
  
    val isDetached = true
    val jws: String = jwsObject.serialize(isDetached)
  
    // The resulting JWS, note the payload is not encoded (empty second part)
    // eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJIUzI1NiJ9..
    // 5rPBT_XW-x7mjc1ubf4WwW1iV2YJyc4CCFxORIEaAEk

    // Hard-coded request headers
    val requestHeaders = List(
      HTTPParam("x-jws-signature", List(jws)),
      // HTTPParam("(request-target)", List("post /v1.3/payments/sepa-credit-transfers")),
      HTTPParam("host", List("api.testbank.com")),
      HTTPParam("content-type", List("application/json")),
      HTTPParam("psu-ip-address", List("192.168.8.78")),
      HTTPParam("psu-geo-location", List("GEO:52.506931,13.144558")),
      HTTPParam("digest", List(s"SHA-256=$digest"))
    )

    validate(pemEncodedCertificate)
    val jwkPublic: JWK = JWK.parseFromPEMEncodedObjects(pemEncodedCertificate)
    val isVerified = verifyJws(jwkPublic.toRSAKey.toRSAPublicKey, httpBody, requestHeaders, "post", "/berlin-group/v1.3/payments/sepa-credit-transfers")
    org.scalameta.logger.elem(isVerified)
    org.scalameta.logger.elem(jws)
    org.scalameta.logger.elem(pemEncodedCertificate)
    
  }
  
}
