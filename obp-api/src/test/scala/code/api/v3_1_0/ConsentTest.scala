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
package code.api.v3_1_0

import code.api.RequestHeader
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.{APIUtil, Consent}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_0_0.{APIMethods300, UserJsonV300}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ConsentTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createConsent))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.answerConsentChallenge))

  object VersionOfApi2 extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint3 extends Tag(nameOf(APIMethods300.Implementations3_0_0.getUserByUserId))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccount(bankId)
  lazy val entitlements = List(EntitlementJsonV400("", CanGetAnyUser.toString()))
  lazy val views = List(ViewJsonV400(bankId, bankAccount.id, "owner"))
  lazy val postConsentEmailJsonV310 = SwaggerDefinitionsJSON.postConsentEmailJsonV310
    .copy(entitlements=entitlements)
    .copy(consumer_id=None)
    .copy(views=views)

  val maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty="consents.max_time_to_live", defaultValue=3600)
  val timeToLive: Option[Long] = Some(maxTimeToLive + 10)
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access")
  {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request")
      val request400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "EMAIL" ).POST
      val response400 = makePostRequest(request400, write(postConsentEmailJsonV310))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    
    scenario("We will call the endpoint with user credentials but wrong SCA method", ApiEndpoint1, VersionOfApi) {
      When("We make a request")
      val request400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "NOT_EMAIL_NEITHER_SMS" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(postConsentEmailJsonV310))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(ConsentAllowedScaMethods)
    }
    
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint3, VersionOfApi, VersionOfApi2) {
      wholeFunctionality(RequestHeader.`Consent-JWT`)
    }

    scenario("We will call the endpoint with user credentials and deprecated header name", ApiEndpoint1, ApiEndpoint3, VersionOfApi, VersionOfApi2) {
      wholeFunctionality(RequestHeader.`Consent-Id`)
    }
  }

  private def wholeFunctionality(nameOfRequestHeader: String) = {
    When("We make a request")
    // Create a consent as the user1.
    // Must fail because we try to set time_to_live=4500
    val requestWrongTimeToLive400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "EMAIL").POST <@ (user1)
    val responseWrongTimeToLive400 = makePostRequest(requestWrongTimeToLive400, write(postConsentEmailJsonV310.copy(time_to_live = timeToLive)))
    Then("We should get a 400")
    responseWrongTimeToLive400.code should equal(400)
    responseWrongTimeToLive400.body.extract[ErrorMessage].message should include(ConsentMaxTTL)

    // Create a consent as the user1.
    // Must fail because we try to assign a role other that user already have access to the request 
    val request400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "EMAIL").POST <@ (user1)
    val response400 = makePostRequest(request400, write(postConsentEmailJsonV310))
    Then("We should get a 400")
    response400.code should equal(400)
    response400.body.extract[ErrorMessage].message should equal(RolesAllowedInConsent)

    Then("We grant the role and test it again")
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
    // Create a consent as the user1. The consent is in status INITIATED
    val secondResponse400 = makePostRequest(request400, write(postConsentEmailJsonV310))
    Then("We should get a 201")
    secondResponse400.code should equal(201)

    val consentId = secondResponse400.body.extract[ConsentJsonV310].consent_id
    val jwt = secondResponse400.body.extract[ConsentJsonV310].jwt
    val header = List((nameOfRequestHeader, jwt))

    // Make a request with the consent which is NOT in status ACCEPTED
    val requestGetUserByUserId400 = (v3_1_0_Request / "users" / "current").GET
    val responseGetUserByUserId400 = makeGetRequest(requestGetUserByUserId400, header)
    APIUtil.getPropsAsBoolValue(nameOfProperty = "consents.allowed", defaultValue = false) match {
      case true =>
        // Due to the wrong status of the consent the request must fail
        responseGetUserByUserId400.body.extract[ErrorMessage].message should include(ConsentStatusIssue)

        // Answer security challenge i.e. SCA
        val answerConsentChallengeRequest = (v3_1_0_Request / "banks" / bankId / "consents" / consentId / "challenge").POST <@ (user1)
        val challenge = Consent.challengeAnswerAtTestEnvironment
        val post = PostConsentChallengeJsonV310(answer = challenge)
        val response400 = makePostRequest(answerConsentChallengeRequest, write(post))
        Then("We should get a 201")
        response400.code should equal(201)

        // Make a request WITHOUT the request header "Consumer-Key: SOME_VALUE"
        // Due to missing value the request must fail
        makeGetRequest(requestGetUserByUserId400, header)
          .body.extract[ErrorMessage].message should include(ConsumerKeyHeaderMissing)

        // Make a request WITH the request header "Consumer-Key: NON_EXISTING_VALUE"
        // Due to non existing value the request must fail
        val headerConsumerKey = List((RequestHeader.`Consumer-Key`, "NON_EXISTING_VALUE"))
        makeGetRequest(requestGetUserByUserId400, header ::: headerConsumerKey)
          .body.extract[ErrorMessage].message should include(ConsentDoesNotMatchConsumer)

        // Make a request WITH the request header "Consumer-Key: EXISTING_VALUE"
        val validHeaderConsumerKey = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))
        val user = makeGetRequest((v3_1_0_Request / "users" / "current").GET, header ::: validHeaderConsumerKey)
          .body.extract[UserJsonV300]
        val assignedEntitlements: Seq[EntitlementJsonV400] = user.entitlements.list.flatMap(
          e => entitlements.find(_ == EntitlementJsonV400(e.bank_id, e.role_name))
        )
        // Check we have all entitlements from the consent
        assignedEntitlements should equal(entitlements)

        // Every consent implies a brand new user is created
        user.user_id should not equal (resourceUser1.userId)

        // Check we have all views from the consent
        val assignedViews = user.views.map(_.list).toSeq.flatten
        assignedViews.map(e => ViewJsonV400(e.bank_id, e.account_id, e.view_id)).distinct should equal(views)

      case false =>
        // Due to missing props at the instance the request must fail
        responseGetUserByUserId400.body.extract[ErrorMessage].message should include(ConsentDisabled)
    }
  }
}
