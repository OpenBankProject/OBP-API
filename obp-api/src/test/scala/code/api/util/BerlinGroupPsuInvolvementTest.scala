package code.api.util

import code.api.berlin.group.v1_3.BerlinGroupServerSetupV1_3
import code.api.util.APIUtil.HTTPParam
import org.scalatest.Tag

/**
 * `frequencyPerDay` limits "an access without PSU involvement per day", so everything turns on how
 * the ASPSP decides a PSU was not involved. NextGenPSD2 makes that a property of one header:
 * `PSU-IP-Address` "shall be contained if and only if this request was actively initiated by the
 * PSU". These scenarios pin that reading, in particular that a request carrying no PSU header at all
 * counts against the limit — the case a TPP running unattended actually produces.
 */
class BerlinGroupPsuInvolvementTest extends BerlinGroupServerSetupV1_3 {

  object PsuInvolvement extends Tag("BerlinGroupPsuInvolvement")

  private def headers(pairs: (String, String)*): List[HTTPParam] =
    pairs.map { case (name, value) => HTTPParam(name, List(value)) }.toList

  feature("Berlin Group - deciding whether the PSU was behind a request") {

    scenario("a request carrying no PSU-IP-Address is unattended", PsuInvolvement) {
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(
        headers("X-Request-ID" -> "5d8a7e2c-3c1f-4f7a-9a6e-1b0d2f3a4b5c")) should be(true)
    }

    scenario("an empty or blank PSU-IP-Address is no address at all", PsuInvolvement) {
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(headers("PSU-IP-Address" -> "")) should be(true)
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(headers("PSU-IP-Address" -> "   ")) should be(true)
    }

    scenario("a request carrying the PSU's address was initiated by the PSU", PsuInvolvement) {
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(headers("PSU-IP-Address" -> "192.168.8.78")) should be(false)
    }

    scenario("the header name is matched case-insensitively, as HTTP requires", PsuInvolvement) {
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(headers("psu-ip-address" -> "192.168.8.78")) should be(false)
    }

    scenario("the sentinel values still mark an unattended request", PsuInvolvement) {
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(headers("PSU-IP-Address" -> "0.0.0.0")) should be(true)
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(
        headers("PSU-IP-Address" -> "192.168.8.78", "PSU-Device-ID" -> "no-psu-involved")) should be(true)
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(
        headers("PSU-IP-Address" -> "192.168.8.78", "PSU-Device-Name" -> "no-psu-involved")) should be(true)
    }

    scenario("a real device id alongside a PSU address does not make the request unattended", PsuInvolvement) {
      BerlinGroupCheck.isTppRequestsWithoutPsuInvolvement(
        headers("PSU-IP-Address" -> "192.168.8.78", "PSU-Device-ID" -> "99435c7e-ad88-49ec-a2ad-99ddcb1f7721")) should be(false)
    }
  }
}
