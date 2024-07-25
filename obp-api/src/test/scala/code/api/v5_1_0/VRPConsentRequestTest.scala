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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{accountRoutingJsonV121, bankRoutingJsonV121, branchRoutingJsonV141, postCounterpartyLimitV510}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue.counterpartyNameExample
import code.api.v3_1_0.PostConsentChallengeJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.UsersJsonV400
import code.api.v5_0_0.ConsentRequestResponseJson
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.consent.ConsentStatus
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class VRPConsentRequestTest extends V510ServerSetup with PropsReset{

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createVRPConsentRequest))
  
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getConsentByConsentRequestId))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.createConsentByConsentRequestId))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.getConsentByConsentRequestId))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getUsers))
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_0_0.getConsentRequest))




  val createVRPConsentRequestWithoutLoginUrl = (v5_1_0_Request / "consumer" / "vrp-consent-requests")
  val createVRPConsentRequestUrl = (v5_1_0_Request / "consumer"/ "vrp-consent-requests").POST<@(user1)
  def getConsentRequestUrl(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId).GET<@(user1)
  def createConsentByConsentRequestIdEmail(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId/"EMAIL"/"consents").POST<@(user1)
  def createConsentByConsentRequestIdImplicit(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId/"IMPLICIT"/"consents").POST<@(user1)
  def getConsentByRequestIdUrl(requestId:String) = (v5_1_0_Request / "consumer"/ "consent-requests"/requestId/"consents").GET<@(user1)

  val fromAccountJson = ConsentRequestFromAccountJson (
    bank_routing = bankRoutingJsonV121.copy(address = testBankId1.value),
    account_routing = accountRoutingJsonV121.copy(address = testAccountId0.value),
    branch_routing = branchRoutingJsonV141
  )

  val toAccountJson = ConsentRequestToAccountJson (
    counterparty_name = counterpartyNameExample.value,
    bank_routing = bankRoutingJsonV121.copy(address = testBankId1.value),
    account_routing = accountRoutingJsonV121.copy(address = testAccountId1.value),
    branch_routing = branchRoutingJsonV141,
    limit = postCounterpartyLimitV510
  )
  lazy val postConsentRequestJson = SwaggerDefinitionsJSON.postConsentRequestJsonV510.copy(
    from_account=fromAccountJson,
    to_account=toAccountJson
  )
  
  
  feature("Create/Get Consent Request v5.1.0") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val response510 = makePostRequest(createVRPConsentRequestWithoutLoginUrl, write(postConsentRequestJson))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal (ApplicationNotIdentified)
    }

    scenario("We will call the Create, Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postConsentRequestJson))
      Then("We should get a 201")
      createConsentResponse.code should equal(201)
      val createConsentRequestResponseJson = createConsentResponse.body.extract[ConsentRequestResponseJson]
      val consentRequestId = createConsentRequestResponseJson.consent_request_id

      When("We try to make the GET request v5.1.0")
      val successGetRes = makeGetRequest(getConsentRequestUrl(consentRequestId))
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val getConsentRequestResponseJson = successGetRes.body.extract[ConsentRequestResponseJson]
      getConsentRequestResponseJson.payload should not be("")

      When("We try to make the GET request v5.1.0")
      Then("We grant the role and test it again")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val createConsentByRequestResponse = makePostRequest(createConsentByConsentRequestIdEmail(consentRequestId), write(""))
      Then("We should get a 200")
      createConsentByRequestResponse.code should equal(201)
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV510].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV510].jwt

      setPropsValues("consumer_validation_method_for_consent"->"NONE")
      val requestWhichFails = (v5_1_0_Request / "users").GET
      val responseWhichFails = makeGetRequest(requestWhichFails, List((s"Consent-JWT", consentJwt)))
      Then("We get 401 error")
      responseWhichFails.code should equal(401)
      responseWhichFails.body.toString contains(ConsentStatusIssue) shouldBe(true)


      val answerConsentChallengeRequest = (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consentId / "challenge").POST <@ (user1)
      val challenge = Consent.challengeAnswerAtTestEnvironment
      val post = PostConsentChallengeJsonV310(answer = challenge)
      val answerConsentChallengeResponse = makePostRequest(answerConsentChallengeRequest, write(post))
      Then("We should get a 201")
      answerConsentChallengeResponse.code should equal(201)

      When("We try to make the GET request v5.1.0")
      val getConsentByRequestResponse = makeGetRequest(getConsentByRequestIdUrl(consentRequestId))
      Then("We should get a 200")
      getConsentByRequestResponse.code should equal(200)
      val getConsentByRequestResponseJson = getConsentByRequestResponse.body.extract[ConsentJsonV510]
      getConsentByRequestResponseJson.consent_request_id.head should be(consentRequestId)
      getConsentByRequestResponseJson.status should be(ConsentStatus.ACCEPTED.toString)


      val requestGetUsers = (v5_1_0_Request / "users").GET

    }

    scenario("We will call the Create (IMPLICIT), Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postConsentRequestJson))
      Then("We should get a 201")
      createConsentResponse.code should equal(201)
      val createConsentRequestResponseJson = createConsentResponse.body.extract[ConsentRequestResponseJson]
      val consentRequestId = createConsentRequestResponseJson.consent_request_id

      When("We try to make the GET request v5.1.0")
      val successGetRes = makeGetRequest(getConsentRequestUrl(consentRequestId))
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val getConsentRequestResponseJson = successGetRes.body.extract[ConsentRequestResponseJson]
      getConsentRequestResponseJson.payload should not be("")

      When("We try to make the GET request v5.1.0")
      Then("We grant the role and test it again")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val createConsentByRequestResponse = makePostRequest(createConsentByConsentRequestIdImplicit(consentRequestId), write(""))
      Then("We should get a 200")
      createConsentByRequestResponse.code should equal(201)
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV510].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV510].jwt

      setPropsValues("consumer_validation_method_for_consent"->"NONE")
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

      When("We try to make the GET request v5.1.0")
      val getConsentByRequestResponse = makeGetRequest(getConsentByRequestIdUrl(consentRequestId))
      Then("We should get a 200")
      getConsentByRequestResponse.code should equal(200)
      val getConsentByRequestResponseJson = getConsentByRequestResponse.body.extract[ConsentJsonV510]
      getConsentByRequestResponseJson.consent_request_id.head should be(consentRequestId)
      getConsentByRequestResponseJson.status should be(ConsentStatus.ACCEPTED.toString)
    }


  }

}
