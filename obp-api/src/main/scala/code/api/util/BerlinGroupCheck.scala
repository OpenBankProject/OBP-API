package code.api.util

import code.api.berlin.group.ConstantsBG
import code.api.{APIFailureNewStyle, RequestHeader}
import code.api.util.APIUtil.{OBPReturnType, fullBoxOrException}
import code.api.util.BerlinGroupSigning.getHeaderValue
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Empty}
import net.liftweb.http.provider.HTTPParam

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object BerlinGroupCheck extends MdcLoggable {


  private val defaultMandatoryHeaders = "Content-Type,Date,Digest,PSU-Device-ID,PSU-Device-Name,PSU-IP-Address,Signature,TPP-Signature-Certificate,X-Request-ID"
  // Parse mandatory headers from a comma-separated string
  private val berlinGroupMandatoryHeaders: List[String] = APIUtil.getPropsValue("berlin_group_mandatory_headers", defaultValue = defaultMandatoryHeaders)
    .split(",")
    .map(_.trim.toLowerCase)
    .toList.filterNot(_.isEmpty)
  private val berlinGroupMandatoryHeaderConsent = APIUtil.getPropsValue("berlin_group_mandatory_header_consent", defaultValue = "TPP-Redirect-URI")
    .split(",")
    .map(_.trim.toLowerCase)
    .toList.filterNot(_.isEmpty)

  private def validateHeaders(verb: String, url: String, reqHeaders: List[HTTPParam], forwardResult: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    val headerMap = reqHeaders.map(h => h.name.toLowerCase -> h).toMap
    val missingHeaders = if(url.contains(ConstantsBG.berlinGroupVersion1.urlPrefix) && url.endsWith("/consents"))
      (berlinGroupMandatoryHeaders ++ berlinGroupMandatoryHeaderConsent).filterNot(headerMap.contains)
    else
      berlinGroupMandatoryHeaders.filterNot(headerMap.contains)

    if (missingHeaders.isEmpty) {
      forwardResult // All mandatory headers are present
    } else {
      if(missingHeaders.size == 1) {
        (fullBoxOrException(Empty ~> APIFailureNewStyle(s"${ErrorMessages.MissingMandatoryBerlinGroupHeaders.replace("headers", "header")}(${missingHeaders.mkString(", ")})", 400, forwardResult._2.map(_.toLight))), forwardResult._2)
      } else {
        (fullBoxOrException(Empty ~> APIFailureNewStyle(s"${ErrorMessages.MissingMandatoryBerlinGroupHeaders}(${missingHeaders.mkString(", ")})", 400, forwardResult._2.map(_.toLight))), forwardResult._2)
      }
    }
  }

  def isTppRequestsWithoutPsuInvolvement(requestHeaders: List[HTTPParam]): Boolean = {
    val psuIpAddress = getHeaderValue(RequestHeader.`PSU-IP-Address`, requestHeaders)
    val psuDeviceId = getHeaderValue(RequestHeader.`PSU-Device-ID`, requestHeaders)
    val psuDeviceNAme = getHeaderValue(RequestHeader.`PSU-Device-Name`, requestHeaders)
    if(psuIpAddress == "0.0.0.0" || psuDeviceId == "no-psu-involved" || psuDeviceNAme == "no-psu-involved") {
      logger.debug(s"isTppRequestsWithoutPsuInvolvement.psuIpAddress: $psuIpAddress")
      logger.debug(s"isTppRequestsWithoutPsuInvolvement.psuDeviceId: $psuDeviceId")
      logger.debug(s"isTppRequestsWithoutPsuInvolvement.psuDeviceNAme: $psuDeviceNAme")
      true
    } else {
      false
    }
  }

  def validate(body: Box[String], verb: String, url: String, reqHeaders: List[HTTPParam], forwardResult: (Box[User], Option[CallContext])): OBPReturnType[Box[User]] = {
    if(url.contains(ConstantsBG.berlinGroupVersion1.urlPrefix)) {
      validateHeaders(verb, url, reqHeaders, forwardResult) match {
        case (user, _) if user.isDefined || user == Empty => // All good. Chain another check
          // Verify signed request (Berlin Group)
          BerlinGroupSigning.verifySignedRequest(body, verb, url, reqHeaders, forwardResult) match {
            case (user, cc) if (user.isDefined || user == Empty) && cc.exists(_.consumer.isEmpty) => // There is no Consumer in the database
              // Create Consumer on the fly on a first usage of RequestHeader.`TPP-Signature-Certificate`
              logger.info(s"Start BerlinGroupSigning.getOrCreateConsumer")
              BerlinGroupSigning.getOrCreateConsumer(reqHeaders, forwardResult)
            case forwardError => // Forward error case
              Future(forwardError)
          }
        case forwardError => // Forward error case
          Future(forwardError)
      }
    } else {
      Future(forwardResult)
    }
  }

}
