package code.util

import code.api.util.APIUtil
import code.api.v4_0_0.V400ServerSetup

class APIUtilHeaderTest extends V400ServerSetup {

  feature("Consent and PSD2 request header lookup") {
    scenario("HTTP/2 lowercase consent and PSD2 header names are accepted") {
      APIUtil.getConsentJWT(List(APIUtil.HTTPParam("consent-jwt", List("jwt")))) shouldBe Some("jwt")
      APIUtil.getConsentJWT(List(APIUtil.HTTPParam("consent-id", List("consent-id")))) shouldBe Some("consent-id")
      APIUtil.getConsentIdRequestHeaderValue(List(APIUtil.HTTPParam("consent-id", List("consent-id")))) shouldBe Some("consent-id")
      APIUtil.`getPSD2-CERT`(List(APIUtil.HTTPParam("psd2-cert", List("certificate")))) shouldBe Some("certificate")
      APIUtil.`getConsent-ID`(List(APIUtil.HTTPParam("consent-id", List("berlin-group-consent-id")))) shouldBe Some("berlin-group-consent-id")
    }
  }
}
