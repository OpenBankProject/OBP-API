package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.CoreAccountsJsonV13
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class AccountInformationServiceAISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object getAccountList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getAccountList))

  object getBalances extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getBalances))

  object getTransactionList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getTransactionList))

  object getCardAccountTransactionList extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getCardAccountTransactionList))

  object createConsent extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.createConsent))

  object deleteConsent extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.deleteConsent))

  object getConsentInformation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentInformation))

  object getConsentScaStatus extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentScaStatus))

  object getConsentStatus extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentStatus))

  object startConsentAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.startConsentAuthorisation))

  object updateConsentsPsuData extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.updateConsentsPsuData))

  object getConsentAuthorisation extends Tag(nameOf(APIMethods_AccountInformationServiceAISApi.getConsentAuthorisation))
  
  val consentJsonForPost =
    """{
      |  "access": {
      |    "accounts": [
      |      {
      |        "iban": "FR7612345987650123456789014",
      |        "bban": "BARC12345612345678",
      |        "pan": "5409050000000000",
      |        "maskedPan": "123456xxxxxx1234",
      |        "msisdn": "+49 170 1234567",
      |        "currency": "EUR"
      |      }
      |    ]
      |  },
      |  "recurringIndicator": false,
      |  "validUntil": "2020-12-31",
      |  "frequencyPerDay": 4,
      |  "combinedServiceIndicator": false
      |}""".stripMargin

  feature("BG v1.3 - getAccountList") {
    scenario("Not Authentication User, test failed ", BerlinGroupV1_3, getAccountList) {
      When("Post consent json with no empty accounts")
      val requestPost = (V1_3_BG / "accounts").GET
      val response = makeGetRequest(requestPost)

      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(UserNotLoggedIn)
    }

    scenario("Authentication User, test succeed", BerlinGroupV1_3, getAccountList) {
      When("Post consent json with no empty accounts")
      val requestPost = (V1_3_BG / "accounts").GET <@ (user1)
      val response = makeGetRequest(requestPost)

      Then("We should get a 200 ")
      response.code should equal(200)
      response.body.extract[CoreAccountsJsonV13].accounts.length > 1 should be (true)
      response.body.extract[CoreAccountsJsonV13].accounts.head.bban
    }
  }
  
  feature("test the BG v1.3 consent endpoints") {
    scenario("Fail call endpoint createConsent, because accounts is not empty", BerlinGroupV1_3) {
      When("Post consent json with no empty accounts")
      val requestPost = (V1_3_BG / "consents").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, consentJsonForPost)

      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessage]
        .message should startWith(InvalidJsonContent)
    }

    scenario("Successful call endpoint createConsent", BerlinGroupV1_3) {
      When("Post consent json with no empty accounts")
      val requestPost = (V1_3_BG / "consents").POST <@ (user1)
      val emptyAccountsJson = consentJsonForPost.replaceFirst("""(?s)("accounts"\s*\:\s*\[).*?(\])""", "$1 $2")
      val response: APIResponse = makePostRequest(requestPost, emptyAccountsJson)

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body \ "consentId" should not be null
      response.body \ "consentStatus" should not be null
      response.body \ "_links" \ "startAuthorisation" should not be null
    }
  }
}