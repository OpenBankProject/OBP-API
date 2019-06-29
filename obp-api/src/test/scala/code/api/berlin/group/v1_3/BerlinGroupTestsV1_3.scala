package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import code.transactionrequests.TransactionRequests.{PaymentServiceTypes, TransactionRequestTypes}
import org.scalatest.Tag

class BerlinGroupTestsV1_3 extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object BerlinGroupConcentsV1_3 extends Tag("berlinGroup_concents_v1_3")

  object BerlinGroupPaymentV1_3 extends Tag("berlinGroup_Payment_v1_3")

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
    scenario("Fail call endpoint createConsent, because accounts is not empty", BerlinGroupConcentsV1_3) {
      When("Post consent json with no empty accounts")
      val requestPost = (urpPrefix / "consents").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, consentJsonForPost)

      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessage]
        .message should startWith(InvalidJsonContent)
    }

    scenario("Successful call endpoint createConsent", BerlinGroupConcentsV1_3) {
      When("Post consent json with no empty accounts")
      val requestPost = (urpPrefix / "consents").POST <@ (user1)
      val emptyAccountsJson = consentJsonForPost.replaceFirst("""(?s)("accounts"\s*\:\s*\[).*?(\])""", "$1 $2")
      val response: APIResponse = makePostRequest(requestPost, emptyAccountsJson)

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body \ "consentId" should not be null
      response.body \ "consentStatus" should not be null
      response.body \ "_links" \ "startAuthorisation" should not be null
    }
  }


  feature("test the BG v1.3 Payment endpoints") {
    scenario("Successful call endpoint initiatePayment", BerlinGroupPaymentV1_3) {
      When("Post empty to call initiatePayment")
      val accounts = MappedBankAccount.findAll().map(_.accountIban.get).filter(_ != null)
      val ibanFrom = accounts.head
      val ibanTo = accounts.last
      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${ibanFrom}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "1234"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
      val requestPost = (urpPrefix / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)

      Then("We should get a 201 ")
      response.code should equal(201)
      response.body \ "transactionStatus" should not be null
      response.body \ "paymentId" should not be null
      response.body \ "_links" should not be null
      response.body \ "_links" \ "scaRedirect" should not be null
      response.body \ "_links" \ "self" should not be null
      response.body \ "_links" \ "status" should not be null
      response.body \ "_links" \ "scaStatus" should not be null
    }
  }

}