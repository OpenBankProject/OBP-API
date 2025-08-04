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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{ConsentJsonV310, PostConsentChallengeJsonV310, PostConsentEntitlementJsonV310}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.{PutConsentStatusJsonV400, UsersJsonV400}
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.api.v5_0_0.{AccountAccessV500, ConsentJsonV500, ConsentRequestResponseJson}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.consent.ConsentStatus
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountRoutingJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class ConsentsTest extends V510ServerSetup with PropsReset{

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createConsentRequest))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getConsentByConsentRequestId))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.createConsentByConsentRequestId))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.getConsentByConsentRequestId))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getUsers))
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_1_0.revokeConsentAtBank))
  object ApiEndpoint7 extends Tag(nameOf(Implementations5_1_0.getConsentByConsentId))
  object ApiEndpoint8 extends Tag(nameOf(Implementations5_1_0.getMyConsentsByBank))
  object getMyConsents extends Tag(nameOf(Implementations5_1_0.getMyConsents))
  object ApiEndpoint9 extends Tag(nameOf(Implementations5_1_0.getConsentsAtBank))
  object GetConsents extends Tag(nameOf(Implementations5_1_0.getConsents))
  object UpdateConsentStatusByConsent extends Tag(nameOf(Implementations5_1_0.updateConsentStatusByConsent))
  object UpdateConsentAccountAccessByConsentId extends Tag(nameOf(Implementations5_1_0.updateConsentAccountAccessByConsentId))
  object revokeMyConsent extends Tag(nameOf(Implementations5_1_0.revokeMyConsent))

  lazy val entitlements = List(PostConsentEntitlementJsonV310("", CanGetAnyUser.toString()))
  lazy val bankId = testBankId1.value
  lazy val accountAccess = List(AccountAccessV500(
    account_routing = AccountRoutingJsonV121(
      scheme = "AccountId",
      address = testAccountId1.value), Constant.SYSTEM_OWNER_VIEW_ID))
  lazy val postConsentRequestJsonV310 = SwaggerDefinitionsJSON.postConsentRequestJsonV500
    .copy(entitlements=Some(entitlements))
    .copy(consumer_id=Some(testConsumer.consumerId.get))
    .copy(bank_id=Some(bankId))
    .copy(account_access=accountAccess)

  lazy val consentStatus = PutConsentStatusJsonV400(status = "AUTHORISED")

  val validHeaderConsumerKey = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))

  val createConsentRequestWithoutLoginUrl = (v5_1_0_Request / "consumer" / "consent-requests")
  val createConsentRequestUrl = (v5_1_0_Request / "consumer"/ "consent-requests").POST<@(user1)
  def getConsentRequestUrl(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId).GET<@(user1)
  def createConsentByConsentRequestIdEmail(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId/"EMAIL"/"consents").POST<@(user1)
  def getConsentByRequestIdUrl(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId/"consents").GET<@(user1)
  def getConsentByIdUrl(requestId:String) = (v5_1_0_Request / "consumer" / "current" / "consents" / requestId ).GET<@(user1)
  def revokeConsentUrl(consentId: String) = (v5_1_0_Request / "banks" / bankId / "consents" / consentId).DELETE
  def getMyConsentAtBank(consentId: String) = (v5_1_0_Request / "banks" / bankId / "my" / "consents").GET
  def getMyConsent(consentId: String) = (v5_1_0_Request / "my" / "consents").GET
  def getConsentsAtBAnk(consentId: String) = (v5_1_0_Request / "management"/ "consents" / "banks" / bankId).GET
  def getConsents(consentId: String) = (v5_1_0_Request / "management"/ "consents").GET
  def updateConsentStatusByConsent(consentId: String) = (v5_1_0_Request / "management" / "banks" / bankId / "consents" / consentId).PUT
  def updateConsentPayloadByConsent(consentId: String) = (v5_1_0_Request / "management" / "banks" / bankId / "consents" / consentId / "account-access").PUT
  def revokeMyConsentUrl(consentId: String) = (v5_1_0_Request / "my" / "consents" / consentId ).DELETE

  feature(s"test $ApiEndpoint6 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint6, VersionOfApi) {
      When(s"We make a request $ApiEndpoint6")
      val response510 = makeDeleteRequest(revokeConsentUrl("whatever"))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint6 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint6, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response510 = makeDeleteRequest(revokeConsentUrl("whatever")<@(user1))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles + CanRevokeConsentAtBank) should be (true)
    }
  }

  feature(s"test $ApiEndpoint8 version $VersionOfApi - Unauthenticated access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint8, VersionOfApi) {
      When(s"We make a request $ApiEndpoint8")
      val response510 = makeGetRequest(getMyConsentAtBank("whatever"))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint8 version $VersionOfApi - Authenticated access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint8, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response510 = makeGetRequest(getMyConsentAtBank("whatever")<@(user1))
      Then("We should get a 200")
      response510.code should equal(200)
    }
  }

  feature(s"test $getMyConsents version $VersionOfApi - Unauthenticated access") {
    scenario("We will call the endpoint without user credentials", getMyConsents, VersionOfApi) {
      When(s"We make a request $getMyConsents")
      val response510 = makeGetRequest(getMyConsent("whatever"))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $getMyConsents version $VersionOfApi - Authenticated access") {
    scenario("We will call the endpoint with user credentials", getMyConsents, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response510 = makeGetRequest(getMyConsent("whatever")<@(user1))
      Then("We should get a 200")
      response510.code should equal(200)
    }
  }


  feature(s"test $ApiEndpoint9 version $VersionOfApi - Unauthenticated access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint9, VersionOfApi) {
      When(s"We make a request $ApiEndpoint9")
      val response510 = makeGetRequest(getConsentsAtBAnk("whatever"))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint9 version $VersionOfApi - Authenticated access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint9, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response510 = makeGetRequest(getConsentsAtBAnk("whatever") <@ (user1))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles + s"$CanGetConsentsAtOneBank or $CanGetConsentsAtAnyBank") should be(true)
    }
  }

  feature(s"test $GetConsents version $VersionOfApi - Unauthenticated access") {
    scenario("We will call the endpoint without user credentials", GetConsents, VersionOfApi) {
      When(s"We make a request $GetConsents")
      val response510 = makeGetRequest(getConsents("whatever"))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $GetConsents version $VersionOfApi - Authenticated access") {
    scenario("We will call the endpoint with user credentials", GetConsents, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response510 = makeGetRequest(getConsents("whatever") <@ (user1))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles + s"$CanGetConsentsAtAnyBank") should be(true)
    }
  }
  feature(s"test $GetConsents version $VersionOfApi - Authenticated access with proper entitlement") {
    scenario("We will call the endpoint with user credentials", GetConsents, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConsentsAtAnyBank.toString)
      val response510 = makeGetRequest(getConsents("whatever") <@ (user1))
      Then("We should get a 200")
      response510.code should equal(200)
    }
  }


  feature(s"test $UpdateConsentStatusByConsent version $VersionOfApi - Unauthenticated access") {
    scenario("We will call the endpoint without user credentials", UpdateConsentStatusByConsent, VersionOfApi) {
      When(s"We make a request $UpdateConsentStatusByConsent")
      val response510 = makePutRequest(updateConsentStatusByConsent("whatever"), write(consentStatus))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $revokeMyConsent version $VersionOfApi- Unauthenticated access") {
    scenario("We will call the endpoint with user credentials", revokeMyConsent, VersionOfApi) {
      When(s"We make a request $revokeMyConsent")
      val response510 = makeDeleteRequest(revokeMyConsentUrl("xxxx"))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  
  feature(s"test $UpdateConsentStatusByConsent version $VersionOfApi - Authenticated access") {
    scenario("We will call the endpoint with user credentials", UpdateConsentStatusByConsent, VersionOfApi) {
      When(s"We make a request $UpdateConsentStatusByConsent")
      val response510 = makePutRequest(updateConsentStatusByConsent("whatever") <@ user1, write(consentStatus))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles + s"$CanUpdateConsentStatusAtOneBank or $CanUpdateConsentStatusAtAnyBank") should be(true)
    }
  }
  feature(s"test $UpdateConsentStatusByConsent version $VersionOfApi - Authenticated access with Role $CanUpdateConsentStatusAtAnyBank") {
    scenario("We will call the endpoint with user credentials", UpdateConsentStatusByConsent, VersionOfApi) {
      When(s"We make a request $UpdateConsentStatusByConsent")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateConsentStatusAtAnyBank.toString)
      val response510 = makePutRequest(updateConsentStatusByConsent("whatever") <@ user1, write(consentStatus))
      Then("We should get a 404")
      response510.code should equal(404)
      response510.body.extract[ErrorMessage].message should startWith(ConsentNotFound)
    }
  }


  feature(s"test $UpdateConsentAccountAccessByConsentId version $VersionOfApi - Unauthenticated access") {
    scenario("We will call the endpoint without user credentials", UpdateConsentAccountAccessByConsentId, VersionOfApi) {
      When(s"We make a request $UpdateConsentAccountAccessByConsentId")
      val response510 = makePutRequest(updateConsentPayloadByConsent("whatever"), write(consentStatus))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $UpdateConsentAccountAccessByConsentId version $VersionOfApi - Authenticated access") {
    scenario("We will call the endpoint with user credentials", UpdateConsentAccountAccessByConsentId, VersionOfApi) {
      When(s"We make a request $UpdateConsentAccountAccessByConsentId")
      val response510 = makePutRequest(updateConsentPayloadByConsent("whatever") <@ user1, write(consentStatus))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles + s"$CanUpdateConsentAccountAccessAtOneBank or $CanUpdateConsentAccountAccessAtAnyBank") should be(true)
    }
  }
  feature(s"test $UpdateConsentAccountAccessByConsentId version $VersionOfApi - Authenticated access with Role $CanUpdateConsentStatusAtAnyBank") {
    scenario("We will call the endpoint with user credentials", UpdateConsentAccountAccessByConsentId, VersionOfApi) {
      When(s"We make a request $UpdateConsentAccountAccessByConsentId")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateConsentAccountAccessAtAnyBank.toString)
      val response510 = makePutRequest(updateConsentPayloadByConsent("whatever") <@ user1, write(consentStatus))
      Then("We should get a 404")
      response510.code should equal(404)
      response510.body.extract[ErrorMessage].message should startWith(ConsentNotFound)
    }
  }
  feature(s"test $revokeMyConsent version $VersionOfApi") {
    scenario("We will call the endpoint with user credentials", revokeMyConsent, VersionOfApi) {
      When(s"We make a request $revokeMyConsent")
      val response510 = makeDeleteRequest(revokeMyConsentUrl("xxxx")<@(user1))
      Then("We should get a 404")
      response510.code should equal(404)
      response510.body.extract[ErrorMessage].message should startWith(ConsentNotFound)
    }
  }
  
  feature(s"Create/Use/Revoke Consent $VersionOfApi") {
    scenario("We will call the Create, Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
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
      Then("We should get a 201")
      createConsentByRequestResponse.code should equal(201)
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      
      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      val requestWhichFails = (v5_1_0_Request / "users").GET
      val responseWhichFails = makeGetRequest(requestWhichFails, List((s"Consent-JWT", consentJwt)))
      Then("We get successful response")
      responseWhichFails.code should equal(401)
      
      
      val answerConsentChallengeRequest = (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consentId / "challenge").POST <@ (user1)
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

      When("We try to make the GET request v5.1.0")
      val getConsentById = makeGetRequest(getConsentByIdUrl(getConsentByRequestResponseJson.consent_id))
      Then("We should get a 200")
      getConsentById.code should equal(200)
      val getConsentByIdJson = getConsentById.body.extract[ConsentJsonV500]
      getConsentByIdJson.consent_request_id.head should be(consentRequestId)
      getConsentByIdJson.status should be(ConsentStatus.ACCEPTED.toString)


      val requestGetUsers = (v5_1_0_Request / "users").GET
      
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
      
      // Revoke consent
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanRevokeConsentAtBank.toString)
      val response510 = makeDeleteRequest(revokeConsentUrl(getConsentByRequestResponseJson.consent_id)<@(user1))
      Then("We should get a 200")
      response510.code should equal(200)
      
      // We cannot get all users anymore
      makeGetRequest(requestGetUsers, List(consentIdRequestHeader)).code should equal(401)
      
      {

        When(s"We try $ApiEndpoint1 v5.0.0")
        val createConsentResponse = makePostRequest(createConsentRequestUrl, write(postConsentRequestJsonV310))
        Then("We should get a 201")
        createConsentResponse.code should equal(201)
        val createConsentRequestResponseJson = createConsentResponse.body.extract[ConsentRequestResponseJson]
        val consentRequestId = createConsentRequestResponseJson.consent_request_id

        When("We try to make the GET request v5.0.0")
        Then("We grant the role and test it again")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
        val createConsentByRequestResponse = makePostRequest(createConsentByConsentRequestIdEmail(consentRequestId), write(""))
        Then("We should get a 201")
        createConsentByRequestResponse.code should equal(201)
        val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id

        
        When(s"We make a request $revokeMyConsent")
        val response510 = makeDeleteRequest(revokeMyConsentUrl(consentId)<@(user1))
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[ConsentJsonV310].status shouldBe("REVOKED")

        When("We try to make the GET request v5.0.0")
        // We cannot get all users anymore
        makeGetRequest(requestGetUsers, List(consentIdRequestHeader)).code should equal(401)
        
        
      }
    }
  }
  
}
