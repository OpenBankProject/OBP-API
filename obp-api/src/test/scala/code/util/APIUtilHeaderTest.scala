package code.util

import code.api.DAuth
import code.api.util.{APIUtil, AuthorisationUtil, Consent, JwsUtil}
import code.api.v4_0_0.V400ServerSetup

class APIUtilHeaderTest extends V400ServerSetup {

  feature("Consent and PSD2 request header lookup") {
    scenario("HTTP/2 lowercase consent and PSD2 header names are accepted") {
      APIUtil.getConsentJWT(List(APIUtil.HTTPParam("consent-jwt", List("jwt")))) shouldBe Some("jwt")
      APIUtil.getConsentJWT(List(APIUtil.HTTPParam("consent-id", List("consent-id")))) shouldBe Some("consent-id")
      APIUtil.getConsentIdRequestHeaderValue(List(APIUtil.HTTPParam("consent-id", List("consent-id")))) shouldBe Some("consent-id")
      APIUtil.`getPSD2-CERT`(List(APIUtil.HTTPParam("psd2-cert", List("certificate")))) shouldBe Some("certificate")
      APIUtil.`getConsent-ID`(List(APIUtil.HTTPParam("consent-id", List("berlin-group-consent-id")))) shouldBe Some("berlin-group-consent-id")
      Consent.getConsumerKey(List(APIUtil.HTTPParam("consumer-key", List("consumer-key")))) shouldBe Some("consumer-key")
      APIUtil.getRequestHeader("PSU-ID", List(APIUtil.HTTPParam("psu-id", List("psu")))) shouldBe "psu"
      APIUtil.hasAuthorizationHeader(List(APIUtil.HTTPParam("authorization", List("Bearer token")))) shouldBe true
      APIUtil.hasDAuthHeader(List(APIUtil.HTTPParam("dauth", List("token")))) shouldBe true
      DAuth.getDAuthToken(List(APIUtil.HTTPParam("dauth", List("token")))) shouldBe Some(List("token"))
      JwsUtil.getJwsHeaderValue(List(APIUtil.HTTPParam("X-JWS-SIGNATURE", List("signature")))) shouldBe "signature"
      JwsUtil.checkRequestIsSigned(List(APIUtil.HTTPParam("DIGEST", List("digest")))) shouldBe true
      AuthorisationUtil.getAuthorisationHeaders(List(APIUtil.HTTPParam("consent-jwt", List("jwt")))) shouldBe List("consent-jwt")
    }
  }
}
