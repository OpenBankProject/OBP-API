package code.api.berlin.group.v1_3

import code.api.Constant
import code.api.Constant.{SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID}
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.PostViewJsonV400
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

class AccountInformationServiceAISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object getAccountList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getAccountList))
  
  object readAccountDetails extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.readAccountDetails))

  object getBalances extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getBalances))

  object getTransactionList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getTransactionList))
  
  object getTransactionDetails extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getTransactionDetails))

  object getCardAccountTransactionList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getCardAccountTransactionList))

  object createConsent extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.createConsent))

  object deleteConsent extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.deleteConsent))

  object getConsentInformation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentInformation))

  object getConsentStatus extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentStatus))
  
  object startConsentAuthorisationTransactionAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationTransactionAuthorisation))
  object startConsentAuthorisationUpdatePsuAuthentication extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationUpdatePsuAuthentication))
  object startConsentAuthorisationSelectPsuAuthenticationMethod extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.startConsentAuthorisationSelectPsuAuthenticationMethod))

  object getConsentAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentAuthorisation))
  
  object getConsentScaStatus extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentScaStatus))
  
  object updateConsentsPsuDataTransactionAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataTransactionAuthorisation))
  object updateConsentsPsuDataUpdatePsuAuthentication extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdatePsuAuthentication))
  object updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod))
  object updateConsentsPsuDataUpdateAuthorisationConfirmation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.updateConsentsPsuDataUpdateAuthorisationConfirmation))

  
  feature(s"BG v1.3 - $getAccountList") {
    scenario("Not Authentication User, test failed ", BerlinGroupV1_3, getAccountList) {
      val requestGet = (V1_3_BG / "accounts").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(UserNotLoggedIn)
    }

    scenario("Authentication User, test failed", BerlinGroupV1_3, getAccountList) {
      val requestGet = (V1_3_BG / "accounts").GET <@ (user1)
      val response = makeGetRequest(requestGet)

      Then("We should get a 403 ")
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(NoViewReadAccountsBerlinGroup)
    }
  }
  
  feature(s"BG v1.3 - $readAccountDetails") {
    scenario("Not Authentication User, test failed ", BerlinGroupV1_3, readAccountDetails) {
      val requestGet = (V1_3_BG / "accounts" / "accountId").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(UserNotLoggedIn)
    }

    scenario("Authentication User, test succeed", BerlinGroupV1_3, readAccountDetails) {
      val bankId = APIUtil.defaultBankId
      val accountId = testAccountId0.value
      
      
      grantUserAccessToViewViaEndpoint(
        bankId,
        accountId,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      
      val requestGet = (V1_3_BG / "accounts" / accountId).GET <@ (user1)
      val response = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[AccountDetailsJsonV13].account.resourceId should be (accountId)
    }
  }

  feature(s"BG v1.3 - $getBalances") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getBalances) {
      val bankId = APIUtil.defaultBankId
      
      Then("We should get a 403 ")
      val requestGetFailed = (V1_3_BG / "accounts" / testAccountId1.value / "balances").GET <@ (user1)
      val responseGetFailed: APIResponse = makeGetRequest(requestGetFailed)
      responseGetFailed.code should equal(403)
      responseGetFailed.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(NoViewReadAccountsBerlinGroup)
      
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId1.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      
      val requestGet = (V1_3_BG / "accounts" / testAccountId1.value / "balances").GET <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[AccountBalancesV13].`balances`.length > 0 should be (true)
      response.body.extract[AccountBalancesV13].account.iban should not be ("")
    }
  }  

  feature(s"BG v1.3 - $getTransactionList") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1

      val requestGetFailed = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1)
      val responseGetFailed: APIResponse = makeGetRequest(requestGetFailed)
      Then("We should get a 403 ")
      responseGetFailed.code should equal(403)
      responseGetFailed.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(UserNoPermissionAccessView)
      
      val bankId = APIUtil.defaultBankId 
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      val requestGet = (V1_3_BG / "accounts" /testAccountId1.value/ "transactions").GET <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response.body.extract[TransactionsJsonV13].transactions.booked.length >0 should be (true)
      response.body.extract[TransactionsJsonV13].transactions.pending.length >0 should be (true)
    }
  }

  feature(s"BG v1.3 - $getTransactionDetails") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getTransactionDetails, getTransactionList) {
      val testAccountId = testAccountId1

      val requestGetFailed = (V1_3_BG / "accounts" / testAccountId.value / "transactions" / "whatever").GET <@ (user1)
      val responseGetFailed: APIResponse = makeGetRequest(requestGetFailed)
      Then("We should get a 403 ")
      responseGetFailed.code should equal(403)
      responseGetFailed.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(UserNoPermissionAccessView)
      
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      val requestGet = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response.body.extract[TransactionsJsonV13].transactions.booked.length > 0 should be (true)
      response.body.extract[TransactionsJsonV13].transactions.pending.length > 0 should be (true)
      val transactionId = response.body.extract[TransactionsJsonV13].transactions.booked.head.transactionId

      val requestGet2 = (V1_3_BG / "accounts" / testAccountId.value / "transactions" / transactionId).GET <@ (user1)
      val response2: APIResponse = makeGetRequest(requestGet2)
      response2.code should equal(200)
      response2.body.extract[SingleTransactionJsonV13].value.transactionsDetails.transactionId should be (transactionId)
    }
  }

  feature(s"BG v1.3 - $getCardAccountTransactionList") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, getCardAccountTransactionList) {
      val testAccountId = testAccountId1
      val requestGetFailed = (V1_3_BG / "card-accounts" / testAccountId.value / "transactions").GET <@ (user1)
      val responseGetFailed: APIResponse = makeGetRequest(requestGetFailed)
      Then("We should get a 403 ")
      responseGetFailed.code should equal(403)
      responseGetFailed.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(UserNoPermissionAccessView)

      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      val requestGet = (V1_3_BG / "card-accounts" / testAccountId.value / "transactions").GET <@ (user1)
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
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val acountRoutingIban = accountsRoutingIban.head
      val postJsonBody = PostConsentJson(
        access = ConsentAccessJson(
          accounts = Option(List( ConsentAccessAccountsJson(
            iban = Some(acountRoutingIban.accountRouting.address),
            bban = None,
            pan = None,
            maskedPan = None,
            msisdn = None,
            currency = None,
          ))),
          balances = None,
          transactions = None,
          availableAccounts = None,
          allPsd2 = None
        ),
        recurringIndicator = true,
        validUntil = "2020-12-31",
        frequencyPerDay = 4,
        combinedServiceIndicator = false
      )
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
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val acountRoutingIban = accountsRoutingIban.head
      val postJsonBody = PostConsentJson(
        access = ConsentAccessJson(
          accounts = Option(List( ConsentAccessAccountsJson(
            iban = Some(acountRoutingIban.accountRouting.address),
            bban = None,
            pan = None,
            maskedPan = None,
            msisdn = None,
            currency = None,
          ))),
          balances = None,
          transactions = None,
          availableAccounts = None,
          allPsd2 = None
        ),
        recurringIndicator = true,
        validUntil = "2020-12-31",
        frequencyPerDay = 4,
        combinedServiceIndicator = false
      )
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
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val acountRoutingIban = accountsRoutingIban.head
      val postJsonBody = PostConsentJson(
        access = ConsentAccessJson(
          accounts = Option(List( ConsentAccessAccountsJson(
            iban = Some(acountRoutingIban.accountRouting.address),
            bban = None,
            pan = None,
            maskedPan = None,
            msisdn = None,
            currency = None,
          ))),
          balances = None,
          transactions = None,
          availableAccounts = None,
          allPsd2 = None
        ),
        recurringIndicator = true,
        validUntil = "2020-12-31",
        frequencyPerDay = 4,
        combinedServiceIndicator = false
      )
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

    feature(s"BG v1.3 - ${startConsentAuthorisationTransactionAuthorisation.name} ") {
      scenario("Authentication User, test succeed", BerlinGroupV1_3, startConsentAuthorisationTransactionAuthorisation) {
        val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
        val acountRoutingIban = accountsRoutingIban.head
        val postJsonBody = PostConsentJson(
          access = ConsentAccessJson(
            accounts = Option(List( ConsentAccessAccountsJson(
              iban = Some(acountRoutingIban.accountRouting.address),
              bban = None,
              pan = None,
              maskedPan = None,
              msisdn = None,
              currency = None,
            ))),
            balances = None,
            transactions = None,
            availableAccounts = None,
            allPsd2 = None
          ),
          recurringIndicator = true,
          validUntil = "2020-12-31",
          frequencyPerDay = 4,
          combinedServiceIndicator = false
        )
        val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
        val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

        Then("We should get a 201 ")
        response.code should equal(201)
        response.body.extract[PostConsentResponseJson].consentId should not be (empty)

        val consentId =response.body.extract[PostConsentResponseJson].consentId

        Then(s"We test the $startConsentAuthorisationTransactionAuthorisation")
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/consentId /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
        responseStartConsentAuthorisation.code should be (201)
        responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].scaStatus should be ("received")
      }
    }
  
    feature(s"BG v1.3 - ${startConsentAuthorisationUpdatePsuAuthentication.name} ") {
      scenario("Authentication User, only mocked data, so only test successful case", BerlinGroupV1_3, startConsentAuthorisationUpdatePsuAuthentication) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{ "psuData": { "password": "start12"}}""")
        responseStartConsentAuthorisation.code should be (201)
      }
    }
  
    feature(s"BG v1.3 - ${startConsentAuthorisationSelectPsuAuthenticationMethod.name} ") {
      scenario("Authentication User, only mocked data, so only test successful case", BerlinGroupV1_3, startConsentAuthorisationSelectPsuAuthenticationMethod) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{"authenticationMethodId":"authenticationMethodId"}""")
        responseStartConsentAuthorisation.code should be (201)
      }
    }


    feature(s"BG v1.3 - ${startConsentAuthorisationTransactionAuthorisation.name} and ${getConsentAuthorisation.name} and ${getConsentScaStatus.name} and ${updateConsentsPsuDataTransactionAuthorisation.name}") {
      scenario("Authentication User, test succeed", BerlinGroupV1_3, startConsentAuthorisationTransactionAuthorisation) {
        val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
        val acountRoutingIban = accountsRoutingIban.head
        val postJsonBody = PostConsentJson(
          access = ConsentAccessJson(
            accounts = Option(List( ConsentAccessAccountsJson(
              iban = Some(acountRoutingIban.accountRouting.address),
              bban = None,
              pan = None,
              maskedPan = None,
              msisdn = None,
              currency = None,
            ))),
            balances = None,
            transactions = None,
            availableAccounts = None,
            allPsd2 = None
          ),
          recurringIndicator = true,
          validUntil = "2020-12-31",
          frequencyPerDay = 4,
          combinedServiceIndicator = false
        )
        val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
        val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))
  
        Then("We should get a 201 ")
        response.code should equal(201)
        response.body.extract[PostConsentResponseJson].consentId should not be (empty)
  
        val consentId =response.body.extract[PostConsentResponseJson].consentId
  
        Then(s"We test the $startConsentAuthorisationTransactionAuthorisation")
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/consentId /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
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
      }
    }  

    feature(s"BG v1.3 - updateConsentsPsuData") {
      scenario("Authentication User, only mocked data, just test succeed", BerlinGroupV1_3, updateConsentsPsuDataTransactionAuthorisation) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
        responseStartConsentAuthorisation.code should be (400)
      }
      
      
      scenario("Authentication User, only mocked data, just test succeed -updateConsentsPsuDataUpdatePsuAuthentication", BerlinGroupV1_3, updateConsentsPsuDataUpdatePsuAuthentication) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{  "psuData":{"password":"start12"  }}""")
        responseStartConsentAuthorisation.code should be (200)
      }
      scenario("Authentication User, only mocked data, just test succeed-updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod", BerlinGroupV1_3, updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{ "authenticationMethodId":""}""")
        responseStartConsentAuthorisation.code should be (200)
      }
      scenario("Authentication User, only mocked data, just test succeed-updateConsentsPsuDataUpdateAuthorisationConfirmation", BerlinGroupV1_3, updateConsentsPsuDataUpdateAuthorisationConfirmation) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{"confirmationCode":"confirmationCode"}""")
        responseStartConsentAuthorisation.code should be (200)
      }
    }  

}