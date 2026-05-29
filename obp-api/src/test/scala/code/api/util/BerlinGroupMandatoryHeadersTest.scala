package code.api.util

import code.api.berlin.group.v1_3.BerlinGroupServerSetupV1_3
import net.liftweb.common.Failure
import code.api.util.APIUtil.HTTPParam
import org.scalatest.Tag

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Unit tests for BerlinGroupCheck mandatory headers validation.
 *
 * Extends BerlinGroupServerSetupV1_3 so Lift is bootstrapped and Props is
 * available. BerlinGroupCheck.berlinGroupMandatoryHeaders is a def, so
 * setPropsValues in beforeEach correctly controls which headers are required.
 */
class BerlinGroupMandatoryHeadersTest extends BerlinGroupServerSetupV1_3 {

  object MandatoryHeaders extends Tag("BerlinGroupMandatoryHeaders")

  // A Berlin Group v1.3 URL that triggers the mandatory header check
  private val bgUrl = "/berlin-group/v1.3/accounts"
  private val bgConsentUrl = "/berlin-group/v1.3/consents"

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Enable only X-Request-ID as mandatory header so tests stay focused
    setPropsValues(
      "berlin_group_mandatory_headers" -> "X-Request-ID",
      "berlin_group_mandatory_header_consent" -> ""
    )
  }

  // Helper: build HTTPParam list from a Map
  private def toParams(headers: Map[String, String]): List[HTTPParam] =
    headers.map { case (k, v) => HTTPParam(k, List(v)) }.toList

  // Helper: call BerlinGroupCheck.validate and block for result.
  // fullBoxOrException throws on validation errors, so we catch and wrap as Failure.
  private def callValidate(url: String, verb: String = "GET", headers: Map[String, String]): net.liftweb.common.Box[_] = {
    val params = toParams(headers)
    val emptyContext: (net.liftweb.common.Box[com.openbankproject.commons.model.User], Option[CallContext]) =
      (net.liftweb.common.Empty, None)
    try {
      val future = BerlinGroupCheck.validate(
        body = net.liftweb.common.Full("{}"),
        verb = verb,
        url = url,
        reqHeaders = params,
        forwardResult = emptyContext
      )
      Await.result(future, 5.seconds)._1
    } catch {
      case e: Exception => net.liftweb.common.Failure(e.getMessage, net.liftweb.common.Full(e), net.liftweb.common.Empty)
    }
  }

  // ─── Missing header tests ────────────────────────────────────────────────

  feature("BG mandatory headers - missing header") {

    scenario("Request without X-Request-ID is rejected", MandatoryHeaders) {
      Given("A BG request with no headers at all")
      val result = callValidate(bgUrl, headers = Map.empty)

      Then("Result is a Failure with missing header error")
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("OBP-20251")
      result.asInstanceOf[Failure].msg should include("x-request-id")
    }

    scenario("Non-BG URL skips the check", MandatoryHeaders) {
      Given("A non-BG URL with no headers")
      val result = callValidate("/obp/v4.0.0/banks", headers = Map.empty)

      Then("Validation is skipped — result is Empty")
      result shouldBe net.liftweb.common.Empty
    }
  }

  // ─── X-Request-ID format tests ───────────────────────────────────────────

  feature("BG mandatory headers - X-Request-ID format") {

    scenario("Valid UUID X-Request-ID is accepted", MandatoryHeaders) {
      val result = callValidate(bgUrl, headers = Map("X-Request-ID" -> UUID.randomUUID().toString))
      result shouldBe net.liftweb.common.Empty
    }

    scenario("Non-UUID X-Request-ID is rejected", MandatoryHeaders) {
      val result = callValidate(bgUrl, headers = Map("X-Request-ID" -> "not-a-uuid"))
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("OBP-20253")
    }

    scenario("Empty X-Request-ID is rejected", MandatoryHeaders) {
      val result = callValidate(bgUrl, headers = Map("X-Request-ID" -> ""))
      result shouldBe a[Failure]
    }
  }

  // ─── Date format tests ───────────────────────────────────────────────────

  feature("BG mandatory headers - Date format") {

    scenario("Valid RFC 7231 Date is accepted", MandatoryHeaders) {
      // Build a valid RFC 7231 date directly with the same format used by isValidRfc7231Date
      val fmt = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH)
      fmt.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
      val validDate = fmt.format(new java.util.Date())
      val result = callValidate(bgUrl, headers = Map(
        "X-Request-ID" -> UUID.randomUUID().toString,
        "Date" -> validDate
      ))
      result shouldBe net.liftweb.common.Empty
    }

    scenario("ISO date format is rejected (not RFC 7231)", MandatoryHeaders) {
      val result = callValidate(bgUrl, headers = Map(
        "X-Request-ID" -> UUID.randomUUID().toString,
        "Date" -> "2026-03-17"
      ))
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("OBP-20257")
    }
  }

  // ─── Consent endpoint extra header tests ─────────────────────────────────

  feature("BG mandatory headers - /consents TPP-Redirect-URI") {

    scenario("Consent request missing TPP-Redirect-URI is rejected", MandatoryHeaders) {
      setPropsValues(
        "berlin_group_mandatory_headers" -> "X-Request-ID",
        "berlin_group_mandatory_header_consent" -> "TPP-Redirect-URI"
      )
      val result = callValidate(bgConsentUrl, headers = Map("X-Request-ID" -> UUID.randomUUID().toString))
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("OBP-20251")
      result.asInstanceOf[Failure].msg should include("tpp-redirect-uri")
    }

    scenario("Consent request with TPP-Redirect-URI passes header check", MandatoryHeaders) {
      setPropsValues(
        "berlin_group_mandatory_headers" -> "X-Request-ID",
        "berlin_group_mandatory_header_consent" -> "TPP-Redirect-URI"
      )
      val result = callValidate(bgConsentUrl, headers = Map(
        "X-Request-ID" -> UUID.randomUUID().toString,
        "TPP-Redirect-URI" -> "https://example.com/callback"
      ))
      result should not be a[Failure]
    }
  }

  // ─── Disabled check ───────────────────────────────────────────────────────

  feature("BG mandatory headers - disabled when list is empty") {

    scenario("All requests pass when mandatory headers list is empty", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe net.liftweb.common.Empty
    }
  }

  // ─── Multiple missing headers ─────────────────────────────────────────────

  feature("BG mandatory headers - multiple missing headers") {

    scenario("Multiple missing headers are all reported", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "X-Request-ID,Content-Type,Date")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("OBP-20251")
      // All three missing headers should appear in the error message
      result.asInstanceOf[Failure].msg should include("x-request-id")
      result.asInstanceOf[Failure].msg should include("content-type")
      result.asInstanceOf[Failure].msg should include("date")
    }

    scenario("Providing one of two required headers still fails", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "X-Request-ID,Content-Type")
      val result = callValidate(bgUrl, headers = Map("X-Request-ID" -> UUID.randomUUID().toString))
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("content-type")
    }
  }

  // ─── Content-Type header ──────────────────────────────────────────────────

  feature("BG mandatory headers - Content-Type") {

    scenario("Missing Content-Type is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "Content-Type")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("content-type")
    }

    scenario("Present Content-Type passes", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "Content-Type")
      val result = callValidate(bgUrl, headers = Map("Content-Type" -> "application/json"))
      result shouldBe net.liftweb.common.Empty
    }
  }

  // ─── Digest header ────────────────────────────────────────────────────────

  feature("BG mandatory headers - Digest") {

    scenario("Missing Digest is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "Digest")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("digest")
    }

    scenario("Present Digest passes header presence check", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "Digest")
      val digest = "SHA-256=" + java.util.Base64.getEncoder.encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest("{}".getBytes("UTF-8"))
      )
      val result = callValidate(bgUrl, headers = Map("Digest" -> digest))
      result shouldBe net.liftweb.common.Empty
    }
  }

  // ─── PSU headers ──────────────────────────────────────────────────────────

  feature("BG mandatory headers - PSU device headers") {

    scenario("Missing PSU-IP-Address is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "PSU-IP-Address")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("psu-ip-address")
    }

    scenario("Missing PSU-Device-ID is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "PSU-Device-ID")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("psu-device-id")
    }

    scenario("Missing PSU-Device-Name is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "PSU-Device-Name")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("psu-device-name")
    }

    scenario("All PSU headers present passes", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "PSU-IP-Address,PSU-Device-ID,PSU-Device-Name")
      val result = callValidate(bgUrl, headers = Map(
        "PSU-IP-Address"  -> "192.168.1.1",
        "PSU-Device-ID"   -> UUID.randomUUID().toString,
        "PSU-Device-Name" -> "Chrome/120"
      ))
      result shouldBe net.liftweb.common.Empty
    }
  }

  // ─── Signature + TPP-Signature-Certificate headers ────────────────────────

  feature("BG mandatory headers - Signature and TPP-Signature-Certificate") {

    scenario("Missing Signature is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "Signature")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("signature")
    }

    scenario("Missing TPP-Signature-Certificate is rejected", MandatoryHeaders) {
      setPropsValues("berlin_group_mandatory_headers" -> "TPP-Signature-Certificate")
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("tpp-signature-certificate")
    }
  }

  // ─── Full default header set ──────────────────────────────────────────────

  feature("BG mandatory headers - full default set") {

    scenario("All 9 default headers missing are all reported", MandatoryHeaders) {
      setPropsValues(
        "berlin_group_mandatory_headers" ->
          "Content-Type,Date,Digest,PSU-Device-ID,PSU-Device-Name,PSU-IP-Address,Signature,TPP-Signature-Certificate,X-Request-ID"
      )
      val result = callValidate(bgUrl, headers = Map.empty)
      result shouldBe a[Failure]
      result.asInstanceOf[Failure].msg should include("OBP-20251")
    }
  }
}
