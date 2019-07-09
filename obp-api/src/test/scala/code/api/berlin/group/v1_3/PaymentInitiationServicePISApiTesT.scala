package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import code.transactionrequests.TransactionRequests.{PaymentServiceTypes, TransactionRequestTypes}
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class PaymentInitiationServicePISApiTesT extends BerlinGroupServerSetupV1_3 with DefaultUsers {

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

  feature("test the BG v1.3 Payment endpoints") {
    scenario("Successful call endpoint initiatePayment", BerlinGroupV1_3, initiatePayment) {
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
      val requestPost = (V1_3_BG / PaymentServiceTypes.bulk_payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
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