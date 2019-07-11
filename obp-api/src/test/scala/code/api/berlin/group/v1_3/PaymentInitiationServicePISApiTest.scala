package code.api.berlin.group.v1_3

import code.api.ErrorMessage
import code.api.util.ErrorMessages._
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{InitiatePaymentResponseJson, StartPaymentAuthorisationJson}
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.util.APIUtil.OAuth._
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import code.tesobe.ErrorMessage
import code.transactionrequests.TransactionRequests.{PaymentServiceTypes, TransactionRequestTypes}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.SepaCreditTransfers
import net.liftweb.mapper.By
import org.scalatest.Tag

class PaymentInitiationServicePISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object PIS extends Tag("Payment Initiation Service (PIS)")
  object initiatePayment extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.initiatePayment))
  object getPaymentInformation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInformation))
  object getPaymentInitiationStatus extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationStatus))
  
  object startPaymentAuthorisation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.startPaymentAuthorisation))
  object getPaymentInitiationAuthorisation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationAuthorisation))
  object getPaymentInitiationScaStatus extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationScaStatus))
  object updatePaymentPsuData extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData))

  
  object startPaymentInitiationCancellationAuthorisation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.startPaymentInitiationCancellationAuthorisation))
  object getPaymentInitiationCancellationAuthorisationInformation extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentInitiationCancellationAuthorisationInformation))
  object getPaymentCancellationScaStatus extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.getPaymentCancellationScaStatus))
  object updatePaymentCancellationPsuData extends Tag(nameOf(APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuData))
  
  feature(s"test the BG v1.3 -${initiatePayment.name}") {
    scenario("Failed Case - Wrong Json format Body", BerlinGroupV1_3, PIS, initiatePayment) {
      val wrongInitiatePaymentJson =
        s"""{
           |"instructedAmount1": {
           |  "currency": "EUR",
           |  "amount": "1234"
           |},
           |"creditorAccount": {
           |  "iban": "123"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongInitiatePaymentJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidJsonFormat The Json body should be the $SepaCreditTransfers "
      And("error should be " + error)
      response.body.extract[code.api.ErrorMessage].message should equal (error)
    }
    scenario("Failed Case - wrong amount", BerlinGroupV1_3, PIS, initiatePayment) {
      val wrongAmountInitiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "123"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "-1234"
           |},
           |"creditorAccount": {
           |  "iban": "12321"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongAmountInitiatePaymentJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"${NotPositiveAmount} Current input is: '-1234'"
      And("error should be " + error)
      response.body.extract[code.api.ErrorMessage].message should equal (error)
    }
    scenario("Successful case - small amount -- change the balance", BerlinGroupV1_3, PIS, initiatePayment) {
      val accounts = MappedBankAccount.findAll().map(_.accountIban.get).filter(_ != null)
      val ibanFrom = accounts.head
      val ibanTo = accounts.last

      val beforePaymentFromAccountBalance = MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanFrom)).map(_.balance).openOrThrowException("Can not be empty here")
      val beforePaymentToAccountBalance =  MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanTo)).map(_.balance).openOrThrowException("Can not be empty here")

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${ibanFrom}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "12"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be ("ACCP")
      payment.paymentId should not be null
      payment._links.scaStatus should not be null


      val afterPaymentFromAccountBalance = MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanFrom)).map(_.balance).openOrThrowException("Can not be empty here")
      val afterPaymentToAccountBalacne =  MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanTo)).map(_.balance).openOrThrowException("Can not be empty here")

      afterPaymentFromAccountBalance-beforePaymentFromAccountBalance should be (BigDecimal(-12))
      afterPaymentToAccountBalacne-beforePaymentToAccountBalance should be (BigDecimal(12))
    }
    scenario("Successful case - big amount -- do not change the balance", BerlinGroupV1_3, PIS, initiatePayment) {
      val accounts = MappedBankAccount.findAll().map(_.accountIban.get).filter(_ != null)
      val ibanFrom = accounts.head
      val ibanTo = accounts.last

      val beforePaymentFromAccountBalance = MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanFrom)).map(_.balance).openOrThrowException("Can not be empty here")
      val beforePaymentToAccountBalance =  MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanTo)).map(_.balance).openOrThrowException("Can not be empty here")

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${ibanFrom}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "123324"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be ("RCVD")
      payment.paymentId should not be null
      payment._links.scaStatus should not be null


      val afterPaymentFromAccountBalance = MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanFrom)).map(_.balance).openOrThrowException("Can not be empty here")
      val afterPaymentToAccountBalacne =  MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanTo)).map(_.balance).openOrThrowException("Can not be empty here")

      afterPaymentFromAccountBalance-beforePaymentFromAccountBalance should be (BigDecimal(0))
      afterPaymentToAccountBalacne-beforePaymentToAccountBalance should be (BigDecimal(0))
    }
  }
  feature(s"test the BG v1.3 -${getPaymentInformation.name}") {
    scenario("Successful case ", BerlinGroupV1_3, PIS, initiatePayment) {
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
           |  "amount": "123"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be ("ACCP")
      payment.paymentId should not be null

      Then(s"we test the ${getPaymentInformation.name}")
      val paymentId = payment.paymentId
      val requestGet = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId).GET <@ (user1)
      val responseGet: APIResponse = makeGetRequest(requestGet)
      responseGet.code should be (200)
      responseGet.body.extract[SepaCreditTransfers].instructedAmount.currency should be ("EUR")
      responseGet.body.extract[SepaCreditTransfers].instructedAmount.amount should be ("123")
      responseGet.body.extract[SepaCreditTransfers].debtorAccount.iban should be (ibanFrom)
      responseGet.body.extract[SepaCreditTransfers].creditorAccount.iban should be (ibanTo)


    }
  }
  feature(s"test the BG v1.3 -${getPaymentInitiationStatus.name}") {
    scenario("Successful case ", BerlinGroupV1_3, PIS, initiatePayment) {
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
           |  "amount": "123324"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be ("RCVD")
      payment.paymentId should not be null
      payment._links.scaStatus should not be null

      Then(s"we test the ${getPaymentInitiationStatus.name}")
      val paymentId = payment.paymentId
      val requestGet = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "status").GET <@ (user1)
      val responseGet: APIResponse = makeGetRequest(requestGet)
      responseGet.code should be (200)
      (responseGet.body \ "transactionStatus").extract[String] should be ("RCVD")
      (responseGet.body \ "fundsAvailable").extract[Boolean] should be (true)
    }
  }
  feature(s"test the BG v1.3 ${startPaymentInitiationCancellationAuthorisation.name}") {
    scenario("Successful call endpoint startPaymentInitiationCancellationAuthorisation", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisation) {
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / "PAYMENT_ID" / "cancellation-authorisations").POST <@ (user1)
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
  feature(s"test the BG v1.3 ${startPaymentAuthorisation}") {
    scenario("Successful call endpoint startPaymentAuthorisation", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisation) {
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / "PAYMENT_ID" / "authorisations").POST <@ (user1)
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