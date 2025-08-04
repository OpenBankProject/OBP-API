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

import code.api.RequestHeader
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{accountRoutingJsonV121, bankRoutingJsonV121, branchRoutingJsonV141, postCounterpartyLimitV510}
import code.api.v5_0_0.ConsentJsonV500
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue.counterpartyNameExample
import code.api.v2_1_0.{CounterpartyIdJson, TransactionRequestBodyCounterpartyJSON}
import code.api.v3_0_0.CoreAccountsJsonV300
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.PostConsentChallengeJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.{TransactionRequestWithChargeJSON400, UsersJsonV400}
import code.api.v5_0_0.ConsentRequestResponseJson
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.consent.ConsentStatus
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage}
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
  object ApiEndpoint5 extends Tag(nameOf(Implementations3_0_0.corePrivateAccountsAllBanks))
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_0_0.getConsentRequest))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.createTransactionRequestCounterparty))


  val validHeaderConsumerKey = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))

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

  val postCounterpartyLimitTestMonthly = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "10", // if I transfer 11 euros, then we trigger this guard.
    max_monthly_amount = "11", // if I transfer 10, then transfer 20, --> we trigger this guard.
    max_number_of_monthly_transactions = 2, //if I transfer 10, then transfer 2, then transfer 3 --> we can trigger this guard. 
    max_yearly_amount = "30", //
    max_number_of_yearly_transactions = 5,
    max_total_amount = "50", //
    max_number_of_transactions = 6
  )

  val postCounterpartyLimitTestYearly = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "100", 
    max_monthly_amount = "200", 
    max_number_of_monthly_transactions = 20, 
    max_yearly_amount = "11", //if I transfer 11 euros, then we trigger this guard.
    max_number_of_yearly_transactions = 2,//if I transfer 1, then transfer 2, then transfer 3 --> we can trigger this guard. 
    max_total_amount = "50", 
    max_number_of_transactions = 20
  )
  
  val postCounterpartyLimitTestTotal = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "100", 
    max_monthly_amount = "200", 
    max_number_of_monthly_transactions = 20, 
    max_yearly_amount = "100",
    max_number_of_yearly_transactions = 20,
    max_total_amount = "11",      //if I transfer 5 euros, then transfer 10  --> we can trigger this guard.
    max_number_of_transactions = 2//if I transfer 1, then transfer 2, then transfer 3 --> we can trigger this guard. 
  )
  
  val toAccountJson = ConsentRequestToAccountJson (
    counterparty_name = counterpartyNameExample.value,
    bank_routing = bankRoutingJsonV121.copy(address = testBankId1.value),
    account_routing = accountRoutingJsonV121.copy(address = testAccountId1.value),
    branch_routing = branchRoutingJsonV141,
    limit = postCounterpartyLimitTestMonthly
  )
  lazy val postVRPConsentRequestMonthlyGuardJson = SwaggerDefinitionsJSON.postVRPConsentRequestJsonV510.copy(
    from_account=fromAccountJson,
    to_account=toAccountJson.copy(limit= postCounterpartyLimitTestMonthly)
  )
  lazy val postVRPConsentRequestYearlyGuardJson = SwaggerDefinitionsJSON.postVRPConsentRequestJsonV510.copy(
    from_account=fromAccountJson,
    to_account=toAccountJson.copy(limit= postCounterpartyLimitTestYearly)
  )
  lazy val postVRPConsentRequestTotalGuardJson = SwaggerDefinitionsJSON.postVRPConsentRequestJsonV510.copy(
    from_account=fromAccountJson,
    to_account=toAccountJson.copy(limit= postCounterpartyLimitTestTotal)
  )


  feature("Create/Get Consent Request v5.1.0") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val response510 = makePostRequest(createVRPConsentRequestWithoutLoginUrl, write(postVRPConsentRequestMonthlyGuardJson))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal (ApplicationNotIdentified)
    }

    scenario("We will call the Create, Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postVRPConsentRequestMonthlyGuardJson))
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
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      val accountAccess = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access
      accountAccess.isDefined should equal(true)
      accountAccess.get.bank_id should equal(fromAccountJson.bank_routing.address)
      accountAccess.get.account_id should equal(fromAccountJson.account_routing.address)
      accountAccess.get.view_id contains("_vrp-") shouldBe( true)
      
      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      val requestWhichFails = (v5_1_0_Request / "my"/ "accounts").GET
      val responseWhichFails = makeGetRequest(requestWhichFails, List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey)
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
      val getConsentByRequestResponseJson = getConsentByRequestResponse.body.extract[ConsentJsonV500]
      getConsentByRequestResponseJson.consent_request_id.head should be(consentRequestId)
      getConsentByRequestResponseJson.status should be(ConsentStatus.ACCEPTED.toString)


      val requestGetMyAccounts = (v5_1_0_Request / "my"/ "accounts").GET
      val responseGetMyAccounts = makeGetRequest(requestGetMyAccounts, List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey)
      Then("We get 200 and proper response")
      responseGetMyAccounts.code should equal(200)
      responseGetMyAccounts.body.extract[CoreAccountsJsonV300].accounts.length > 0 shouldBe(true)

      Then("We can test the COUNTERPARTY payment limit")

      val consentRequestBankId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.bank_id
      val consentRequestAccountId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.account_id
      val consentRequestViewId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.view_id
      val consentRequestCounterpartyId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.helper_info.head.counterparty_ids
      
      val createTransReqRequest = (v4_0_0_Request / "banks" / consentRequestBankId / "accounts" / consentRequestAccountId /
        consentRequestViewId / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST
      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(consentRequestCounterpartyId.head),
        value = AmountOfMoneyJsonV121("EUR","1"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None,
      )
      val response = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty), List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey)
      response.code shouldBe(201)
      response.body.extract[TransactionRequestWithChargeJSON400].status shouldBe("COMPLETED")
    }
  
    scenario("We will call the Create (IMPLICIT), Get and Delete endpoints with user credentials ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postVRPConsentRequestMonthlyGuardJson))
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
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      val accountAccess = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access
      accountAccess.isDefined should equal(true)
      accountAccess.get.bank_id should equal(fromAccountJson.bank_routing.address)
      accountAccess.get.account_id should equal(fromAccountJson.account_routing.address)
      accountAccess.get.view_id contains("_vrp-") shouldBe( true)


      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      val requestWhichFails = (v5_1_0_Request / "my"/ "accounts").GET
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
      val getConsentByRequestResponseJson = getConsentByRequestResponse.body.extract[ConsentJsonV500]
      getConsentByRequestResponseJson.consent_request_id.head should be(consentRequestId)
      getConsentByRequestResponseJson.status should be(ConsentStatus.ACCEPTED.toString)
    }

    scenario("We will create consent properly, and test the counterparty limit - monthly guard", ApiEndpoint1, ApiEndpoint3, ApiEndpoint7, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postVRPConsentRequestMonthlyGuardJson))
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
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      val accountAccess = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access
      accountAccess.isDefined should equal(true)
      accountAccess.get.bank_id should equal(fromAccountJson.bank_routing.address)
      accountAccess.get.account_id should equal(fromAccountJson.account_routing.address)
      accountAccess.get.view_id contains("_vrp-") shouldBe( true)

      val answerConsentChallengeRequest = (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consentId / "challenge").POST <@ (user1)
      val challenge = Consent.challengeAnswerAtTestEnvironment
      val post = PostConsentChallengeJsonV310(answer = challenge)
      val answerConsentChallengeResponse = makePostRequest(answerConsentChallengeRequest, write(post))
      Then("We should get a 201")
      answerConsentChallengeResponse.code should equal(201)
      
      Then("We can test the COUNTERPARTY payment limit")

      val consentRequestBankId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.bank_id
      val consentRequestAccountId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.account_id
      val consentRequestViewId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.view_id
      val consentRequestCounterpartyId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.helper_info.head.counterparty_ids

      val createTransReqRequest = (v4_0_0_Request / "banks" / consentRequestBankId / "accounts" / consentRequestAccountId /
        consentRequestViewId / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST
      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(consentRequestCounterpartyId.head),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None,
      )
      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      val response = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty), List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey)
      response.code shouldBe(400)
      response.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response.body.extract[ErrorMessage].message contains("max_single_amount") shouldBe(true)
      
      //("we try the max_monthly_amount limit (11 euros) . now we transfer 9 euro first. then 9 euros, we will get the error")
      val response1 = makePostRequest(
        createTransReqRequest, 
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","3"))), 
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response1.code shouldBe(201)
      val response2 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","9"))),
        List((s"Consent-JWT", consentJwt) ) ::: validHeaderConsumerKey
      )

      response2.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response2.body.extract[ErrorMessage].message contains("max_monthly_amount") shouldBe(true)
      
      //("we try the max_number_of_monthly_transactions limit (2 times), we try the 3rd request, we will get the error. response2 failed, so does not count in database.")
      val response3 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response3.code shouldBe(201)
      
      val response4 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response4.code shouldBe(400)

      response4.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response4.body.extract[ErrorMessage].message contains("max_number_of_monthly_transactions") shouldBe(true)

    }

    scenario("We will create consent properly, and test the counterparty limit - yearly guard", ApiEndpoint1, ApiEndpoint3, ApiEndpoint7, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postVRPConsentRequestYearlyGuardJson))
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
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      val accountAccess = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access
      accountAccess.isDefined should equal(true)
      accountAccess.get.bank_id should equal(fromAccountJson.bank_routing.address)
      accountAccess.get.account_id should equal(fromAccountJson.account_routing.address)
      accountAccess.get.view_id contains("_vrp-") shouldBe( true)

      val answerConsentChallengeRequest = (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consentId / "challenge").POST <@ (user1)
      val challenge = Consent.challengeAnswerAtTestEnvironment
      val post = PostConsentChallengeJsonV310(answer = challenge)
      val answerConsentChallengeResponse = makePostRequest(answerConsentChallengeRequest, write(post))
      Then("We should get a 201")
      answerConsentChallengeResponse.code should equal(201)

      Then("We can test the COUNTERPARTY payment limit")

      val consentRequestBankId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.bank_id
      val consentRequestAccountId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.account_id
      val consentRequestViewId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.view_id
      val consentRequestCounterpartyId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.helper_info.head.counterparty_ids

      val createTransReqRequest = (v4_0_0_Request / "banks" / consentRequestBankId / "accounts" / consentRequestAccountId /
        consentRequestViewId / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST
      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(consentRequestCounterpartyId.head),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None,
      )
      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      val response1 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","3"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response1.code shouldBe(201)
      val response2 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","9"))),
       List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response2.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response2.body.extract[ErrorMessage].message contains("max_yearly_amount") shouldBe(true)

      //("we try the max_number_of_monthly_transactions limit (2 times), we try the 3rd request, we will get the error. response2 failed, so does not count in database.")
      val response3 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response3.code shouldBe(201)

      val response4 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response4.code shouldBe(400)

      response4.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response4.body.extract[ErrorMessage].message contains("max_number_of_yearly_transactions") shouldBe(true)

    }

    scenario("We will create consent properly, and test the counterparty limit - total guard", ApiEndpoint1, ApiEndpoint3, ApiEndpoint7, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val createConsentResponse = makePostRequest(createVRPConsentRequestUrl, write(postVRPConsentRequestTotalGuardJson))
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
      val consentId = createConsentByRequestResponse.body.extract[ConsentJsonV500].consent_id
      val consentJwt = createConsentByRequestResponse.body.extract[ConsentJsonV500].jwt
      val accountAccess = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access
      accountAccess.isDefined should equal(true)
      accountAccess.get.bank_id should equal(fromAccountJson.bank_routing.address)
      accountAccess.get.account_id should equal(fromAccountJson.account_routing.address)
      accountAccess.get.view_id contains("_vrp-") shouldBe( true)

      val answerConsentChallengeRequest = (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consentId / "challenge").POST <@ (user1)
      val challenge = Consent.challengeAnswerAtTestEnvironment
      val post = PostConsentChallengeJsonV310(answer = challenge)
      val answerConsentChallengeResponse = makePostRequest(answerConsentChallengeRequest, write(post))
      Then("We should get a 201")
      answerConsentChallengeResponse.code should equal(201)

      Then("We can test the COUNTERPARTY payment limit")

      val consentRequestBankId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.bank_id
      val consentRequestAccountId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.account_id
      val consentRequestViewId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.view_id
      val consentRequestCounterpartyId = createConsentByRequestResponse.body.extract[ConsentJsonV500].account_access.get.helper_info.head.counterparty_ids

      val createTransReqRequest = (v4_0_0_Request / "banks" / consentRequestBankId / "accounts" / consentRequestAccountId /
        consentRequestViewId / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST
      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(consentRequestCounterpartyId.head),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None
      )
      setPropsValues("consumer_validation_method_for_consent"->"CONSUMER_KEY_VALUE")
      //("we try the max_monthly_amount limit (11 euros) . now we transfer 9 euro first. then 9 euros, we will get the error")
      val response1 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","3"))),
        List( (s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response1.code shouldBe(201)
      val response2 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","9"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )

      response2.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response2.body.extract[ErrorMessage].message contains("max_total_amount") shouldBe(true)

      //("we try the max_number_of_monthly_transactions limit (2 times), we try the 3rd request, we will get the error. response2 failed, so does not count in database.")
      val response3 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response3.code shouldBe(201)

      val response4 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2"))),
        List((s"Consent-JWT", consentJwt)) ::: validHeaderConsumerKey
      )
      response4.code shouldBe(400)

      response4.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response4.body.extract[ErrorMessage].message contains("max_number_of_transactions") shouldBe(true)

    }

    
  }

}
