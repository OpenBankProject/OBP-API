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
package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{PostConsentChallengeJsonV310, PostConsentEntitlementJsonV310}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.UsersJsonV400
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.consent.ConsentStatus
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountRoutingJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class ConsentRequestTest extends V500ServerSetupAsync with PropsReset{

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createConsentRequest))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getConsentByConsentRequestId))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.createConsentByConsentRequestId))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.getConsentByConsentRequestId))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getUsers))
  
  lazy val entitlements = List(PostConsentEntitlementJsonV310("", CanGetAnyUser.toString()))
  lazy val accountAccess = List(AccountAccessV500(
    account_routing = AccountRoutingJsonV121(
      scheme = "AccountId",
      address = testAccountId1.value), "owner"))
  lazy val postConsentRequestJsonV310 = SwaggerDefinitionsJSON.postConsentRequestJsonV500
    .copy(entitlements=Some(entitlements))
    .copy(consumer_id=None)
    .copy(account_access=accountAccess)
  
  val createConsentRequestWithoutLoginUrl = (v5_0_0_Request / "consumer" / "consent-requests")
  val createConsentRequestUrl = (v5_0_0_Request / "consumer"/ "consent-requests").POST<@(user1)
  def getConsentRequestUrl(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId).GET<@(user1)
  def createConsentByConsentRequestIdEmail(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId/"EMAIL"/"consents").POST<@(user1)
  def getConsentByRequestIdUrl(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId/"consents").GET<@(user1)

  feature("Create/Get Consent Request v5.0.0") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      val response500 = makePostRequest(createConsentRequestWithoutLoginUrl, write(postConsentRequestJsonV310))
      Then("We should get a 401")
      response500.code should equal(401)
      response500.body.extract[ErrorMessage].message should equal (ApplicationNotIdentified)
    }

    scenario("We will call the Create, Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.0.0")
      val createConsentResponse = makePostRequest(createConsentRequestUrl, write(postConsentRequestJsonV310))
      Then("We should get a 201")
      createConsentResponse.code should equal(201)
      val createConsentRequestResponseJson = createConsentResponse.body.extract[ConsentRequestResponseJson]
      val consentRequestId = createConsentRequestResponseJson.consent_request_id

      When("We try to make the GET request v5.0.0")
      val successGetRes = makeGetRequest(getConsentRequestUrl(consentRequestId))
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val getConsentRequestResponseJson = successGetRes.body.extract[ConsentRequestResponseJson]
      getConsentRequestResponseJson.payload should not be("")
      
      When("We try to make the GET request v5.0.0")
      Then("We grant the role and test it again")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val createConsentByRequestResponse = makePostRequest(createConsentByConsentRequestIdEmail(consentRequestId), write(""))
      Then("We should get a 200")
      createConsentByRequestResponse.code should equal(201)
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      
      setPropsValues("consumer_validation_method_for_consent"->"NONE")
      val requestWhichFails = (v5_0_0_Request / "users").GET
      val responseWhichFails = makeGetRequest(requestWhichFails, List((s"Consent-JWT", consentJwt)))
      Then("We get successful response")
      responseWhichFails.code should equal(401)
      
      
      val answerConsentChallengeRequest = (v5_0_0_Request / "banks" / testBankId1.value / "consents" / consentId / "challenge").POST <@ (user1)
      val challenge = Consent.challengeAnswerAtTestEnvironment
      val post = PostConsentChallengeJsonV310(answer = challenge)
      val answerConsentChallengeResponse = makePostRequest(answerConsentChallengeRequest, write(post))
      Then("We should get a 201")
      answerConsentChallengeResponse.code should equal(201)
      
      When("We try to make the GET request v5.0.0")
      val getConsentByRequestResponse = makeGetRequest(getConsentByRequestIdUrl(consentRequestId))
      Then("We should get a 200")
      getConsentByRequestResponse.code should equal(200)
      val getConsentByRequestResponseJson = getConsentByRequestResponse.body.extract[ConsentJsonV500]
      getConsentByRequestResponseJson.consent_request_id.head should be(consentRequestId)
      getConsentByRequestResponseJson.status should be(ConsentStatus.ACCEPTED.toString)


      val requestGetUsers = (v5_0_0_Request / "users").GET
      
      // Test Request Header "Consent-JWT:SOME_VALUE"
      val consentRequestHeader = (s"Consent-JWT", getConsentByRequestResponseJson.jwt)
      val responseGetUsers = makeGetRequest(requestGetUsers, List(consentRequestHeader))
      Then("We get successful response")
      responseGetUsers.code should equal(200)
      val users = responseGetUsers.body.extract[UsersJsonV400].users
      users.size should be > 0
      
      // Test Request Header "Consent-Id:SOME_VALUE"
      val consentIdRequestHeader = (s"Consent-Id", getConsentByRequestResponseJson.consent_id)
      val responseGetUsersSecond = makeGetRequest(requestGetUsers, List(consentIdRequestHeader))
      Then("We get successful response")
      responseGetUsersSecond.code should equal(200)
      val usersSecond = responseGetUsersSecond.body.extract[UsersJsonV400].users
      usersSecond.size should be > 0
      users.size should equal(usersSecond.size)
      
      // Test Request Header "Consent-JWT:INVALID_JWT_VALUE"
      val wrongRequestHeader = (s"Consent-JWT", "INVALID_JWT_VALUE")
      val responseGetUsersWrong = makeGetRequest(requestGetUsers, List(wrongRequestHeader))
      Then("We get successful response")
      responseGetUsersWrong.code should equal(401)
      responseGetUsersWrong.body.extract[ErrorMessage].message contains (ConsentHeaderValueInvalid) should be (true)
    }

  }
  
}
