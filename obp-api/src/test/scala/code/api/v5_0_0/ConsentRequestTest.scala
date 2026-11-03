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
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v5_0_0

import org.json4s._
import code.api.{Constant, RequestHeader}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.{APIUtil, CommonsEmailWrapper, Consent}
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{PostConsentChallengeJsonV310, PostConsentEntitlementJsonV310}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.api.v4_0_0.UsersJsonV400
import code.api.v5_0_0.Http4s500.Implementations5_0_0
import code.consent.ConsentStatus
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountRoutingJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class ConsentRequestTest extends V500ServerSetup with PropsReset{

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
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_0_0.getConsentRequest))
  
  lazy val entitlements = List(PostConsentEntitlementJsonV310("", CanGetAnyUser.toString()))
  lazy val bankId = testBankId1.value
  lazy val entitlementOneBank = List(PostConsentEntitlementJsonV310(testBankId1.value, CanCreateEntitlementAtOneBank.toString()))
  lazy val forbiddenEntitlementAnyBank = List(PostConsentEntitlementJsonV310("", CanCreateEntitlementAtAnyBank.toString()))
  lazy val accountAccess = List(AccountAccessV500(
    account_routing = AccountRoutingJsonV121(
      scheme = "AccountId",
      address = testAccountId1.value), Constant.SYSTEM_OWNER_VIEW_ID))
  lazy val postConsentRequestJson = SwaggerDefinitionsJSON.postConsentRequestJsonV500
    .copy(entitlements=Some(entitlements))
    .copy(consumer_id=Some(testConsumer.consumerId.get))
    .copy(bank_id=Some(bankId))
    .copy(account_access=accountAccess)

  val validHeaderConsumerKey = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))
  
  val createConsentRequestWithoutLoginUrl = (v5_0_0_Request / "consumer" / "consent-requests")
  val createConsentRequestUrl = (v5_0_0_Request / "consumer"/ "consent-requests").POST<@(user1)
  def getConsentRequestUrl(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId).GET<@(user1)
  def createConsentByConsentRequestIdEmail(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId/"EMAIL"/"consents").POST<@(user1)
  def createConsentByConsentRequestIdImplicit(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId/"IMPLICIT"/"consents").POST<@(user1)
  def getConsentByRequestIdUrl(requestId:String) = (v5_0_0_Request / "consumer"/ "consent-requests"/requestId/"consents").GET<@(user1)

  feature("Create/Get Consent Request v5.0.0") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      val response500 = makePostRequest(createConsentRequestWithoutLoginUrl, write(postConsentRequestJson))
      Then("We should get a 401")
      response500.code should equal(401)
      response500.body.extract[ErrorMessage].message should equal (ApplicationNotIdentified)
    }

    scenario("We will call the Create, Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.0.0")
      val createConsentResponse = makePostRequest(createConsentRequestUrl, write(postConsentRequestJson))
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

      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      val requestWhichFails = (v5_0_0_Request / "users").GET
      val responseWhichFails = makeGetRequest(requestWhichFails, List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey)
      Then("We get 401 error")
      responseWhichFails.code should equal(401)
      responseWhichFails.body.toString contains(ConsentStatusIssue) shouldBe(true)


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
      val responseGetUsers = makeGetRequest(requestGetUsers, List(consentRequestHeader) ::: validHeaderConsumerKey)
      Then("We get successful response")
      responseGetUsers.code should equal(200)
      val users = responseGetUsers.body.extract[UsersJsonV400].users
      users.size should be > 0

      // Test Request Header "Consent-Id:SOME_VALUE"
      val consentIdRequestHeader = (s"Consent-Id", getConsentByRequestResponseJson.consent_id)
      val responseGetUsersSecond = makeGetRequest(requestGetUsers, List(consentIdRequestHeader) ::: validHeaderConsumerKey)
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

    scenario("We will call the Create (IMPLICIT), Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.0.0")
      val createConsentResponse = makePostRequest(createConsentRequestUrl, write(postConsentRequestJson))
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
      val createConsentByRequestResponse = makePostRequest(createConsentByConsentRequestIdImplicit(consentRequestId), write(""))
      Then("We should get a 200")
      createConsentByRequestResponse.code should equal(201)
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      
      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
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
      val responseGetUsers = makeGetRequest(requestGetUsers, List(consentRequestHeader) ::: validHeaderConsumerKey)
      Then("We get successful response")
      responseGetUsers.code should equal(200)
      val users = responseGetUsers.body.extract[UsersJsonV400].users
      users.size should be > 0
      
      // Test Request Header "Consent-Id:SOME_VALUE"
      val consentIdRequestHeader = (s"Consent-Id", getConsentByRequestResponseJson.consent_id)
      val responseGetUsersSecond = makeGetRequest(requestGetUsers, List(consentIdRequestHeader) ::: validHeaderConsumerKey)
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

    scenario(s"Check the forbidden roles ${CanCreateEntitlementAtAnyBank.toString()}", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.0.0")
      val postJsonForbiddenEntitlementAtAnyBank = postConsentRequestJson.copy(entitlements = Some(forbiddenEntitlementAnyBank))
      val createConsentResponse = makePostRequest(createConsentRequestUrl, write(postJsonForbiddenEntitlementAtAnyBank))
      Then("We should get a 201")
      createConsentResponse.code should equal(201)
      val createConsentRequestResponseJson = createConsentResponse.body.extract[ConsentRequestResponseJson]
      val consentRequestId = createConsentRequestResponseJson.consent_request_id

      // Role CanCreateEntitlementAtAnyBank MUST be forbidden
      val forbiddenRoleResponse = makePostRequest(createConsentByConsentRequestIdEmail(consentRequestId), write(""))
      Then("We should get a 400")
      forbiddenRoleResponse.code should equal(400)
      forbiddenRoleResponse.code should equal(400)
      forbiddenRoleResponse.body.extract[ErrorMessage].message should equal (RolesForbiddenInConsent)
    }

    scenario(s"Check the role ${CanCreateEntitlementAtOneBank.toString()} is allowed in a consent", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.0.0")
      // An agent acting under a consent may grant bank roles to humans, so this role is allowed
      // (only CanCreateEntitlementAtAnyBank is forbidden). The user must already hold it.
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateEntitlementAtOneBank.toString)
      val postJsonEntitlementAtOneBank = postConsentRequestJson.copy(entitlements = Some(entitlementOneBank))
      val createConsentResponse = makePostRequest(createConsentRequestUrl, write(postJsonEntitlementAtOneBank))
      Then("We should get a 201")
      createConsentResponse.code should equal(201)
      val createConsentRequestResponseJson = createConsentResponse.body.extract[ConsentRequestResponseJson]
      val consentRequestId = createConsentRequestResponseJson.consent_request_id

      val allowedRoleResponse = makePostRequest(createConsentByConsentRequestIdEmail(consentRequestId), write(""))
      Then("We should get a 201")
      allowedRoleResponse.code should equal(201)
      // The role must actually be in the consent, not silently dropped
      val jwt = allowedRoleResponse.body.extract[ConsentJsonV500].jwt
      code.api.util.JwtUtil.getSignedPayloadAsJson(jwt).openOrThrowException("cannot read consent JWT") should include (CanCreateEntitlementAtOneBank.toString)
    }

  }

  feature("The consent challenge is sent before Create Consent By CONSENT_REQUEST_ID answers v5.0.0") {
    // The PSU needs the challenge to accept the consent. When it was sent from a Future the request did not wait
    // for, it could run after the request's database connection was closed and never be sent at all.
    def challengeMailTo(recipient: String) = CommonsEmailWrapper.testModeSentMail.filter(mail =>
      mail.to == List(recipient) && mail.subject == "OBP Consent Challenge" &&
        mail.textContent.exists(_.contains(Consent.challengeAnswerAtTestEnvironment)))

    // Returns the consent request id and the response of creating its consent.
    def createConsent(createConsentUrl: String => code.setup.OBPReq, consentRequest: PostConsentRequestJsonV500 = postConsentRequestJson) = {
      // A send that takes as long as an SMTP round trip: one the request does not wait for is still pending when
      // the response arrives.
      setPropsValues("mail.test.mode" -> "true", "mail.test.mode.delay_millis" -> "1500")
      CommonsEmailWrapper.clearTestModeSentMail()
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val createConsentRequestResponse = makePostRequest(createConsentRequestUrl, write(consentRequest))
      createConsentRequestResponse.code should equal(201)
      val consentRequestId = createConsentRequestResponse.body.extract[ConsentRequestResponseJson].consent_request_id
      (consentRequestId, makePostRequest(createConsentUrl(consentRequestId), write("")))
    }

    scenario("EMAIL: the challenge goes to the email address of the consent request", ApiEndpoint1, ApiEndpoint3, VersionOfApi) {
      When("We create a consent with an EMAIL challenge")
      val (_, response) = createConsent(createConsentByConsentRequestIdEmail)
      response.code should equal(201)
      Then("The challenge mail has already been sent, once, to the consent request's email address")
      challengeMailTo(postConsentRequestJson.email.head).size should equal(1)
    }

    scenario("IMPLICIT: the challenge goes to the email address of the user", ApiEndpoint1, ApiEndpoint3, VersionOfApi) {
      When("We create a consent with an IMPLICIT challenge")
      val (_, response) = createConsent(createConsentByConsentRequestIdImplicit)
      response.code should equal(201)
      Then("The challenge mail has already been sent, once, to the user's email address")
      challengeMailTo(resourceUser1.emailAddress).size should equal(1)
    }

    scenario("EMAIL without an email address: no consent that no one could answer", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      When("We create a consent with an EMAIL challenge from a consent request that names no email address")
      val (consentRequestId, response) = createConsent(createConsentByConsentRequestIdEmail, postConsentRequestJson.copy(email = None))
      Then("We get a 400, as from the other Create Consent endpoints, and no challenge is sent")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should include ("email")
      CommonsEmailWrapper.testModeSentMail.filter(_.subject == "OBP Consent Challenge") shouldBe empty
      And("No consent was kept for the consent request")
      makeGetRequest(getConsentByRequestIdUrl(consentRequestId)).code should not equal (200)
    }
  }

}
