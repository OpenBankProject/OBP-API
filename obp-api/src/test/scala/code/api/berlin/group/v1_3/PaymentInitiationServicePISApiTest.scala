package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{InitiatePaymentResponseJson, StartPaymentAuthorisationJson}
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{InvalidJsonFormat, NotPositiveAmount}
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import code.transactionrequests.TransactionRequests.{PaymentServiceTypes, TransactionRequestTypes}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.SepaCreditTransfers
import org.scalatest.Tag

class PaymentInitiationServicePISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object PIS extends Tag("Payment Initiation Service (PIS)")
  object initiatePayment extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.initiatePayment))
  
  object getPaymentCancellationScaStatus extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentCancellationScaStatus))
  object getPaymentInitiationAuthorisation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationAuthorisation))
  object getPaymentInformation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInformation))
  object getPaymentInitiationCancellationAuthorisationInformation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationCancellationAuthorisationInformation))
  object getPaymentInitiationScaStatus extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationScaStatus))
  object getPaymentInitiationStatus extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationStatus))
  object startPaymentAuthorisation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisation))
  object startPaymentInitiationCancellationAuthorisation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisation))
  object updatePaymentCancellationPsuData extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuData))
  object updatePaymentPsuData extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData))

  lazy val accounts = MappedBankAccount.findAll().map(_.accountIban.get).filter(_ != null)
  lazy val ibanFrom = accounts.head
  lazy val ibanTo = accounts.last
  lazy val wrongInitiatePaymentJson =
    s"""{
       |"instructedAmount1": {
       |  "currency": "EUR",
       |  "amount": "1234"
       |},
       |"creditorAccount": {
       |  "iban": "${ibanTo}"
       |},
       |"creditorName": "70charname"
            }""".stripMargin
  lazy val initiatePaymentJson =
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
  lazy val wrongAmountInitiatePaymentJson =
    s"""{
       | "debtorAccount": {
       |   "iban": "${ibanFrom}"
       | },
       |"instructedAmount": {
       |  "currency": "EUR",
       |  "amount": "-1234"
       |},
       |"creditorAccount": {
       |  "iban": "${ibanTo}"
       |},
       |"creditorName": "70charname"
            }""".stripMargin

  feature("test the BG v1.3 Payment endpoints- wrong json") {
    scenario("Unsuccessful call endpoint initiatePayment", BerlinGroupV1_3, PIS, initiatePayment) {
      When("Post empty to call initiatePayment")
      val requestPost = (V1_3_BG / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongInitiatePaymentJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidJsonFormat The Json body should be the $SepaCreditTransfers "
      And("error should be " + error)
      response.body.extract[ErrorMessage].message should equal (error)
    }
    scenario("Unsuccessful call endpoint initiatePayment - wrong amount", BerlinGroupV1_3, PIS, initiatePayment) {
      When("Post empty to call initiatePayment")
      val requestPost = (V1_3_BG / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongAmountInitiatePaymentJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"${NotPositiveAmount} Current input is: '-1234'"
      And("error should be " + error)
      response.body.extract[ErrorMessage].message should equal (error)
    }
    
    scenario("Successful call endpoint initiatePayment", BerlinGroupV1_3, PIS, initiatePayment) {
      When("Post empty to call initiatePayment")
      val requestPost = (V1_3_BG / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should not be null
      payment.paymentId should not be null
      payment._links should not be null
      payment._links.scaRedirect should not be null
      payment._links.self should not be null
      payment._links.status should not be null
      payment._links.scaStatus should not be null
    }
  }
  
  feature("test the BG v1.3 startPaymentInitiationCancellationAuthorisation") {
    scenario("Successful call endpoint startPaymentInitiationCancellationAuthorisation", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisation) {
      When("Post empty to call initiatePayment")
      val requestPost = (V1_3_BG / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / "PAYMENT_ID" / "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """""")
      Then("We should get a 200 ")
      response.code should equal(200)
      org.scalameta.logger.elem(response)
      val payment = response.body.extract[StartPaymentAuthorisationJson]
      payment.authorisationId should not be null
      payment.psuMessage should not be null
      payment.scaStatus should not be null
      payment._links.scaStatus should not be null
    }
  }  
  
  feature("test the BG v1.3 startPaymentAuthorisation") {
    scenario("Successful call endpoint startPaymentAuthorisation", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisation) {
      When("Post empty to call initiatePayment")
      val requestPost = (V1_3_BG / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / "PAYMENT_ID" / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """""")
      Then("We should get a 200 ")
      response.code should equal(200)
      org.scalameta.logger.elem(response)
      val payment = response.body.extract[StartPaymentAuthorisationJson]
      payment.authorisationId should not be null
      payment.psuMessage should not be null
      payment.scaStatus should not be null
      payment._links.scaStatus should not be null
    }
  }
  
}