package code.api.util

import java.security.interfaces.RSAPublicKey
import java.time.format.DateTimeFormatter
import java.time.{Duration, ZoneOffset, ZonedDateTime}
import java.util

import code.api.Constant
import code.util.Helper.MdcLoggable
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json
import net.liftweb.util.SecurityHelpers
import sun.security.provider.X509Factory

import scala.collection.immutable.{HashMap, List}
import scala.jdk.CollectionConverters.seqAsJavaListConverter


object JwsUtil extends MdcLoggable {
  implicit val formats = CustomJsonFormats.formats
  case class JwsProtectedHeader(b64: Boolean,
                                `x5t#S256`: Option[String],
                                x5c: Option[List[String]],
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
        val timeDifference = Duration.between(verifyingTime, signingTime)
        val timeDifferenceInNanos = (timeDifference.abs.getSeconds * 1000000000L) + timeDifference.abs.getNano
        val criteriaOneOk = signingTime.isBefore(verifyingTime) || // Signing Time > Verifying Time otherwise
          (signingTime.isAfter(verifyingTime) && timeDifferenceInNanos < (2 * 1000000000L)) // IF "Verifying Time > Signing Time" THEN "Verifying Time - Signing Time < 2 seconds"
        val criteriaTwoOk = timeDifferenceInNanos < (60 * 1000000000L) // Signing Time - Verifying Time < 60 seconds
        criteriaOneOk && criteriaTwoOk
      case None => false
    }
  }
  def computeDigest(input: String): String = SecurityHelpers.hash256(input)
  def verifyDigestHeader(headerValue: String, httpBody: String): Boolean = {
    headerValue == s"SHA-256=${computeDigest(httpBody)}"
  }
  def getDigestHeaderValue(requestHeaders: List[HTTPParam]): String = {
    requestHeaders.find(_.name.toLowerCase == "digest").map(_.values.mkString).getOrElse("None")
  }
  def getJwsHeaderValue(requestHeaders: List[HTTPParam]): String = {
    requestHeaders.find(_.name == "x-jws-signature").map(_.values.mkString).getOrElse("None")
  }
  def checkRequestIsSigned(requestHeaders: List[HTTPParam]): Boolean = {
    requestHeaders.find(_.name == "x-jws-signature").isDefined ||
    requestHeaders.find(_.name == "digest").isDefined
  }
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
    val isVerifiedSigningTime = verifySigningTime(jwsProtectedHeaderAsString)
    logger.debug("JWS Protected Header: " + jwsProtectedHeaderAsString)
    logger.debug("Rebuilt Detached Payload: " + rebuiltDetachedPayload)
    logger.debug("Is Verified Jws: " + isVerifiedJws)
    logger.debug("Is Verified Digest Header: " + isVerifiedDigestHeader)
    logger.debug("Is Verified Signing Time: " + isVerifiedSigningTime)
    logger.debug("X-JWS-Signature: " + xJwsSignature)
    logger.debug("Digest Header Value: " + getDigestHeaderValue(requestHeaders))
    isVerifiedJws && isVerifiedDigestHeader && isVerifiedSigningTime
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
    if(forceVerifyRequestSignResponse(url)){
      checkRequestIsSigned(forwardResult._2.map(_.requestHeaders).getOrElse(Nil)) match {
        case false => 
          (Failure(ErrorMessages.X509RequestIsNotSigned), forwardResult._2)
        case true =>
          val pem: String = getPem(forwardResult._2.map(_.requestHeaders).getOrElse(Nil))
          X509.validate(pem) match {
            case Full(true) => // PEM certificate is ok
              val jwkPublic: JWK = X509.pemToRsaJwk(pem)
              val isVerified = JwsUtil.verifyJws(jwkPublic.toRSAKey.toRSAPublicKey, body.getOrElse(""), reqHeaders, verb, url)
              if (isVerified) forwardResult else (Failure(ErrorMessages.X509PublicKeyCannotVerify), forwardResult._2)
            case Failure(msg, t, c) => (Failure(msg, t, c), forwardResult._2) // PEM certificate is not valid
            case _ => (Failure(ErrorMessages.X509GeneralError), forwardResult._2) // PEM certificate cannot be validated
          }
      }
    } else {
      forwardResult
    }
    
  }
  
  def forceVerifyRequestSignResponse(url: String): Boolean = {
    val standards: List[String] = APIUtil.getPropsValue(nameOfProperty="force_jws", "None").split(",").map(_.trim).toList
    val pathOfStandard = HashMap(
      "BGv1.3"->"berlin-group/v1.3", 
      "OBPv4.0.0"->"obp/v4.0.0", 
      "OBPv5.0.0"->"obp/v5.0.0", 
      "OBPv3.1.0"->"obp/v3.1.0", 
      "UKv1.3"->"open-banking/v3.1"
    ).withDefaultValue("{Not found any standard to match}")
    standards.exists(standard => url.contains(pathOfStandard(standard))) || url.contains("development/echo/jws-verified-request-jws-signed-response")
  }
  

  def getPem(requestHeaders: List[HTTPParam]): String = {
    val xJwsSignature = getJwsHeaderValue(requestHeaders)
    val jwsProtectedHeaderAsString = JWSObject.parse(xJwsSignature).getHeader().toString()
    val x5c = json.parse(jwsProtectedHeaderAsString).extractOpt[JwsProtectedHeader] match {
      case Some(header) =>
        header.x5c.map(_.headOption.getOrElse("None")).getOrElse("None")
      case None => "None"
    }
    s"""${X509Factory.BEGIN_CERT}
       |$x5c
       |${X509Factory.END_CERT}
       |""".stripMargin
  }

  private def signRequestResponseCommon(body: Box[String], 
                                        verb: String, 
                                        url: String, 
                                        requestResponse: String, 
                                        contentType: String,
                                        psuIpAddress: Option[String] = None,
                                        psuGeoLocation: Option[String] = None,
                                        signingTime: Option[ZonedDateTime] = None
                                       ): List[HTTPParam] = {
    val digest = "SHA-256=" + computeDigest(body.getOrElse(""))
    // The payload which will not be encoded and must be passed to
    // the JWS consumer in a detached manner
    val host = Constant.HostName
    val detachedPayload: Payload = new Payload(
      s"""($requestResponse): ${verb.toLowerCase} ${url}
         |host: ${host}
         |content-type: $contentType
         |psu-ip-address: ${psuIpAddress.getOrElse("None")}
         |psu-geo-location: ${psuGeoLocation.getOrElse("None")}
         |digest: $digest
         |""".stripMargin)
    logger.debug("Detached Payload of Signing: " + detachedPayload)
    
    val sigD =
      s"""{
        |    "pars": [
        |      "($requestResponse)",
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
    val sigT: String = signingTime match {
      case None => ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
      case Some(time) => time.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }
    val criticalParams: util.Set[String] = new util.HashSet[String]()
    criticalParams.add("b64")
    criticalParams.addAll(getDeferredCriticalHeaders)
    // Create and sign JWS
    val jwsProtectedHeader: JWSHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
      .base64URLEncodePayload(false)
      .x509CertChain(List(new com.nimbusds.jose.util.Base64(CertificateUtil.x5c)).asJava)
      .criticalParams(criticalParams)
      .customParam("sigT", sigT)
      .customParam("sigD", JSONObjectUtils.parse(sigD))
      .build();
    val jwsObject: JWSObject = new JWSObject(jwsProtectedHeader, detachedPayload)


    // Compute the RSA signature
    jwsObject.sign(CertificateUtil.rsaSigner)

    val isDetached = true
    val jws: String = jwsObject.serialize(isDetached)

    List(HTTPParam("x-jws-signature", List(jws)), HTTPParam("digest", List(digest))) :::
    List(
      HTTPParam("host", List(host)),
      HTTPParam("content-type", List(contentType)),
      HTTPParam("psu-ip-address", List(psuIpAddress.getOrElse("None"))),
      HTTPParam("psu-geo-location", List(psuGeoLocation.getOrElse("None"))),
    )
  }

  /**
   * This function signs request we send to a TPP app.
   * @param body HTTP body of an request 
   * @param verb HTTP method of an request
   * @param url HTTP relative path of an request 
   * @return Request header params: x-jws-signature and digest
   */
  def signResponse(body: Box[String], verb: String, url: String, contentType: String): List[HTTPParam] = {
    signRequestResponseCommon(body, verb, url, "status-line", contentType)
  }

  /**
   * This function simulates signing request at a TPP app.
   * @param body HTTP body of an request 
   * @param verb HTTP method of an request
   * @param url HTTP relative path of an request 
   * @return Request header params: x-jws-signature and digest
   */
  def signRequest(body: Box[String], verb: String, url: String, contentType: String, signingTime: Option[ZonedDateTime] = None): List[HTTPParam] = {
    signRequestResponseCommon(body, verb, url, "request-target", contentType, signingTime = signingTime)
  }
  
}
