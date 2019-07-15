package code.api.berlin.group.v1_3

import code.api.BerlinGroup.ScaStatus
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{CancellationJsonV13, InitiatePaymentResponseJson, StartPaymentAuthorisationJson}
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{AuthorisationNotFound, InvalidJsonFormat, NotPositiveAmount, _}
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import code.transactionrequests.TransactionRequests.{PaymentServiceTypes, TransactionRequestTypes}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.SepaCreditTransfers
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

import scala.collection.immutable.List

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
  feature(s"test the BG v1.3 ${startPaymentAuthorisation.name} and ${getPaymentInitiationAuthorisation.name} and ${getPaymentInitiationScaStatus.name} and ${updatePaymentPsuData.name}") {
    scenario(s"${startPaymentAuthorisation.name} Failed Case - Wrong PaymentId", BerlinGroupV1_3, PIS, startPaymentAuthorisation) {
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / "PAYMENT_ID" / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """""")
      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[code.api.ErrorMessage].message should startWith (InvalidTransactionRequestId)
    }
    scenario(s"Successful Case ", BerlinGroupV1_3, PIS, startPaymentAuthorisation) {

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
           |  "amount": "12355"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      val requestInitiatePaymentJson = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val responseInitiatePaymentJson: APIResponse = makePostRequest(requestInitiatePaymentJson, initiatePaymentJson)
      Then("We should get a 201 ")
      responseInitiatePaymentJson.code should equal(201)
      val paymentResponseInitiatePaymentJson = responseInitiatePaymentJson.body.extract[InitiatePaymentResponseJson]
      paymentResponseInitiatePaymentJson.transactionStatus should be ("RCVD")
      paymentResponseInitiatePaymentJson.paymentId should not be null

      val paymentId = paymentResponseInitiatePaymentJson.paymentId

      Then(s"we test the ${startPaymentAuthorisation.name}")
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """""")
      Then("We should get a 200 ")
      response.code should equal(200)
      org.scalameta.logger.elem(response)
      val startPaymentAuthorisationResponse = response.body.extract[StartPaymentAuthorisationJson]
      startPaymentAuthorisationResponse.authorisationId should not be null
      startPaymentAuthorisationResponse.psuMessage should be ("Please check your SMS at a mobile device.")
      startPaymentAuthorisationResponse.scaStatus should be (ScaStatus.received.toString)
      startPaymentAuthorisationResponse._links.scaStatus should not be null
      
      Then(s"We can test the ${getPaymentInitiationAuthorisation.name}")
      val requestGetPaymentInitiationAuthorisation = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "authorisations").GET <@ (user1)
      val responseGetPaymentInitiationAuthorisation: APIResponse = makeGetRequest(requestGetPaymentInitiationAuthorisation)
      responseGetPaymentInitiationAuthorisation.code should be (200)
      responseGetPaymentInitiationAuthorisation.body.extract[List[StartPaymentAuthorisationJson]].length > 0 should be (true)
      val paymentInitiationAuthorisation = responseGetPaymentInitiationAuthorisation.body.extract[List[StartPaymentAuthorisationJson]].head
      val authorisationId = paymentInitiationAuthorisation.authorisationId
      paymentInitiationAuthorisation.scaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${getPaymentInitiationScaStatus.name}")
      val requestGetPaymentInitiationScaStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "authorisations" /authorisationId).GET <@ (user1)
      val responseGetPaymentInitiationScaStatus: APIResponse = makeGetRequest(requestGetPaymentInitiationScaStatus)
      responseGetPaymentInitiationScaStatus.code should be (200)
      val paymentInitiationScaStatus = (responseGetPaymentInitiationScaStatus.body \ "scaStatus").extract[String]
      paymentInitiationScaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${updatePaymentPsuData.name}")
      val updatePaymentPsuDataJsonBody = APIMethods_PaymentInitiationServicePISApi
        .resourceDocs
        .filter( _.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData)
        .head.exampleRequestBody.asInstanceOf[JvalueCaseClass] //All the Json String convert to JvalueCaseClass implicitly 
        .jvalueToCaseclass
      
      val requestUpdatePaymentPsuData = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "authorisations"/authorisationId).PUT <@ (user1)
      val responseUpdatePaymentPsuData: APIResponse = makePutRequest(requestUpdatePaymentPsuData, write(updatePaymentPsuDataJsonBody))
      responseUpdatePaymentPsuData.code should be (200)
      responseUpdatePaymentPsuData.body.extract[StartPaymentAuthorisationJson].scaStatus should be("finalised")
      responseUpdatePaymentPsuData.body.extract[StartPaymentAuthorisationJson].authorisationId should be(authorisationId)

      val afterPaymentFromAccountBalance = MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanFrom)).map(_.balance).openOrThrowException("Can not be empty here")
      val afterPaymentToAccountBalacne =  MappedBankAccount.find(By(MappedBankAccount.accountIban, ibanTo)).map(_.balance).openOrThrowException("Can not be empty here")

      afterPaymentFromAccountBalance-beforePaymentFromAccountBalance should be (BigDecimal(-12355.00))
      afterPaymentToAccountBalacne-beforePaymentToAccountBalance should be (BigDecimal(12355.00))
      
    }
    
  }
  feature(s"test the BG v1.3 ${startPaymentInitiationCancellationAuthorisation.name} " +
    s"and ${getPaymentInitiationCancellationAuthorisationInformation.name} " +
    s"and ${getPaymentCancellationScaStatus.name}" +
    s"and ${updatePaymentCancellationPsuData.name}") {
    scenario(s"${startPaymentInitiationCancellationAuthorisation.name} Failed Case - Wrong PaymentId", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisation) {

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / "PAYMENT_ID" / "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """""")
      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[code.api.ErrorMessage].message should startWith (InvalidTransactionRequestId)
    }
    scenario(s"Successful Case ", BerlinGroupV1_3, PIS) {

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
           |  "amount": "12355"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      val requestInitiatePaymentJson = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString).POST <@ (user1)
      val responseInitiatePaymentJson: APIResponse = makePostRequest(requestInitiatePaymentJson, initiatePaymentJson)
      Then("We should get a 201 ")
      responseInitiatePaymentJson.code should equal(201)
      val paymentResponseInitiatePaymentJson = responseInitiatePaymentJson.body.extract[InitiatePaymentResponseJson]
      paymentResponseInitiatePaymentJson.transactionStatus should be ("RCVD")

      val paymentId = paymentResponseInitiatePaymentJson.paymentId

      Then(s"we test the ${startPaymentInitiationCancellationAuthorisation.name}")
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost)
      Then("We should get a 200 ")
      response.code should equal(200)
      org.scalameta.logger.elem(response)
      val startPaymentAuthorisationResponse = response.body.extract[StartPaymentAuthorisationJson]
      startPaymentAuthorisationResponse.authorisationId should not be null
      startPaymentAuthorisationResponse.psuMessage should be ("Please check your SMS at a mobile device.")
      startPaymentAuthorisationResponse.scaStatus should be (ScaStatus.received.toString)
      startPaymentAuthorisationResponse._links.scaStatus should not be null

      Then(s"We can test the ${getPaymentInitiationCancellationAuthorisationInformation.name}")
      val requestGetPaymentInitiationCancellationAuthorisationInformation = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "cancellation-authorisations").GET <@ (user1)
      val responseGetPaymentInitiationCancellationAuthorisationInformation: APIResponse = makeGetRequest(requestGetPaymentInitiationCancellationAuthorisationInformation)
      responseGetPaymentInitiationCancellationAuthorisationInformation.code should be (200)
      responseGetPaymentInitiationCancellationAuthorisationInformation.body.extract[CancellationJsonV13].cancellationIds.length > 0 should be (true)
      val cancellationJsonV13 = responseGetPaymentInitiationCancellationAuthorisationInformation.body.extract[CancellationJsonV13].cancellationIds
      val cancelationId = cancellationJsonV13.head

      Then(s"We can test the ${getPaymentCancellationScaStatus.name}")
      val requestGetPaymentCancellationScaStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "cancellation-authorisations" /cancelationId).GET <@ (user1)
      val responseGetPaymentCancellationScaStatus: APIResponse = makeGetRequest(requestGetPaymentCancellationScaStatus)
      responseGetPaymentCancellationScaStatus.code should be (200)
      val cancellationScaStatus = (responseGetPaymentCancellationScaStatus.body \ "scaStatus").extract[String]
      cancellationScaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${updatePaymentCancellationPsuData.name}")
      val updatePaymentCancellationPsuDataJsonBody = APIMethods_PaymentInitiationServicePISApi
        .resourceDocs
        .filter( _.partialFunction == APIMethods_PaymentInitiationServicePISApi.updatePaymentCancellationPsuData)
        .head.exampleRequestBody.asInstanceOf[JvalueCaseClass] //All the Json String convert to JvalueCaseClass implicitly 
        .jvalueToCaseclass

      val requestUpdatePaymentCancellationPsuData = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.sepa_credit_transfers.toString / paymentId / "cancellation-authorisations"/cancelationId).PUT <@ (user1)
      val responseUpdatePaymentCancellationPsuData: APIResponse = makePutRequest(requestUpdatePaymentCancellationPsuData, write(updatePaymentCancellationPsuDataJsonBody))
      responseUpdatePaymentCancellationPsuData.code should be (200)
      responseUpdatePaymentCancellationPsuData.body.extract[StartPaymentAuthorisationJson].scaStatus should be("finalised")
      responseUpdatePaymentCancellationPsuData.body.extract[StartPaymentAuthorisationJson].authorisationId should be(cancelationId)

    }
  }

  feature("test the BG v1.3 getPaymentCancellationScaStatus") {
    scenario("Successful call endpoint getPaymentCancellationScaStatus", BerlinGroupV1_3, PIS, getPaymentCancellationScaStatus) {
      When("Post empty to call initiatePayment")
      val cancellationId = "NON_EXISTING_CANCELLATION_ID"
      val requestGet = (V1_3_BG /
        PaymentServiceTypes.bulk_payments.toString /
        TransactionRequestTypes.sepa_credit_transfers.toString /
        "PAYMENT_ID" /
        "cancellation-authorisations" /
        cancellationId).POST <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidTransactionRequestId Current TransactionRequestId(PAYMENT_ID) "
      And("error should be " + error)
      response.body.extract[code.api.ErrorMessage].message should equal (error)
    }
  }
  feature("test the BG v1.3 getPaymentInitiationAuthorisation") {
    scenario("Successful call endpoint getPaymentInitiationAuthorisation", BerlinGroupV1_3, PIS, getPaymentInitiationAuthorisation) {
      When("Post empty to call initiatePayment")
      val requestGet = (V1_3_BG /
        PaymentServiceTypes.bulk_payments.toString /
        TransactionRequestTypes.sepa_credit_transfers.toString /
        "NON_EXISTING_PAYMENT_ID" /
        "authorisations").POST <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidTransactionRequestId Current TransactionRequestId(NON_EXISTING_PAYMENT_ID) "
      And("error should be " + error)
      response.body.extract[code.api.ErrorMessage].message should equal (error)
    }
  }
  feature("test the BG v1.3 getPaymentInitiationCancellationAuthorisationInformation") {
    scenario("Successful call endpoint getPaymentInitiationCancellationAuthorisationInformation", BerlinGroupV1_3, PIS, getPaymentInitiationCancellationAuthorisationInformation) {
      When("Post empty to call initiatePayment")
      val requestGet = (V1_3_BG /
        PaymentServiceTypes.bulk_payments.toString /
        TransactionRequestTypes.sepa_credit_transfers.toString /
        "NON_EXISTING_PAYMENT_ID" /
        "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)
      Then("We should get a 200 ")
      response.code should equal(200)
      val payment = response.body.extract[CancellationJsonV13]
      payment.cancellationIds should be equals(0)
    }
  }

}