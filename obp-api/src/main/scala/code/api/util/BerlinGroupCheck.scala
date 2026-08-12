package code.api.util

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.BgSpecValidation
import code.api.{APIFailureNewStyle, RequestHeader}
import code.api.util.APIUtil.{HTTPParam, OBPReturnType, fullBoxOrException}
import code.api.util.BerlinGroupSigning.{getCertificateFromTppSignatureCertificate, getHeaderValue}
import code.metrics.MappedMetric
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Empty}

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.mapper.By

object BerlinGroupCheck extends MdcLoggable {


  private val defaultMandatoryHeaders = "Content-Type,Date,Digest,PSU-Device-ID,PSU-Device-Name,PSU-IP-Address,Signature,TPP-Signature-Certificate,X-Request-ID"
  // Parse mandatory headers from a comma-separated string (def so tests can override via Props)
  private def berlinGroupMandatoryHeaders: List[String] = APIUtil.getPropsValue("berlin_group_mandatory_headers", defaultValue = defaultMandatoryHeaders)
    .split(",")
    .map(_.trim.toLowerCase)
    .toList.filterNot(_.isEmpty)
  private def berlinGroupMandatoryHeaderConsent: List[String] = APIUtil.getPropsValue("berlin_group_mandatory_header_consent", defaultValue = "TPP-Redirect-URI")
    .split(",")
    .map(_.trim.toLowerCase)
    .toList.filterNot(_.isEmpty)

  def hasUnwantedConsentIdHeaderForBGEndpoint(path: String, reqHeaders: List[HTTPParam]): Boolean = {
    val headerMap: Map[String, HTTPParam] = reqHeaders.map(h => h.name.toLowerCase -> h).toMap
    val hasConsentIdId = headerMap.get(RequestHeader.`Consent-ID`.toLowerCase).flatMap(_.values.headOption).isDefined

    val parts = path.stripPrefix("/").stripSuffix("/").split("/").toList
    val doesNotRequireConsentId = parts.reverse match {
      case "consents" :: restOfThePath => true
      case consentId :: "consents" :: restOfThePath => true
      case "status" :: consentId :: "consents" :: restOfThePath => true
      case "authorisations" :: consentId :: "consents" :: restOfThePath => true
      case authorisationId :: "authorisations" :: consentId :: "consents" :: restOfThePath => true
      case _ => false
    }
    doesNotRequireConsentId && hasConsentIdId && path.contains(ConstantsBG.berlinGroupVersion1.urlPrefix)
  }

  private def validateHeaders(
                               verb: String,
                               url: String,
                               reqHeaders: List[HTTPParam],
                               forwardResult: (Box[User], Option[CallContext])
                             ): (Box[User], Option[CallContext]) = {

    val headerMap: Map[String, HTTPParam] = reqHeaders.map(h => h.name.toLowerCase -> h).toMap
    val maybeRequestId: Option[String] = headerMap.get(RequestHeader.`X-Request-ID`.toLowerCase).flatMap(_.values.headOption)

    val missingHeaders: List[String] = {
      if (url.contains(ConstantsBG.berlinGroupVersion1.urlPrefix) && url.endsWith("/consents"))
        (berlinGroupMandatoryHeaders ++ berlinGroupMandatoryHeaderConsent).filterNot(headerMap.contains)
      else
        berlinGroupMandatoryHeaders.filterNot(headerMap.contains)
    }

    val resultWithWrongDateHeaderCheck: Option[(Box[User], Option[CallContext])] = {
      val date: Option[String] = headerMap.get(RequestHeader.Date.toLowerCase).flatMap(_.values.headOption)
      if (date.isDefined && !DateTimeUtil.isValidRfc7231Date(date.get)) {
        val message = ErrorMessages.NotValidRfc7231Date
        Some(
          (
            fullBoxOrException(
              Empty ~> APIFailureNewStyle(message, 400, forwardResult._2.map(_.toLight))
            ),
            forwardResult._2
          )
        )
      } else None
    }

    val resultWithMissingHeaderCheck: Option[(Box[User], Option[CallContext])] =
      if (missingHeaders.nonEmpty) {
        val message = if (missingHeaders.size == 1)
          ErrorMessages.MissingMandatoryBerlinGroupHeaders.replace("headers", "header")
        else
          ErrorMessages.MissingMandatoryBerlinGroupHeaders

        Some(
          (
            fullBoxOrException(
              Empty ~> APIFailureNewStyle(s"$message(${missingHeaders.mkString(", ")})", 400, forwardResult._2.map(_.toLight))
            ),
            forwardResult._2
          )
        )
      } else None

    val resultWithInvalidRequestIdCheck: Option[(Box[User], Option[CallContext])] =
      if (maybeRequestId.exists(id => !APIUtil.checkIfStringIsUUID(id))) {
        Some(
          (
            fullBoxOrException(
              Empty ~> APIFailureNewStyle(s"${ErrorMessages.InvalidUuidValue} (${RequestHeader.`X-Request-ID`})", 400, forwardResult._2.map(_.toLight))
            ),
            forwardResult._2
          )
        )
      } else None

    val resultWithRequestIdUsedTwiceCheck: Option[(Box[User], Option[CallContext])] = {
      val alreadyUsed = maybeRequestId match {
        case Some(id) =>
          MappedMetric.findAll(By(MappedMetric.correlationId, id), By(MappedMetric.verb, "POST"), By(MappedMetric.httpCode, 201)).nonEmpty
        case None =>
          false
      }
      if (alreadyUsed) {
        Some(
          (
            fullBoxOrException(
              Empty ~> APIFailureNewStyle(s"${ErrorMessages.InvalidRequestIdValueAlreadyUsed}(${RequestHeader.`X-Request-ID`})", 400, forwardResult._2.map(_.toLight))
            ),
            forwardResult._2
          )
        )
      } else None
    }


    // === Signature Header Parsing ===
    val resultWithInvalidSignatureHeaderCheck: Option[(Box[User], Option[CallContext])] = {
      val maybeSignature: Option[String] = headerMap.get("signature").flatMap(_.values.headOption)
      maybeSignature.flatMap { header =>
        BerlinGroupSignatureHeaderParser.parseSignatureHeader(header) match {
          case Right(parsed) =>
            logger.debug(s"Parsed Signature Header:")
            logger.debug(s"  SN: ${parsed.keyId.sn}")
            logger.debug(s"  CA: ${parsed.keyId.ca}")
            logger.debug(s"  CN: ${parsed.keyId.cn}")
            logger.debug(s"  O:  ${parsed.keyId.o}")
            logger.debug(s"  Headers: ${parsed.headers.mkString(", ")}")
            logger.debug(s"  Algorithm: ${parsed.algorithm}")
            logger.debug(s"  Signature: ${parsed.signature}")

            // A Signature header without a usable TPP-Signature-Certificate cannot have its keyId.SN
            // checked against anything, so it is an invalid signature header (400) — not a 500.
            val certSerialNumber = getCertificateFromTppSignatureCertificate(reqHeaders).map(_.getSerialNumber)

            logger.debug(s"Certificate serial number (decimal): ${certSerialNumber.map(_.toString)}")
            logger.debug(s"Certificate serial number (hex): ${certSerialNumber.map(sn => sn.toString(16).toUpperCase)}")

            val snMatches = certSerialNumber
              .map(BerlinGroupSignatureHeaderParser.doesSerialNumberMatch(parsed.keyId.sn, _))
              .getOrElse(false)

            if (!snMatches) {
              logger.debug(s"Serial number mismatch (or no usable certificate). Parsed SN: ${parsed.keyId.sn}, Certificate decimal: ${certSerialNumber.map(_.toString)}, Certificate hex: ${certSerialNumber.map(sn => sn.toString(16).toUpperCase)}")
              Some(
                (
                  fullBoxOrException(
                    Empty ~> APIFailureNewStyle(
                      s"${ErrorMessages.InvalidSignatureHeader} keyId.SN does not match the serial number from certificate",
                      400,
                      forwardResult._2.map(_.toLight)
                    )
                  ),
                  forwardResult._2
                )
              )
            } else {
              None // All good
            }

          case Left(error) =>
            Some(
              (
                fullBoxOrException(
                  Empty ~> APIFailureNewStyle(
                    s"${ErrorMessages.InvalidSignatureHeader} $error",
                    400,
                    forwardResult._2.map(_.toLight)
                  )
                ),
                forwardResult._2
              )
            )
        }
      }
    }

    // Chain validation steps
    resultWithMissingHeaderCheck
      .orElse(resultWithWrongDateHeaderCheck)
      .orElse(resultWithInvalidRequestIdCheck)
      .orElse(resultWithRequestIdUsedTwiceCheck)
      .orElse(resultWithInvalidSignatureHeaderCheck)
      .getOrElse(forwardResult)
  }

  /**
   * Whether the PSU was behind this request, which is what `frequencyPerDay` counts: it is "the
   * requested maximum frequency for an access without PSU involvement per day".
   *
   * NextGenPSD2 settles the question with one header. On every AIS read and consent-management call,
   * `PSU-IP-Address` "shall be contained if and only if this request was actively initiated by the
   * PSU" (psd2-api v1.3, parameter `PSU-IP-Address_conditionalForAis`). Omitting it is therefore the
   * TPP's declaration that no PSU is involved, and that is the case the daily limit governs.
   *
   * This used to be read the other way round: only a request carrying a sentinel value counted, so a
   * TPP that simply sent nothing — the very shape the spec reserves for unattended access — was never
   * counted at all, and each TPP decided whether its own daily limit applied to it.
   *
   * The two sentinels are still honoured, for a TPP that sends the header unconditionally and marks
   * the no-PSU case in the value rather than by omission.
   */
  def isTppRequestsWithoutPsuInvolvement(requestHeaders: List[HTTPParam]): Boolean = {
    def valueOf(name: String): Option[String] =
      requestHeaders.find(_.name.equalsIgnoreCase(name)).map(_.values.mkString.trim).filter(_.nonEmpty)

    val psuIpAddress = valueOf(RequestHeader.`PSU-IP-Address`)
    val markedAsUnattended = psuIpAddress.contains("0.0.0.0") ||
      valueOf(RequestHeader.`PSU-Device-ID`).contains("no-psu-involved") ||
      valueOf(RequestHeader.`PSU-Device-Name`).contains("no-psu-involved")

    val withoutPsu = psuIpAddress.isEmpty || markedAsUnattended
    logger.debug(s"isTppRequestsWithoutPsuInvolvement: $withoutPsu (PSU-IP-Address: $psuIpAddress)")
    withoutPsu
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
