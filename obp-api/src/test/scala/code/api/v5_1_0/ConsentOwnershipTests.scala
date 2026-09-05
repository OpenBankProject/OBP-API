/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v5_1_0

import org.json4s._
import code.api.{Constant, RequestHeader}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{ConsentJsonV310, PostConsentChallengeJsonV310, PostConsentEntitlementJsonV310, PostConsentViewJsonV310}
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * Who the OBP-native consent read answers to.
 *
 * GET /obp/v5.1.0/user/current/consents/CONSENT_ID compared the consent's PSU against
 * CallContext.userId -- the authenticated principal. Under Consent-Id / Consent-JWT authentication
 * that principal is the per-consent shadow user, never the human, so the comparison could not match
 * and the PSU was told their own consent did not exist. The subject is now CallContext.onBehalfOfUser,
 * the accessor the codebase already keeps for exactly this distinction.
 *
 * The rule, and why a consent with no PSU yet stays readable by anyone, is in
 * Consent.checkObpConsentUserAccess.
 */
class ConsentOwnershipTests extends V510ServerSetup with PropsReset {

  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object CreateConsent extends Tag(nameOf(Implementations5_1_0.createConsent))
  object ConsentOwnership extends Tag("ConsentOwnership")

  private val psu = "psu-user-id"
  private val otherPsu = "someone-else-user-id"

  feature("Consent.checkObpConsentUserAccess") {

    scenario("the PSU a consent is bound to may read it", ConsentOwnership) {
      Consent.checkObpConsentUserAccess(psu, Some(psu)) should equal(None)
    }

    scenario("a different human may not read a bound consent", ConsentOwnership) {
      Consent.checkObpConsentUserAccess(psu, Some(otherPsu)) should equal(Some(ConsentNotFound))
    }

    scenario("a caller with no human at all may not read a bound consent", ConsentOwnership) {
      Consent.checkObpConsentUserAccess(psu, None) should equal(Some(ConsentNotFound))
    }

    // Deliberate, and load-bearing: this endpoint is where a PSU inspects a consent before deciding
    // to authorise it, and the app doing the inspecting belongs to the PSU, not to the TPP that
    // lodged the consent. See the Berlin Group SCA regression in AccountInformationServiceAISApiTest.
    scenario("a consent with no PSU yet is readable", ConsentOwnership) {
      Consent.checkObpConsentUserAccess("", Some(psu)) should equal(None)
      Consent.checkObpConsentUserAccess(null, None) should equal(None)
      Consent.checkObpConsentUserAccess("   ", Some(otherPsu)) should equal(None)
    }
  }

  private val validHeaderConsumerKey =
    List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))

  private lazy val bankId = randomBankId
  private lazy val bankAccount = randomPrivateAccount(bankId)
  private lazy val entitlements = List(PostConsentEntitlementJsonV310("", CanGetAnyUser.toString()))
  private lazy val views = List(PostConsentViewJsonV310(bankId, bankAccount.id, Constant.SYSTEM_OWNER_VIEW_ID))
  private lazy val postConsentImplicitJsonV310 = SwaggerDefinitionsJSON.postConsentImplicitJsonV310
    .copy(entitlements = entitlements)
    .copy(consumer_id = Some(testConsumer.consumerId.get))
    .copy(views = views)

  // Lodge an OBP-native consent for resourceUser1 and take it through SCA, so it ends up ACCEPTED
  // and usable as a credential. Returns (consentId, consentJWT).
  private def acceptedConsentOfUser1(): (String, String) = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

    val request = (v5_1_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1)
    val response = makePostRequest(request, write(postConsentImplicitJsonV310), validHeaderConsumerKey)
    response.code should equal(201)
    val consentId = response.body.extract[ConsentJsonV310].consent_id
    val jwt = response.body.extract[ConsentJsonV310].jwt

    val challengeRequest = (v5_1_0_Request / "banks" / bankId / "consents" / consentId / "challenge").POST <@ (user1)
    val challengeResponse = makePostRequest(
      challengeRequest, write(PostConsentChallengeJsonV310(answer = Consent.challengeAnswerAtTestEnvironment)))
    challengeResponse.code should equal(201)

    (consentId, jwt)
  }

  feature("Consent-authenticated reads of GET /user/current/consents/CONSENT_ID") {

    scenario("The PSU may read their own consent when the consent itself is the credential", CreateConsent, VersionOfApi, ConsentOwnership) {
      setPropsValues("consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")
      val (consentId, jwt) = acceptedConsentOfUser1()

      When("The consent is presented as the credential, so cc.user is its shadow user")
      val request = (v5_1_0_Request / "user" / "current" / "consents" / consentId).GET
      val response = makeGetRequest(
        request, List((RequestHeader.`Consent-JWT`, jwt)) ::: validHeaderConsumerKey)

      Then("The read resolves the human behind the consent and succeeds -- this used to be a 404")
      response.code should equal(200)
      (response.body \ "consent_id").extract[String] should equal(consentId)

      setPropsValues("consumer_validation_method_for_consent" -> "CONSUMER_CERTIFICATE")
    }

    scenario("Another user still cannot read a consent bound to someone else", CreateConsent, VersionOfApi, ConsentOwnership) {
      setPropsValues("consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")
      val (consentId, _) = acceptedConsentOfUser1()

      When("user2 asks for a consent that belongs to resourceUser1")
      val response = makeGetRequest((v5_1_0_Request / "user" / "current" / "consents" / consentId).GET <@ (user2))

      Then("It is refused as not found, so the id is not confirmed to a stranger")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should include(ConsentNotFound)

      setPropsValues("consumer_validation_method_for_consent" -> "CONSUMER_CERTIFICATE")
    }
  }
}
