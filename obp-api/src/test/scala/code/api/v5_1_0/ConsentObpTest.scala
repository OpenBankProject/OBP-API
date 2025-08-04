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

import code.api.{Constant, RequestHeader}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, Consent}
import code.api.util.APIUtil.OAuth._
import code.api.v3_0_0.{APIMethods300, UserJsonV300}
import code.api.v3_1_0.{ConsentJsonV310, PostConsentChallengeJsonV310, PostConsentEntitlementJsonV310, PostConsentViewJsonV310}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ConsentObpTest extends V510ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object CreateConsent extends Tag(nameOf(Implementations5_1_0.createConsent))
  object AnswerConsentChallenge extends Tag(nameOf(Implementations3_1_0.answerConsentChallenge))

  object VersionOfApi2 extends Tag(ApiVersion.v3_0_0.toString)
  object GetUserByUserId extends Tag(nameOf(APIMethods300.Implementations3_0_0.getUserByUserId))

  val validHeaderConsumerKey = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccount(bankId)
  lazy val entitlements = List(PostConsentEntitlementJsonV310("", CanGetAnyUser.toString()))
  lazy val views = List(PostConsentViewJsonV310(bankId, bankAccount.id, Constant.SYSTEM_OWNER_VIEW_ID))
  lazy val postConsentEmailJsonV310 = SwaggerDefinitionsJSON.postConsentEmailJsonV310
    .copy(entitlements=entitlements)
    .copy(consumer_id=Some(testConsumer.consumerId.get))
    .copy(views=views)
  lazy val postConsentImplicitJsonV310 = SwaggerDefinitionsJSON.postConsentImplicitJsonV310
    .copy(entitlements=entitlements)
    .copy(consumer_id=Some(testConsumer.consumerId.get))
    .copy(views=views)

  val maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty="consents.max_time_to_live", defaultValue=3600)
  val timeToLive: Option[Long] = Some(maxTimeToLive + 10)
  
  feature(s"test $CreateConsent version $VersionOfApi - Unauthorized access")
  {
    scenario("We will call the endpoint without user credentials-IMPLICIT", CreateConsent, VersionOfApi) {
      When("We make a request")
      val request = (v5_1_0_Request / "my" / "consents" / "IMPLICIT" ).POST
      val response = makePostRequest(request, write(postConsentImplicitJsonV310))
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint with user credentials-Implicit", CreateConsent, GetUserByUserId, VersionOfApi, VersionOfApi2) {
      setPropsValues("consumer_validation_method_for_consent"-> "CONSUMER_KEY_VALUE")
      wholeFunctionalityImplicit(RequestHeader.`Consent-JWT`)
      setPropsValues("consumer_validation_method_for_consent"-> "CONSUMER_CERTIFICATE")
    }

    scenario("We will call the endpoint with user credentials and deprecated header name-Implicit", CreateConsent, GetUserByUserId, VersionOfApi, VersionOfApi2) {
      setPropsValues("consumer_validation_method_for_consent"-> "CONSUMER_KEY_VALUE")
      wholeFunctionalityImplicit(RequestHeader.`Consent-Id`)
      setPropsValues("consumer_validation_method_for_consent"-> "CONSUMER_CERTIFICATE")
    }
  }

  private def wholeFunctionalityImplicit(nameOfRequestHeader: String) = {
    When("We make a request")
    // Create a consent as the user1.
    // Must fail because we try to set time_to_live=4500
    val requestWrongTimeToLive = (v5_1_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1)
    val responseWrongTimeToLive = makePostRequest(requestWrongTimeToLive, write(postConsentImplicitJsonV310.copy(time_to_live = timeToLive)))
    Then("We should get a 400")
    responseWrongTimeToLive.code should equal(400)
    responseWrongTimeToLive.body.extract[ErrorMessage].message should include(ConsentMaxTTL)

    // Create a consent as the user1.
    // Must fail because we try to assign a role other that user already have access to the request 
    val request = (v5_1_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1)
    val response = makePostRequest(request, write(postConsentImplicitJsonV310), validHeaderConsumerKey)
    Then("We should get a 400")
    response.code should equal(400)
    response.body.extract[ErrorMessage].message should equal(RolesAllowedInConsent)

    Then("We grant the role and test it again")
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
    // Create a consent as the user1. The consent is in status INITIATED
    val secondResponse = makePostRequest(request, write(postConsentImplicitJsonV310), validHeaderConsumerKey)
    Then("We should get a 201")
    secondResponse.code should equal(201)

    val consentId = secondResponse.body.extract[ConsentJsonV310].consent_id
    val jwt = secondResponse.body.extract[ConsentJsonV310].jwt
    val header = List((nameOfRequestHeader, jwt))

    // Make a request with the consent which is NOT in status ACCEPTED
    val requestGetUserByUserId = (v5_1_0_Request / "users" / "current").GET
    val responseGetUserByUserId = makeGetRequest(requestGetUserByUserId, header ::: validHeaderConsumerKey)
    APIUtil.getPropsAsBoolValue(nameOfProperty = "consents.allowed", defaultValue = false) match {
      case true =>
        // Due to the wrong status of the consent the request must fail
        responseGetUserByUserId.body.extract[ErrorMessage].message should include(ConsentStatusIssue)

        // Answer security challenge i.e. SCA
        val answerConsentChallengeRequest = (v5_1_0_Request / "banks" / bankId / "consents" / consentId / "challenge").POST <@ (user1)
        val challenge = Consent.challengeAnswerAtTestEnvironment
        val post = PostConsentChallengeJsonV310(answer = challenge)
        val response = makePostRequest(answerConsentChallengeRequest, write(post))
        Then("We should get a 201")
        response.code should equal(201)

        // Make a request WITHOUT the request header "Consumer-Key: SOME_VALUE"
        // Due to missing value the request must fail
        makeGetRequest(requestGetUserByUserId, header)
          .body.extract[ErrorMessage].message should include(ConsentNotFound)

        // Make a request WITH the request header "Consumer-Key: NON_EXISTING_VALUE"
        // Due to non existing value the request must fail
        val headerConsumerKey = List((RequestHeader.`Consumer-Key`, "NON_EXISTING_VALUE"))
        makeGetRequest(requestGetUserByUserId, header ::: headerConsumerKey)
          .body.extract[ErrorMessage].message should include(ConsentNotFound)

        // Make a request WITH the request header "Consumer-Key: EXISTING_VALUE"
        val response2 = makeGetRequest((v5_1_0_Request / "users" / "current").GET, header ::: validHeaderConsumerKey)
        val user =   response2.body.extract[UserJsonV300]
        val assignedEntitlements: Seq[PostConsentEntitlementJsonV310] = user.entitlements.list.flatMap(
          e => entitlements.find(_ == PostConsentEntitlementJsonV310(e.bank_id, e.role_name))
        )
        // Check we have all entitlements from the consent
        assignedEntitlements should equal(entitlements)

        // Every consent implies a brand new user is created
        user.user_id should not equal (resourceUser1.userId)

        // Check we have all views from the consent
        val assignedViews = user.views.map(_.list).toSeq.flatten
        assignedViews.map(e => PostConsentViewJsonV310(e.bank_id, e.account_id, e.view_id)).distinct should equal(views)

      case false =>
        // Due to missing props at the instance the request must fail
        responseGetUserByUserId.body.extract[ErrorMessage].message should include(ConsentDisabled)
    }
  }
}
