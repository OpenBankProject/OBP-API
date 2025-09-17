package code.api.berlin.group.v1_3

import code.api.Constant
import code.api.Constant.{SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID}
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.PostViewJsonV400
import code.consent.ConsentStatus
import code.model.dataAccess.BankAccountRouting
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AccountInformationServiceAISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object getAccountList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getAccountList))
  
  object getAccountDetails extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getAccountDetails))

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

  def getNextMonthDate(): String = {
    val nextMonthDate = LocalDate.now().plusMonths(1)
    nextMonthDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
  }

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

  feature(s"BG v1.3 - $getAccountDetails") {
    scenario("Not Authentication User, test failed ", BerlinGroupV1_3, getAccountDetails) {
      val requestGet = (V1_3_BG / "accounts" / "accountId").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(UserNotLoggedIn)
    }

    scenario("Authentication User, test succeed", BerlinGroupV1_3, getAccountDetails) {
      val bankId = APIUtil.defaultBankId
      val accountId = testAccountId0.value
      
      
      grantUserAccessToViewViaEndpoint(
        bankId,
        accountId,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      grantUserAccessToViewViaEndpoint(
        bankId,
        accountId,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      grantUserAccessToViewViaEndpoint(
        bankId,
        accountId,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )
      
      val requestGet = (V1_3_BG / "accounts" / accountId).GET <@ (user1)
      val response = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      val jsonResponse = response.body.extract[AccountDetailsJsonV13]
      jsonResponse.account.resourceId should be (accountId)

      jsonResponse.account._links.balances match {
        case Some(link) =>
          link.href.contains(berlinGroupVersion1) shouldBe true
        case None => // Nothing to check
      }
      jsonResponse.account._links.transactions match {
        case Some(link) =>
          link.href.contains(berlinGroupVersion1) shouldBe true
        case None => // Nothing to check
      }

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
      val requestGet = (V1_3_BG / "accounts" /testAccountId1.value/ "transactions").GET <@ (user1) <<? List(("bookingStatus", "both"))
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response.body.extract[TransactionsJsonV13].transactions.booked.head.length >0 should be (true)
      response.body.extract[TransactionsJsonV13].transactions.pending.head.length >0 should be (true)

      val requestGet2 = (V1_3_BG / "accounts" / testAccountId1.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "booked"))
      val response2: APIResponse = makeGetRequest(requestGet2)
      Then("We should get a 200 ")
      response2.code should equal(200)
      response2.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response2.body.extract[TransactionsJsonV13].transactions.pending.isEmpty should be(true)
      response2.body.extract[TransactionsJsonV13].transactions.booked.nonEmpty should be(true)

      val requestGet3 = (V1_3_BG / "accounts" / testAccountId1.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "pending"))
      val response3: APIResponse = makeGetRequest(requestGet3)
      Then("We should get a 200 ")
      response3.code should equal(200)
      response3.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response3.body.extract[TransactionsJsonV13].transactions.pending.nonEmpty should be(true)
      response3.body.extract[TransactionsJsonV13].transactions.booked.isEmpty should be(true)
    }
  }

  feature(s"BG v1.3 - $getTransactionList - Parameter Validation") {
    scenario("Authentication User, test failed with invalid bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      val requestGetWithInvalidStatus = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "invalid"))
      val responseInvalid: APIResponse = makeGetRequest(requestGetWithInvalidStatus)
      Then("We should get a 400 for invalid bookingStatus")
      responseInvalid.code should equal(400)
      responseInvalid.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
      responseInvalid.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")
    }

    scenario("Authentication User, test failed with empty bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      val requestGetWithEmptyStatus = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", ""))
      val responseEmpty: APIResponse = makeGetRequest(requestGetWithEmptyStatus)
      Then("We should get a 400 for empty bookingStatus")
      responseEmpty.code should equal(400)
      responseEmpty.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
      responseEmpty.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")
    }

    scenario("Authentication User, test failed with case sensitive bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      val requestGetWithUpperCaseStatus = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "BOOKED"))
      val responseUpperCase: APIResponse = makeGetRequest(requestGetWithUpperCaseStatus)
      Then("We should get a 400 for case sensitive bookingStatus")
      responseUpperCase.code should equal(400)
      responseUpperCase.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
      responseUpperCase.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")

      val requestGetWithMixedCaseStatus = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "Booked"))
      val responseMixedCase: APIResponse = makeGetRequest(requestGetWithMixedCaseStatus)
      Then("We should get a 400 for mixed case bookingStatus")
      responseMixedCase.code should equal(400)
      responseMixedCase.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
      responseMixedCase.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")
    }

    scenario("Authentication User, test failed with special characters in bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      val invalidBookingStatuses = List("booked!", "pending@", "both#", "booked ", " booked", "booked;", "null", "undefined")
      
      invalidBookingStatuses.foreach { invalidStatus =>
        val requestGetWithSpecialChars = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", invalidStatus))
        val responseSpecialChars: APIResponse = makeGetRequest(requestGetWithSpecialChars)
        Then(s"We should get a 400 for bookingStatus with special characters: '$invalidStatus'")
        responseSpecialChars.code should equal(400)
        responseSpecialChars.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
        responseSpecialChars.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")
      }
    }

    scenario("Authentication User, test missing bookingStatus parameter handling", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      // Test without bookingStatus parameter - should fail because it returns empty string which is invalid
      val requestGetWithoutBookingStatus = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1)
      val responseWithoutParam: APIResponse = makeGetRequest(requestGetWithoutBookingStatus)
      Then("We should get a 400 for missing bookingStatus parameter (treated as empty string)")
      responseWithoutParam.code should equal(400)
      responseWithoutParam.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
      responseWithoutParam.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")
    }

    scenario("Authentication User, test multiple invalid bookingStatus parameters", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      // Test with multiple bookingStatus parameters - only first one should be considered
      val requestGetWithMultipleParams = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "invalid"), ("bookingStatus", "booked"))
      val responseMultipleParams: APIResponse = makeGetRequest(requestGetWithMultipleParams)
      Then("We should get a 400 because first parameter is invalid")
      responseMultipleParams.code should equal(400)
      responseMultipleParams.body.extract[ErrorMessage].message should include(DuplicateQueryParameters)
    }

    scenario("Authentication User, test URL encoding in bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      // Test with URL encoded values that should still be invalid
      val encodedInvalidStatuses = List("book%65d", "pend%69ng", "bot%68")
      
      encodedInvalidStatuses.foreach { encodedStatus =>
        val requestGetWithEncodedStatus = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", encodedStatus))
        val responseEncoded: APIResponse = makeGetRequest(requestGetWithEncodedStatus)
        Then(s"We should get a 400 for URL encoded invalid bookingStatus: '$encodedStatus'")
        responseEncoded.code should equal(400)
        responseEncoded.body.extract[ErrorMessagesBG].tppMessages.head.text should include(InvalidUrlParameters)
        responseEncoded.body.extract[ErrorMessagesBG].tppMessages.head.text should include("bookingStatus parameter must take two one of those values : booked, pending or both!")
      }
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
      val requestGet = (V1_3_BG / "accounts" / testAccountId.value / "transactions").GET <@ (user1) <<? List(("bookingStatus", "both"))
      val response: APIResponse = makeGetRequest(requestGet)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[TransactionsJsonV13].account.iban should not be ("")
      response.body.extract[TransactionsJsonV13].transactions.pending.head.length > 0 should be (true)
      val transactionId = response.body.extract[TransactionsJsonV13].transactions.pending.head.head.transactionId

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

  feature(s"BG v1.3 - $createConsent - postJsonBodyAvailableAccounts") {
    lazy val postJsonBody = PostConsentJson(
      access = ConsentAccessJson(
        accounts = None,
        balances = None,
        transactions = None,
        availableAccounts = Some("allAccounts"),
        allPsd2 = None
      ),
      recurringIndicator = false,
      validUntil = getNextMonthDate(),
      frequencyPerDay = 1,
      combinedServiceIndicator = Some(false)
    )
    val postJsonBodyWrong1 = postJsonBody.copy(
      access = postJsonBody.access.copy(
        availableAccounts = Some("wrong")
      )
    )
    val postJsonBodyWrong2 = postJsonBody.copy(
      frequencyPerDay = 2
    )
    val postJsonBodyWrong3 = postJsonBody.copy(
      recurringIndicator = true
    )

    scenario("Authentication User, test failed due to availableAccounts wrong value", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBodyWrong1))

      Then("We should get a 400")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BerlinGroupConsentAccessAvailableAccounts)
    }
    scenario("Authentication User, test failed due to frequency per day", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBodyWrong2))

      Then("We should get a 400")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BerlinGroupConsentAccessFrequencyPerDay)
    }
    scenario("Authentication User, test failed due to recurringIndicator = true", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBodyWrong3))

      Then("We should get a 400")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BerlinGroupConsentAccessRecurringIndicator)
    }
    scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      val jsonResponse = response.body.extract[PostConsentResponseJson]
      jsonResponse.consentId should not be (empty)
      jsonResponse.consentStatus should be (ConsentStatus.received.toString)
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
        validUntil = getNextMonthDate(),
        frequencyPerDay = 4,
        combinedServiceIndicator = Some(false)
      )
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      val jsonResponse = response.body.extract[PostConsentResponseJson]
      jsonResponse.consentId should not be (empty)
      jsonResponse.consentStatus should be (ConsentStatus.received.toString)
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
        validUntil = getNextMonthDate(),
        frequencyPerDay = 4,
        combinedServiceIndicator = Some(false)
      )
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body.extract[PostConsentResponseJson].consentId should not be (empty)

      val consentId =response.body.extract[PostConsentResponseJson].consentId

      Then("We test the delete consent ")  
      val requestDelete = (V1_3_BG / "consents"/ consentId ).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should be (204)

      Then(s"We test the $getConsentStatus")
      val requestGetStatus = (V1_3_BG / "consents" / consentId / "status").GET <@ (user1)
      val responseGetStatus = makeGetRequest(requestGetStatus)
      responseGetStatus.code should be(200)
      responseGetStatus.body.extract[ConsentStatusJsonV13].consentStatus should be(ConsentStatus.terminatedByTpp.toString)

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
        validUntil = getNextMonthDate(),
        frequencyPerDay = 4,
        combinedServiceIndicator = Some(false)
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
      responseGet.body.extract[GetConsentResponseJson].consentStatus should be (ConsentStatus.received.toString)

      Then(s"We test the $getConsentStatus")
      val requestGetStatus = (V1_3_BG / "consents"/consentId /"status" ).GET <@ (user1)
      val responseGetStatus = makeGetRequest(requestGetStatus)
      responseGetStatus.code should be (200)
      responseGetStatus.body.extract[ConsentStatusJsonV13].consentStatus should be (ConsentStatus.received.toString)
      
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
          validUntil = getNextMonthDate(),
          frequencyPerDay = 4,
          combinedServiceIndicator = Some(false)
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
        responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].scaStatus should be (ConsentStatus.received.toString)
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
          validUntil = getNextMonthDate(),
          frequencyPerDay = 4,
          combinedServiceIndicator = Some(false)
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
        responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].scaStatus should be (ConsentStatus.received.toString)

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
        responseGetConsentScaStatus.body.extract[ScaStatusJsonV13].scaStatus should be (ConsentStatus.received.toString)
      }
    }  

    feature(s"BG v1.3 - updateConsentsPsuData") {
      scenario("Authentication User, only mocked data, just test succeed", BerlinGroupV1_3, updateConsentsPsuDataTransactionAuthorisation) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
        responseStartConsentAuthorisation.code should be (403)
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