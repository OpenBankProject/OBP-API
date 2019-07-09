package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.builder.ConfirmationOfFundsServicePIISApi.APIMethods_ConfirmationOfFundsServicePIISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class ConfirmationOfFundsServicePIISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object PIIS extends Tag("Confirmation of Funds Service (PIIS)")
  object checkAvailabilityOfFunds extends Tag(nameOf(APIMethods_ConfirmationOfFundsServicePIISApi.checkAvailabilityOfFunds))
  
  
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

  feature("test the BG v1.3 consent endpoints") {
    scenario("Fail call endpoint createConsent, because accounts is not empty", PIIS) {
      When("Post consent json with no empty accounts")
      val requestPost = (V1_3_BG / "consents").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, consentJsonForPost)

      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessage]
        .message should startWith(InvalidJsonContent)
    }

    scenario("Successful call endpoint createConsent", PIIS) {
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