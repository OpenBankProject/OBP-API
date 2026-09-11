package code.api.berlin.group.v1_3

import org.json4s._
import code.api.Constant
import code.api.Constant.{SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID}
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.Http4sBGv13AIS
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.berlin.group.v1_3.model.ScaStatusResponse
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.v4_0_0.PostViewJsonV400
import code.consent.{ConsentStatus, ConsentTrait, Consents}
import code.model.TokenType.Access
import code.model.UserX
import code.bankconnectors.DoobieBankAccountRoutingQueries
import code.model.dataAccess.ResourceUser
import code.setup.{APIResponse, DefaultUsers}
import code.token.Tokens
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import org.json4s.native.Serialization.write
import net.liftweb.util.Helpers.randomString
import net.liftweb.util.TimeHelpers.TimeSpan
import org.scalatest.Tag

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration._

class AccountInformationServiceAISApiTest extends BerlinGroupConsentFixtures {

  object getAccountList extends Tag(nameOf(Http4sBGv13AIS.getAccountList))

  object getAccountDetails extends Tag(nameOf(Http4sBGv13AIS.getAccountDetails))

  object getBalances extends Tag(nameOf(Http4sBGv13AIS.getBalances))

  object getTransactionList extends Tag(nameOf(Http4sBGv13AIS.getTransactionList))

  object getTransactionDetails extends Tag(nameOf(Http4sBGv13AIS.getTransactionDetails))

  object getCardAccountTransactionList extends Tag(nameOf(Http4sBGv13AIS.getCardAccountTransactionList))

  object createConsent extends Tag(nameOf(Http4sBGv13AIS.createConsent))

  object deleteConsent extends Tag(nameOf(Http4sBGv13AIS.deleteConsent))

  object getConsentInformation extends Tag(nameOf(Http4sBGv13AIS.getConsentInformation))

  object getConsentStatus extends Tag(nameOf(Http4sBGv13AIS.getConsentStatus))

  // body-dispatch variants — use string literals since handlers are unified in http4s
  object startConsentAuthorisationTransactionAuthorisation extends Tag("startConsentAuthorisationTransactionAuthorisation")
  object startConsentAuthorisationUpdatePsuAuthentication extends Tag("startConsentAuthorisationUpdatePsuAuthentication")
  object startConsentAuthorisationSelectPsuAuthenticationMethod extends Tag("startConsentAuthorisationSelectPsuAuthenticationMethod")

  object getConsentAuthorisation extends Tag(nameOf(Http4sBGv13AIS.getConsentAuthorisation))

  object getConsentScaStatus extends Tag(nameOf(Http4sBGv13AIS.getConsentScaStatus))

  object updateConsentsPsuDataTransactionAuthorisation extends Tag("updateConsentsPsuDataTransactionAuthorisation")
  object updateConsentsPsuDataUpdatePsuAuthentication extends Tag("updateConsentsPsuDataUpdatePsuAuthentication")
  object updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod extends Tag("updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod")
  object updateConsentsPsuDataUpdateAuthorisationConfirmation extends Tag("updateConsentsPsuDataUpdateAuthorisationConfirmation")

  Feature(s"BG v1.3 - $getAccountList") {
    Scenario("Not Authentication User, test failed ", BerlinGroupV1_3, getAccountList) {
      val requestGet = (V1_3_BG / "accounts").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(AuthenticatedUserIsRequired)
    }

    Scenario("Authentication User, test failed", BerlinGroupV1_3, getAccountList) {
      val requestGet = (V1_3_BG / "accounts").GET <@ (user1)
      val response = makeGetRequest(requestGet)

      Then("We should get a 403 ")
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(NoViewReadAccountsBerlinGroup)
    }
  }

  Feature(s"BG v1.3 - $getAccountDetails") {
    Scenario("Not Authentication User, test failed ", BerlinGroupV1_3, getAccountDetails) {
      val requestGet = (V1_3_BG / "accounts" / "accountId").GET
      val response = makeGetRequest(requestGet)

      Then("We should get a 401 ")
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(AuthenticatedUserIsRequired)
    }

    Scenario("Authentication User, test succeed", BerlinGroupV1_3, getAccountDetails) {
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

  Feature(s"BG v1.3 - $getBalances") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, getBalances) {
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

  Feature(s"BG v1.3 - $getTransactionList") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, getTransactionList) {
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

  Feature(s"BG v1.3 - $getTransactionList - Parameter Validation") {
    Scenario("Authentication User, test failed with invalid bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
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

    Scenario("Authentication User, test failed with empty bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
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

    Scenario("Authentication User, test failed with case sensitive bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
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

    Scenario("Authentication User, test failed with special characters in bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
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

    Scenario("Authentication User, test missing bookingStatus parameter handling", BerlinGroupV1_3, getTransactionList) {
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

    Scenario("Authentication User, test multiple invalid bookingStatus parameters", BerlinGroupV1_3, getTransactionList) {
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

    Scenario("Authentication User, test URL encoding in bookingStatus parameter", BerlinGroupV1_3, getTransactionList) {
      val testAccountId = testAccountId1
      val bankId = APIUtil.defaultBankId
      grantUserAccessToViewViaEndpoint(
        bankId,
        testAccountId.value,
        resourceUser1.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID, is_system = true)
      )

      // Values decode to "bad", "bookedx", "pendingz" — none of booked/pending/both.
      val encodedInvalidStatuses = List("%62%61%64", "boo%6b%65%64x", "pend%69ngz")
      
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

  Feature(s"BG v1.3 - $getTransactionDetails") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, getTransactionDetails, getTransactionList) {
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

  Feature(s"BG v1.3 - $getCardAccountTransactionList") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, getCardAccountTransactionList) {
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

  Feature(s"BG v1.3 - $createConsent - postJsonBodyAvailableAccounts") {
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

    Scenario("Authentication User, test failed due to availableAccounts wrong value", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBodyWrong1))

      Then("We should get a 400")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BerlinGroupConsentAccessAvailableAccounts)
    }
    Scenario("Authentication User, test failed due to frequency per day", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBodyWrong2))

      Then("We should get a 400")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BerlinGroupConsentAccessFrequencyPerDay)
    }
    Scenario("Authentication User, test failed due to recurringIndicator = true", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBodyWrong3))

      Then("We should get a 400")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(BerlinGroupConsentAccessRecurringIndicator)
    }
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents" ).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(postJsonBody))

      Then("We should get a 201 ")
      response.code should equal(201)
      val jsonResponse = response.body.extract[PostConsentResponseJson]
      jsonResponse.consentId should not be (empty)
      jsonResponse.consentStatus should be (ConsentStatus.received.toString)
    }

    Scenario("An availableAccounts consent gains the PSU's own accounts when it is authorised", BerlinGroupV1_3, updateConsentsPsuDataTransactionAuthorisation) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")

      Given("An availableAccounts consent, which names no IBAN")
      val requestPost = (V1_3_BG / "consents").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(postJsonBody))
      responsePost.code should equal(201)
      val consentId = responsePost.body.extract[PostConsentResponseJson].consentId

      Then("Nothing can be resolved at creation: the consent is lodged with an empty view list")
      consentViewsOf(consentId) should be (Nil)

      When("The PSU answers the SCA challenge")
      val requestStartAuthorisation = (V1_3_BG / "consents" / consentId / "authorisations").POST <@ (user1)
      val responseStartAuthorisation = makePostRequest(requestStartAuthorisation, """{"scaAuthenticationData":""}""")
      responseStartAuthorisation.code should be (201)
      val authorisationId = responseStartAuthorisation.body.extract[StartConsentAuthorisationJson].authorisationId

      val requestUpdatePsuData = (V1_3_BG / "consents" / consentId / "authorisations" / authorisationId).PUT <@ (user1)
      val responseUpdatePsuData = makePutRequest(requestUpdatePsuData, """{"scaAuthenticationData":"123"}""")
      responseUpdatePsuData.code should be (200)
      responseUpdatePsuData.body.extract[ScaStatusResponse].scaStatus should be ("valid")

      Then("The consent covers every IBAN-addressable account the authorising PSU holds, and only for reading the account list")
      val grantedViews = consentViewsOf(consentId)
      grantedViews should not be (empty)
      grantedViews.map(_.view_id).distinct should be (List(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID))
      grantedViews.map(v => (v.bank_id, v.account_id)).toSet should be (ibanAddressableAccountsHeldBy(resourceUser1))

      And("The account list it serves is not empty — before the views were materialised here, an authorised availableAccounts consent answered with nothing")
      ibanAddressableAccountsHeldBy(resourceUser1) should not be (empty)
    }

    Scenario("Authorising a consent that names one IBAN does not widen it to the PSU's other accounts", BerlinGroupV1_3, updateConsentsPsuDataTransactionAuthorisation) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")

      Given("A consent narrowed to a single IBAN, and a PSU who holds more than that one account")
      ibanAddressableAccountsHeldBy(resourceUser1).size should be > 1
      val requestPost = (V1_3_BG / "consents").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(bgConsentPostBody()))
      responsePost.code should equal(201)
      val consentId = responsePost.body.extract[PostConsentResponseJson].consentId
      val viewsAtCreation = consentViewsOf(consentId)
      viewsAtCreation should not be (empty)

      When("The PSU authorises it")
      val requestStartAuthorisation = (V1_3_BG / "consents" / consentId / "authorisations").POST <@ (user1)
      val responseStartAuthorisation = makePostRequest(requestStartAuthorisation, """{"scaAuthenticationData":""}""")
      responseStartAuthorisation.code should be (201)
      val authorisationId = responseStartAuthorisation.body.extract[StartConsentAuthorisationJson].authorisationId

      val requestUpdatePsuData = (V1_3_BG / "consents" / consentId / "authorisations" / authorisationId).PUT <@ (user1)
      val responseUpdatePsuData = makePutRequest(requestUpdatePsuData, """{"scaAuthenticationData":"123"}""")
      responseUpdatePsuData.code should be (200)

      Then("Its accounts are exactly the ones it named. This is the availableAccounts test's mirror image: " +
        "the predicate that decides whether to resolve the PSU's holdings must read the value of " +
        "availableAccounts, not merely whether an access object is present — every consent carries one")
      consentViewsOf(consentId) should be (viewsAtCreation)
    }
  }

  Feature(s"BG v1.3 - $createConsent") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val testBankId = testAccountId1
      val accountsRoutingIban = DoobieBankAccountRoutingQueries.findAllByScheme(AccountRoutingScheme.IBAN.toString)
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


  Feature(s"BG v1.3 - $createConsent and $deleteConsent") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val testBankId = testAccountId1
      val accountsRoutingIban = DoobieBankAccountRoutingQueries.findAllByScheme(AccountRoutingScheme.IBAN.toString)
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

  Feature(s"BG v1.3 - $createConsent and $getConsentInformation and $getConsentStatus") {
    Scenario("Authentication User, test succeed", BerlinGroupV1_3, createConsent) {
      val testBankId = testAccountId1
      val accountsRoutingIban = DoobieBankAccountRoutingQueries.findAllByScheme(AccountRoutingScheme.IBAN.toString)
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

    Feature(s"BG v1.3 - ${startConsentAuthorisationTransactionAuthorisation.name} ") {
      Scenario("Authentication User, test succeed", BerlinGroupV1_3, startConsentAuthorisationTransactionAuthorisation) {
        val accountsRoutingIban = DoobieBankAccountRoutingQueries.findAllByScheme(AccountRoutingScheme.IBAN.toString)
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
  
    Feature(s"BG v1.3 - ${startConsentAuthorisationUpdatePsuAuthentication.name} ") {
      Scenario("Authentication User, only mocked data, so only test successful case", BerlinGroupV1_3, startConsentAuthorisationUpdatePsuAuthentication) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{ "psuData": { "password": "start12"}}""")
        responseStartConsentAuthorisation.code should be (201)
      }
    }
  
    Feature(s"BG v1.3 - ${startConsentAuthorisationSelectPsuAuthenticationMethod.name} ") {
      Scenario("Authentication User, only mocked data, so only test successful case", BerlinGroupV1_3, startConsentAuthorisationSelectPsuAuthenticationMethod) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations" ).POST <@ (user1)
        val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{"authenticationMethodId":"authenticationMethodId"}""")
        responseStartConsentAuthorisation.code should be (201)
      }
    }


    Feature(s"BG v1.3 - ${startConsentAuthorisationTransactionAuthorisation.name} and ${getConsentAuthorisation.name} and ${getConsentScaStatus.name} and ${updateConsentsPsuDataTransactionAuthorisation.name}") {
      Scenario("Authentication User, test succeed", BerlinGroupV1_3, startConsentAuthorisationTransactionAuthorisation) {
        val accountsRoutingIban = DoobieBankAccountRoutingQueries.findAllByScheme(AccountRoutingScheme.IBAN.toString)
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

    Feature(s"BG v1.3 - updateConsentsPsuData") {
      Scenario("Authentication User, only mocked data, just test succeed", BerlinGroupV1_3, updateConsentsPsuDataTransactionAuthorisation) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
        responseStartConsentAuthorisation.code should be (403)
      }
      
      
      Scenario("Authentication User, only mocked data, just test succeed -updateConsentsPsuDataUpdatePsuAuthentication", BerlinGroupV1_3, updateConsentsPsuDataUpdatePsuAuthentication) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{  "psuData":{"password":"start12"  }}""")
        responseStartConsentAuthorisation.code should be (200)
      }
      Scenario("Authentication User, only mocked data, just test succeed-updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod", BerlinGroupV1_3, updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{ "authenticationMethodId":""}""")
        responseStartConsentAuthorisation.code should be (200)
      }
      Scenario("Authentication User, only mocked data, just test succeed-updateConsentsPsuDataUpdateAuthorisationConfirmation", BerlinGroupV1_3, updateConsentsPsuDataUpdateAuthorisationConfirmation) {
        val requestStartConsentAuthorisation = (V1_3_BG / "consents"/"consentId" /"authorisations"/ "AUTHORISATIONID" ).PUT <@ (user1)
        val responseStartConsentAuthorisation = makePutRequest(requestStartConsentAuthorisation, """{"confirmationCode":"confirmationCode"}""")
        responseStartConsentAuthorisation.code should be (200)
      }
    }

  Feature(s"BG v1.3 - unclaimed consent SCA (regression: GET /obp/v5.1.0/user/current/consents/CONSENT_ID 404 before SCA, wrong authorisationId from ${startConsentAuthorisationTransactionAuthorisation.name})") {
    Scenario("Unclaimed consent: viewable pre-SCA by any user, authorisable, and claimed by the answering PSU on correct OTP", BerlinGroupV1_3, startConsentAuthorisationTransactionAuthorisation, updateConsentsPsuDataTransactionAuthorisation) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")

      val createdConsent = createUnclaimedBerlinGroupConsent()
      Option(createdConsent.userId).forall(_.isBlank) should be (true)
      val consentId = createdConsent.consentId

      Then("A different logged-in user can GET the unclaimed consent — this used to 404 (Bug A)")
      val requestGetConsent = (baseRequest / "obp" / "v5.1.0" / "user" / "current" / "consents" / consentId).GET <@ (user2)
      val responseGetConsent = makeGetRequest(requestGetConsent)
      responseGetConsent.code should be (200)

      Then(s"We test the $startConsentAuthorisationTransactionAuthorisation")
      val requestStartConsentAuthorisation = (V1_3_BG / "consents" / consentId / "authorisations").POST <@ (user1)
      val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
      responseStartConsentAuthorisation.code should be (201)
      val authorisationId = responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].authorisationId

      Then("The returned authorisationId must resolve on GET — this used to be the wrong field (Bug C)")
      val requestGetConsentScaStatus = (V1_3_BG / "consents" / consentId / "authorisations" / authorisationId).GET <@ (user1)
      val responseGetConsentScaStatus = makeGetRequest(requestGetConsentScaStatus)
      responseGetConsentScaStatus.code should be (200)

      Then(s"We submit the correct OTP to $updateConsentsPsuDataTransactionAuthorisation and the consent becomes valid, owned by the answering PSU")
      val requestUpdatePsuData = (V1_3_BG / "consents" / consentId / "authorisations" / authorisationId).PUT <@ (user1)
      val responseUpdatePsuData = makePutRequest(requestUpdatePsuData, """{"scaAuthenticationData":"123"}""")
      responseUpdatePsuData.code should be (200)
      responseUpdatePsuData.body.extract[ScaStatusResponse].scaStatus should be ("valid")

      val updatedConsent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("test consent lookup failed")
      updatedConsent.userId should be (resourceUser1.userId)
      updatedConsent.status should be (ConsentStatus.valid.toString)
    }

    Scenario("Unclaimed consent: an incorrect OTP is rejected with 400 and the consent stays unclaimed (documents that updateConsentUser in updateConsentsPsuDataAll is never reached on a failed challenge answer, unrelated to this fix)", BerlinGroupV1_3, updateConsentsPsuDataTransactionAuthorisation) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")

      val createdConsent = createUnclaimedBerlinGroupConsent()
      val consentId = createdConsent.consentId

      val requestStartConsentAuthorisation = (V1_3_BG / "consents" / consentId / "authorisations").POST <@ (user1)
      val responseStartConsentAuthorisation = makePostRequest(requestStartConsentAuthorisation, """{"scaAuthenticationData":""}""")
      responseStartConsentAuthorisation.code should be (201)
      val authorisationId = responseStartConsentAuthorisation.body.extract[StartConsentAuthorisationJson].authorisationId

      Then("We submit a wrong OTP")
      val requestUpdatePsuData = (V1_3_BG / "consents" / consentId / "authorisations" / authorisationId).PUT <@ (user1)
      val responseUpdatePsuData = makePutRequest(requestUpdatePsuData, """{"scaAuthenticationData":"wrong-otp"}""")
      responseUpdatePsuData.code should be (400)
      responseUpdatePsuData.body.extract[ErrorMessagesBG].tppMessages.head.text should include ("OBP-40016")

      Then("The consent is not claimed — validateChallengeAnswerC4 fails the Box before updateConsentUser runs")
      val updatedConsent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("test consent lookup failed")
      Option(updatedConsent.userId).forall(_.isBlank) should be (true)
      updatedConsent.status should be (ConsentStatus.received.toString)
    }
  }

  Feature(s"BG v1.3 - $createConsent consent ownership") {
    Scenario("A consent lodged on a client-credentials session is left unowned, not bound to the consumer's own pseudo-user", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents").POST <@ (clientCredentialsSession)
      val response: APIResponse = makePostRequest(requestPost, write(bgConsentPostBody()))

      Then("We should get a 201")
      response.code should equal(201)
      val consentId = response.body.extract[PostConsentResponseJson].consentId

      Then("The consent must be left unowned — a pseudo-user owner is neither blank nor the PSU, so it " +
        "would fail the mUserId guard on GET /obp/v5.1.0/user/current/consents/CONSENT_ID and hide the " +
        "consent from the real PSU at SCA time (OBP-35001)")
      val createdConsent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("test consent lookup failed")
      Option(createdConsent.userId).forall(_.isBlank) should be (true)
      createdConsent.status should be (ConsentStatus.received.toString)
    }

    Scenario("A consent lodged on a genuine PSU session is still owned by that PSU", BerlinGroupV1_3, createConsent) {
      val requestPost = (V1_3_BG / "consents").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, write(bgConsentPostBody()))

      Then("We should get a 201")
      response.code should equal(201)
      val consentId = response.body.extract[PostConsentResponseJson].consentId

      Then("Filtering out the consumer's pseudo-user must not drop a real PSU session (DirectLogin/OAuth1)")
      val createdConsent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("test consent lookup failed")
      createdConsent.userId should be (resourceUser1.userId)
    }
  }

  // Reading a consent's authorisation sub-resources is the TPP polling its own consent -- in Berlin
  // Group the PSU never calls the API at all, it authenticates at the ASPSP under Redirect or hands
  // its factors to the TPP under Embedded. Both handlers already reflect that, taking no user. The
  // ResourceDocs did not, and a doc left on the UserOnly default sends the middleware down
  // anonymousAccess, which 401s a request carrying no user.
  //
  // Pinned because nothing else would notice a revert: these keep working for as long as OAuth2
  // token parsing auto-vivifies a user for a client-credentials token, and start 401ing the day
  // that stops. The consent endpoints in the same file have been UserOrApplication all along, so
  // this is also a consistency guard within the family.
  Feature("BG v1.3 - consent authorisation sub-resources accept a client-credentials caller") {
    for (name <- List(nameOf(Http4sBGv13AIS.getConsentAuthorisation), nameOf(Http4sBGv13AIS.getConsentScaStatus))) {
      Scenario(s"$name declares UserOrApplication", BerlinGroupV1_3) {
        val docs = APIUtil.ResourceDoc.getResourceDocs(
          List(APIUtil.buildOperationId(ConstantsBG.berlinGroupVersion1, name)))
        docs should not be empty
        docs.foreach(_.authMode should equal(APIUtil.UserOrApplication))
      }
    }
  }

}