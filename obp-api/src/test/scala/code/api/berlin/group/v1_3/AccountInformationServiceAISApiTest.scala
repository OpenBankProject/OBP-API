package code.api.berlin.group.v1_3

import com.openbankproject.commons.model.ErrorMessage
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AccountInformationServiceAISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object getAccountList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getAccountList))
  
  object readAccountDetails extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.readAccountDetails))

  object getBalances extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getBalances))

  object getTransactionList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getTransactionList))

  object getCardAccountTransactionList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getCardAccountTransactionList))

  object createConsent extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.createConsent))

  object deleteConsent extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.deleteConsent))

  object getConsentInformation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentInformation))

  object getConsentStatus extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentStatus))
  
  object startConsentAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.startConsentAuthorisation))

  object getConsentAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentAuthorisation))
  
  object getConsentScaStatus extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentScaStatus))
  
  object updateConsentsPsuData extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.updateConsentsPsuData))

  
  feature(s"BG v1.3 - $getAccountList") {
    scenario("Not Authentication User, test failed ", BerlinGroupV1_3, getAccountList) {
      val requestGet = (V1_3_BG / "accounts").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should startWith(UserNotLoggedIn)
    }

    scenario("Authentication User, test succeed", BerlinGroupV1_3, getAccountList) {
      val requestGet = (V1_3_BG / "accounts").GET <@ (user1)
      val response = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[CoreAccountsJsonV13].accounts.length > 1 should be (true)
    }
  }
  
  feature(s"BG v1.3 - $readAccountDetails") {
    scenario("Not Authentication User, test failed ", BerlinGroupV1_3, readAccountDetails) {
      val requestGet = (V1_3_BG / "accounts" / "accountId").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should startWith(UserNotLoggedIn)
    }

    scenario("Authentication User, test succeed", BerlinGroupV1_3, readAccountDetails) {
      val requestGetAccounts = (V1_3_BG / "accounts").GET <@ (user1)
      val responseGetAccounts = makeGetRequest(requestGetAccounts)
      val accountId = responseGetAccounts.body.extract[CoreAccountsJsonV13].accounts.map(_.resourceId).headOption.getOrElse("")
      
      val requestGet = (V1_3_BG / "accounts" / accountId).GET <@ (user1)
      val response = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[AccountDetailsJsonV13].account.resourceId should be (accountId)
    }
  }

  feature(s"BG v1.3 - $getBalances") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getBalances) {
      val testBankId = testAccountId1
      val requestGet = (V1_3_BG / "accounts" /testBankId.value/ "balances").GET <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[AccountBalancesV13].`balances`.length > 0 should be (true)
      response.body.extract[AccountBalancesV13].account.iban should not be ("")
    }
  }  

  feature(s"BG v1.3 - $getTransactionList") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getTransactionList) {
      val testBankId = testAccountId1
      val requestGet = (V1_3_BG / "accounts" /testBankId.value/ "transactions").GET <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response.body.extract[TransactionsJsonV13].transactions.booked.length >0 should be (true)
      response.body.extract[TransactionsJsonV13].transactions.pending.length >0 should be (true)
    }
  }

  feature(s"BG v1.3 - $getCardAccountTransactionList") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getCardAccountTransactionList) {
      val testBankId = testAccountId1
      val requestGet = (V1_3_BG / "card-accounts" /testBankId.value/ "transactions").GET <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[CardTransactionsJsonV13].cardAccount.maskedPan.length >0 should be (true)
      response.body.extract[CardTransactionsJsonV13].transactions.booked.length >0 should be (true)
    }
  }

  feature(s"BG v1.3 - $createConsent") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val testBankId = testAccountId1
      val postJsonBody = APIMethods_AccountInformationServiceAISApi
        .resourceDocs
        .filter( _.partialFunction == APIMethods_AccountInformationServiceAISApi.createConsent)
        .head.exampleRequestBody.asInstanceOf[PostConsentJson]
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body.extract[PostConsentResponseJson].consentId should not be (empty)
      response.body.extract[PostConsentResponseJson].consentStatus should be ("received")
    }
  }


  feature(s"BG v1.3 - $createConsent and $deleteConsent") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val testBankId = testAccountId1
      val postJsonBody = APIMethods_AccountInformationServiceAISApi
        .resourceDocs
        .filter( _.partialFunction == APIMethods_AccountInformationServiceAISApi.createConsent)
        .head.exampleRequestBody.asInstanceOf[PostConsentJson]
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body.extract[PostConsentResponseJson].consentId should not be (empty)

      val consentId =response.body.extract[PostConsentResponseJson].consentId

      Then("We test the delete consent ")  
      val requestDelete = (V1_3_BG / "consents"/consentId ).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should be (204)

      //TODO We can not delete one consent two time, will fix it later.
//      val responseDeleteSecondTime = makeDeleteRequest(requestDelete)
//      responseDeleteSecondTime.code should be (400)
    }
  }  

  feature(s"BG v1.3 - $createConsent and $getConsentInformation and $getConsentStatus") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val testBankId = testAccountId1
      val postJsonBody = APIMethods_AccountInformationServiceAISApi
        .resourceDocs
        .filter( _.partialFunction == APIMethods_AccountInformationServiceAISApi.createConsent)
        .head.exampleRequestBody.asInstanceOf[PostConsentJson]
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body.extract[PostConsentResponseJson].consentId should not be (empty)

      val consentId =response.body.extract[PostConsentResponseJson].consentId

      Then(s"We test the $getConsentInformation")
      val requestGet = (V1_3_BG / "consents"/consentId ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      responseGet.code should be (200)
      responseGet.body.extract[GetConsentResponseJson].consentStatus should be ("received")

      Then(s"We test the $getConsentStatus")
      val requestGetStatus = (V1_3_BG / "consents"/consentId /"status" ).GET <@ (user1)
      val responseGetStatus = makeGetRequest(requestGetStatus)
      responseGetStatus.code should be (200)
      responseGetStatus.body.extract[ConsentStatusJsonV13].consentStatus should be ("received")
      
    }
  }

    feature(s"BG v1.3 - ${startConsentAuthorisation.name} ") {
      scenario("Authentication User, test succeed", BerlinGroupV1_3, startConsentAuthorisation) {
        val postJsonBody = APIMethods_AccountInformationServiceAISApi
          .resourceDocs
          .filter( _.partialFunction == APIMethods_AccountInformationServiceAISApi.createConsent)
          .head.exampleRequestBody.asInstanceOf[PostConsentJson] 
        val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
        val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

        Then("We should get a 201 ")
        response.code should equal(201)
        response.body.extract[PostConsentResponseJson].consentId should not be (empty)

        val consentId =response.body.extract[PostConsentResponseJson].consentId

        Then(s"We test the $startConsentAuthorisation")
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/consentId /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation)
        responseStartConsentAuthorisation.code should be (201)
        responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].scaStatus should be ("received")
      }
    }


    feature(s"BG v1.3 - ${startConsentAuthorisation.name} and ${getConsentAuthorisation.name} and ${getConsentScaStatus.name} and ${updateConsentsPsuData.name}") {
      scenario("Authentication User, test succeed", BerlinGroupV1_3, startConsentAuthorisation) {
        val postJsonBody = APIMethods_AccountInformationServiceAISApi
          .resourceDocs
          .filter( _.partialFunction == APIMethods_AccountInformationServiceAISApi.createConsent)
          .head.exampleRequestBody.asInstanceOf[PostConsentJson]
        val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
        val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))
  
        Then("We should get a 201 ")
        response.code should equal(201)
        response.body.extract[PostConsentResponseJson].consentId should not be (empty)
  
        val consentId =response.body.extract[PostConsentResponseJson].consentId
  
        Then(s"We test the $startConsentAuthorisation")
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/consentId /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation)
        responseStartConsentAuthorisation.code should be (201)
        responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].scaStatus should be ("received")

        Then(s"We test the $getConsentAuthorisation")
        val requestGetConsentAuthorisation = (V1_3_BG / "consents"/consentId /"authorisations" ).GET<@ (user1)
        val responseGetConsentAuthorisation = makeGetRequest(requestGetConsentAuthorisation)
        responseGetConsentAuthorisation.code should be (200)
        responseGetConsentAuthorisation.body.extract[AuthorisationJsonV13].authorisationIds.length > 0 should be (true)

        Then(s"We test the $getConsentScaStatus")
        val authorisationId = responseGetConsentAuthorisation.body.extract[AuthorisationJsonV13].authorisationIds.head
        val requestGetConsentScaStatus = (V1_3_BG / "consents"/consentId /"authorisations"/authorisationId ).GET <@ (user1)
        val responseGetConsentScaStatus = makeGetRequest(requestGetConsentScaStatus)
        responseGetConsentScaStatus.code should be (200)
        responseGetConsentScaStatus.body.extract[ScaStatusJsonV13].scaStatus should be ("received")
/*
        Then(s"We test the $updateConsentsPsuData")
        val updateConsentsPsuDataJsonBody = APIMethods_AccountInformationServiceAISApi
          .resourceDocs
          .filter( _.partialFunction == APIMethods_AccountInformationServiceAISApi.updateConsentsPsuData)
          .head.exampleRequestBody.asInstanceOf[JvalueCaseClass] //All the Json String convert to JvalueCaseClass implicitly 
          .jvalueToCaseclass.extract[PostConsentJson]
        val requestUpdateConsentsPsuData = (V1_3_BG / "consents"/consentId /"authorisations"/ authorisationId).PUT <@ (user1)
        val responseUpdateConsentsPsuData = makePutRequest(requestUpdateConsentsPsuData, write(updateConsentsPsuDataJsonBody))
        responseUpdateConsentsPsuData.code should be (200)
        responseUpdateConsentsPsuData.body.extract[PostConsentResponseJson].consentStatus should be ("received")
        */
      }
    }  

}