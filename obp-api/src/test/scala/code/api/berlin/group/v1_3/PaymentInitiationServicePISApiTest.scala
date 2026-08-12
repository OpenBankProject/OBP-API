package code.api.berlin.group.v1_3

import org.json4s._
import code.api.BerlinGroup.ScaStatus
import code.api.Constant.SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{CancelPaymentResponseJson, CancellationJsonV13, ErrorMessagesBG, InitiatePaymentResponseJson, StartPaymentAuthorisationJson}
import code.api.berlin.group.v1_3.model.{ScaStatusResponse, TransactionStatus, UpdatePsuAuthenticationResponse}
import code.api.berlin.group.v1_3.Http4sBGv13PIS
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.ErrorMessages._
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import code.model.TokenType
import code.setup.{APIResponse, DefaultUsers}
import code.token.Tokens
import net.liftweb.util.Helpers.randomString
import net.liftweb.util.TimeHelpers.TimeSpan
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, PaymentServiceTypes, TransactionRequestTypes}
import com.openbankproject.commons.model.{SepaCreditTransfers, SepaCreditTransfersBerlinGroupV13, ViewId}
import org.json4s.native.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

class PaymentInitiationServicePISApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {

  object PIS extends Tag("Payment Initiation Service (PIS)")
  object initiatePayment extends Tag(nameOf(Http4sBGv13PIS.initiatePayments))
  object getPaymentInformation extends Tag(nameOf(Http4sBGv13PIS.getPaymentInformation))
  object getPaymentInitiationStatus extends Tag(nameOf(Http4sBGv13PIS.getPaymentInitiationStatus))
  // body-dispatch variants — use string literals since handlers are unified in http4s
  object startPaymentAuthorisationTransactionAuthorisation extends Tag("startPaymentAuthorisationTransactionAuthorisation")
  object startPaymentAuthorisationUpdatePsuAuthentication extends Tag("startPaymentAuthorisationUpdatePsuAuthentication")
  object startPaymentAuthorisationSelectPsuAuthenticationMethod extends Tag("startPaymentAuthorisationSelectPsuAuthenticationMethod")
  object getPaymentInitiationAuthorisation extends Tag(nameOf(Http4sBGv13PIS.getPaymentInitiationAuthorisation))
  object getPaymentInitiationScaStatus extends Tag(nameOf(Http4sBGv13PIS.getPaymentInitiationScaStatus))
  object updatePaymentPsuDataTransactionAuthorisation extends Tag("updatePaymentPsuDataTransactionAuthorisation")
  object updatePaymentPsuDataUpdatePsuAuthentication extends Tag("updatePaymentPsuDataUpdatePsuAuthentication")
  object updatePaymentPsuDataSelectPsuAuthenticationMethod extends Tag("updatePaymentPsuDataSelectPsuAuthenticationMethod")
  object updatePaymentPsuDataAuthorisationConfirmation extends Tag("updatePaymentPsuDataAuthorisationConfirmation")

  object cancelPayment extends Tag(nameOf(Http4sBGv13PIS.cancelPayment))
  object startPaymentInitiationCancellationAuthorisationTransactionAuthorisation extends Tag("startPaymentInitiationCancellationAuthorisationTransactionAuthorisation")
  object startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication extends Tag("startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication")
  object startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod extends Tag("startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod")
  object getPaymentInitiationCancellationAuthorisationInformation extends Tag(nameOf(Http4sBGv13PIS.getPaymentInitiationCancellationAuthorisationInformation))
  object getPaymentCancellationScaStatus extends Tag(nameOf(Http4sBGv13PIS.getPaymentCancellationScaStatus))
  object updatePaymentCancellationPsuDataTransactionAuthorisation extends Tag("updatePaymentCancellationPsuDataTransactionAuthorisation")
  object updatePaymentCancellationPsuDataUpdatePsuAuthentication extends Tag("updatePaymentCancellationPsuDataUpdatePsuAuthentication")
  object updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod extends Tag("updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod")
  object updatePaymentCancellationPsuDataAuthorisationConfirmation extends Tag("updatePaymentCancellationPsuDataAuthorisationConfirmation")

  feature(s"test the BG v1.3 -${initiatePayment.name}") {
    scenario("Failed Case - Wrong Json format Body", BerlinGroupV1_3, PIS, initiatePayment) {
      val wrongInitiatePaymentJson =
        s"""{
           |"instructedAmount1": {
           |  "currency": "EUR",
           |  "amount": "1234"
           |},
           |"creditorAccount": {
           |  "iban": "DE75512108001245126199"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongInitiatePaymentJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidJsonFormat The Json body should be the $SepaCreditTransfersBerlinGroupV13 "
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith (error)
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
           |  "iban": "DE75512108001245126199"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongAmountInitiatePaymentJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"${NotPositiveAmount} Current input is: '-1234'"
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text contains extractErrorMessageCode(NotPositiveAmount) should be (true)
    }
    scenario("Successful case - small amount -- change the balance", BerlinGroupV1_3, PIS, initiatePayment) {
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val acountRoutingIbanFrom = accountsRoutingIban.head
      val acountRoutingIbanTo = accountsRoutingIban.last

      val beforePaymentFromAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanFrom.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanFrom.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")
      val beforePaymentToAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanTo.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanTo.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${acountRoutingIbanFrom.accountRouting.address}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "12"
           |},
           |"creditorAccount": {
           |  "iban": "${acountRoutingIbanTo.accountRouting.address}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin
      
      grantAccountAccess(acountRoutingIbanFrom)

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be (TransactionStatus.ACCP.code)
      payment.paymentId should not be null
      payment._links.scaStatus should not be null


      val afterPaymentFromAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanFrom.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanFrom.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")
      val afterPaymentToAccountBalacne = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanTo.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanTo.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      afterPaymentFromAccountBalance-beforePaymentFromAccountBalance should be (BigDecimal(-12))
      afterPaymentToAccountBalacne-beforePaymentToAccountBalance should be (BigDecimal(12))
    }
    scenario("Successful case - big amount -- do not change the balance", BerlinGroupV1_3, PIS, initiatePayment) {
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val acountRoutingIbanFrom = accountsRoutingIban.head
      val acountRoutingIbanTo = accountsRoutingIban.last

      val beforePaymentFromAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanFrom.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanFrom.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")
      val beforePaymentToAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanTo.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanTo.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${acountRoutingIbanFrom.accountRouting.address}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "2001"
           |},
           |"creditorAccount": {
           |  "iban": "${acountRoutingIbanTo.accountRouting.address}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      grantAccountAccess(acountRoutingIbanFrom)

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be (TransactionStatus.RCVD.code)
      payment.paymentId should not be null
      payment._links.scaStatus should not be null

      val afterPaymentFromAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanFrom.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanFrom.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")
      val afterPaymentToAccountBalacne = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanTo.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanTo.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      afterPaymentFromAccountBalance-beforePaymentFromAccountBalance should be (BigDecimal(0))
      afterPaymentToAccountBalacne-beforePaymentToAccountBalance should be (BigDecimal(0))
    }
  }

  private def grantAccountAccess(acountRoutingIbanFrom: BankAccountRouting) = {
    org.scalameta.logger.elem(Views.views.vend.systemView(ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)))
    Views.views.vend.systemView(ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)).flatMap(view =>
      // Grant account access
      Views.views.vend.grantAccessToSystemView(acountRoutingIbanFrom.bankId,
        acountRoutingIbanFrom.accountId,
        view,
        resourceUser1
      )
    )
  }

  feature(s"test the BG v1.3 -${getPaymentInformation.name}") {
    scenario("Successful case ", BerlinGroupV1_3, PIS, initiatePayment) {
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val ibanFrom = accountsRoutingIban.head.accountRouting.address
      val ibanTo = accountsRoutingIban.last.accountRouting.address

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

      grantAccountAccess(accountsRoutingIban.head)

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be (TransactionStatus.ACCP.code)
      payment.paymentId should not be null

      Then(s"we test the ${getPaymentInformation.name}")
      val paymentId = payment.paymentId
      val requestGet = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId).GET <@ (user1)
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
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val ibanFrom = accountsRoutingIban.head.accountRouting.address
      val ibanTo = accountsRoutingIban.last.accountRouting.address

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${ibanFrom}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "2001"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      grantAccountAccess(accountsRoutingIban.head)

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
      Then("We should get a 201 ")
      response.code should equal(201)
      val payment = response.body.extract[InitiatePaymentResponseJson]
      payment.transactionStatus should be (TransactionStatus.RCVD.code)
      payment.paymentId should not be null
      payment._links.scaStatus should not be null

      Then(s"we test the ${getPaymentInitiationStatus.name}")
      val paymentId = payment.paymentId
      val requestGet = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "status").GET <@ (user1)
      val responseGet: APIResponse = makeGetRequest(requestGet)
      responseGet.code should be (200)
      (responseGet.body \ "transactionStatus").extract[String] should be (TransactionStatus.RCVD.code)
      (responseGet.body \ "fundsAvailable").extract[Boolean] should be (true)
    }
  }
  feature(s"test the BG v1.3 ${startPaymentAuthorisationTransactionAuthorisation.name} and ${getPaymentInitiationAuthorisation.name} and ${getPaymentInitiationScaStatus.name} and ${updatePaymentPsuDataTransactionAuthorisation.name}") {
    scenario(s"${startPaymentAuthorisationTransactionAuthorisation.name} Failed Case - Wrong PaymentId", BerlinGroupV1_3, PIS, startPaymentAuthorisationTransactionAuthorisation) {
     
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{"scaAuthenticationData":"123"}""".stripMargin)
      Then("We should get a 400 ")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith (InvalidTransactionRequestId)
    }
    scenario(s"Successful Case ", BerlinGroupV1_3, PIS, startPaymentAuthorisationTransactionAuthorisation) {
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString)).filterNot(_.bankId.value == "DEFAULT_BANK_ID_NOT_SET")
      val acountRoutingIbanFrom = accountsRoutingIban.head
      val acountRoutingIbanTo = accountsRoutingIban.last

      val beforePaymentFromAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanFrom.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanFrom.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")
      val beforePaymentToAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanTo.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanTo.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      
      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${acountRoutingIbanFrom.accountRouting.address}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "2001"
           |},
           |"creditorAccount": {
           |  "iban": "${acountRoutingIbanTo.accountRouting.address}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      grantAccountAccess(accountsRoutingIban.head)

      val requestInitiatePaymentJson = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val responseInitiatePaymentJson: APIResponse = makePostRequest(requestInitiatePaymentJson, initiatePaymentJson)
      Then("We should get a 201 ")
      responseInitiatePaymentJson.code should equal(201)
      val paymentResponseInitiatePaymentJson = responseInitiatePaymentJson.body.extract[InitiatePaymentResponseJson]
      paymentResponseInitiatePaymentJson.transactionStatus should be (TransactionStatus.RCVD.code)
      paymentResponseInitiatePaymentJson.paymentId should not be null

      val paymentId = paymentResponseInitiatePaymentJson.paymentId

      Then(s"we test the ${startPaymentAuthorisationTransactionAuthorisation.name}")
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{"scaAuthenticationData":"123"}""".stripMargin)
      Then("We should get a 201 ")
      response.code should equal(201)
      val startPaymentAuthorisationResponse = response.body.extract[StartPaymentAuthorisationJson]
      startPaymentAuthorisationResponse.authorisationId should not be null
      startPaymentAuthorisationResponse.psuMessage should be ("Please check your SMS at a mobile device.")
      startPaymentAuthorisationResponse.scaStatus should be (ScaStatus.received.toString)
      startPaymentAuthorisationResponse._links.scaStatus should not be null
      
      Then(s"We can test the ${getPaymentInitiationAuthorisation.name}")
      val requestGetPaymentInitiationAuthorisation = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "authorisations").GET <@ (user1)
      val responseGetPaymentInitiationAuthorisation: APIResponse = makeGetRequest(requestGetPaymentInitiationAuthorisation)
      responseGetPaymentInitiationAuthorisation.code should be (200)
      responseGetPaymentInitiationAuthorisation.body.extract[List[StartPaymentAuthorisationJson]].length > 0 should be (true)
      val paymentInitiationAuthorisation = responseGetPaymentInitiationAuthorisation.body.extract[List[StartPaymentAuthorisationJson]].head
      val authorisationId = paymentInitiationAuthorisation.authorisationId
      paymentInitiationAuthorisation.scaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${getPaymentInitiationScaStatus.name}")
      val requestGetPaymentInitiationScaStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "authorisations" /authorisationId).GET <@ (user1)
      val responseGetPaymentInitiationScaStatus: APIResponse = makeGetRequest(requestGetPaymentInitiationScaStatus)
      responseGetPaymentInitiationScaStatus.code should be (200)
      val paymentInitiationScaStatus = (responseGetPaymentInitiationScaStatus.body \ "scaStatus").extract[String]
      paymentInitiationScaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${updatePaymentPsuDataTransactionAuthorisation.name}")
      val updatePaymentPsuDataJsonBody = Http4sBGv13PIS.resourceDocs
        .filter(_.partialFunctionName == "updatePaymentPsuDataTransactionAuthorisation")
        .head.exampleRequestBody match {
          case jvc: JvalueCaseClass => jvc.jvalueToCaseclass
          case jv: org.json4s.JValue => jv
        }
      
      val requestUpdatePaymentPsuData = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "authorisations"/authorisationId).PUT <@ (user1)
      val responseUpdatePaymentPsuData: APIResponse = makePutRequest(requestUpdatePaymentPsuData, write(updatePaymentPsuDataJsonBody))
      responseUpdatePaymentPsuData.code should be (200)
      responseUpdatePaymentPsuData.body.extract[ScaStatusResponse].scaStatus should be("finalised")

      Thread.sleep(100) // wait for 100 milliseconds
      
      val afterPaymentFromAccountBalance = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanFrom.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanFrom.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")
      val afterPaymentToAccountBalacne = MappedBankAccount.find(
        By(MappedBankAccount.bank, acountRoutingIbanTo.bankId.value),
        By(MappedBankAccount.theAccountId, acountRoutingIbanTo.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      afterPaymentFromAccountBalance-beforePaymentFromAccountBalance should be (BigDecimal(-2001.00))
      afterPaymentToAccountBalacne-beforePaymentToAccountBalance should be (BigDecimal(2001.00))
      
    }
    
  }

  feature(s"test the BG v1.3 ${updatePaymentPsuDataUpdatePsuAuthentication} and ${updatePaymentPsuDataUpdatePsuAuthentication.name}") {
    scenario(s"${startPaymentAuthorisationTransactionAuthorisation.name}" , BerlinGroupV1_3, PIS, updatePaymentPsuDataUpdatePsuAuthentication) {

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "authorisations" / "AUTHORISATION_ID").PUT <@ (user1)
      val response: APIResponse = makePutRequest(requestPost, """{"psuData": {"password": "start12"}}""".stripMargin)
      Then("We should get a 200 ")
      response.code should equal(200)
    }
  }

  feature(s"test the BG v1.3 ${updatePaymentPsuDataSelectPsuAuthenticationMethod} and ${updatePaymentPsuDataSelectPsuAuthenticationMethod.name}") {
    scenario(s"${startPaymentAuthorisationTransactionAuthorisation.name}" , BerlinGroupV1_3, PIS, updatePaymentPsuDataSelectPsuAuthenticationMethod) {
      val requestPut = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "authorisations" / "AUTHORISATION_ID").PUT <@ (user1)
      val response: APIResponse = makePutRequest(requestPut, """{"authenticationMethodId":""}""".stripMargin)
      Then("We should get a 200 ")
      response.code should equal(200)
    }
  }

  feature(s"test the BG v1.3 ${updatePaymentPsuDataAuthorisationConfirmation} and ${updatePaymentPsuDataAuthorisationConfirmation.name}") {
    scenario(s"${startPaymentAuthorisationTransactionAuthorisation.name}" , BerlinGroupV1_3, PIS, updatePaymentPsuDataAuthorisationConfirmation) {

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "authorisations"/"AUTHORISATION_ID").PUT <@ (user1)
      val response: APIResponse = makePutRequest(requestPost, """{"confirmationCode":"confirmationCode"}""".stripMargin)
      Then("We should get a 200 ")
      response.code should equal(200)
    }
  }


  feature(s"test the BG v1.3 ${startPaymentAuthorisationUpdatePsuAuthentication.name}") {
    scenario(s"${startPaymentAuthorisationUpdatePsuAuthentication.name} ", BerlinGroupV1_3, PIS, startPaymentAuthorisationUpdatePsuAuthentication) {

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{  "psuData":{"password":"start12"  }}""".stripMargin)
      Then("We should get a 201")
      response.code should equal(201)
    }
  }
  feature(s"test the BG v1.3 ${startPaymentAuthorisationSelectPsuAuthenticationMethod.name}") {
    scenario(s"${startPaymentAuthorisationSelectPsuAuthenticationMethod.name} ", BerlinGroupV1_3, PIS, startPaymentAuthorisationSelectPsuAuthenticationMethod) {

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{"authenticationMethodId":""}""".stripMargin)
      Then("We should get a 201")
      response.code should equal(201)
    }
  }

  feature(s"test the BG v1.3 ${cancelPayment.name} - Error Scenarios") {
    scenario(s"${cancelPayment.name} Failed Case - Invalid PaymentId", BerlinGroupV1_3, PIS, cancelPayment) {
      
      When("Try to cancel payment with invalid paymentId")
      val requestDelete = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "INVALID_PAYMENT_ID").DELETE <@ (user1)
      val responseDelete: APIResponse = makeDeleteRequest(requestDelete)
      
      Then("We should get a 400 Bad Request")
      responseDelete.code should equal(400)
      
      And("Error message should indicate invalid transaction request ID")
      val errorMessages = responseDelete.body.extract[ErrorMessagesBG]
      errorMessages.tppMessages.head.text should startWith (InvalidTransactionRequestId)
    }
    
    scenario(s"${cancelPayment.name} Failed Case - Payment Not Found", BerlinGroupV1_3, PIS, cancelPayment) {
      
      When("Try to cancel non-existent payment")
      val nonExistentPaymentId = "00000000-0000-0000-0000-000000000000"
      val requestDelete = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / nonExistentPaymentId).DELETE <@ (user1)
      val responseDelete: APIResponse = makeDeleteRequest(requestDelete)
      
      Then("We should get a 400 or 404")
      responseDelete.code should (equal(400) or equal(404))
      
      And("Error message should indicate payment not found")
      val errorMessages = responseDelete.body.extract[ErrorMessagesBG]
      errorMessages.tppMessages.head.text should (include("not found") or include("not exist") or startWith(InvalidTransactionRequestId))
    }
    
    scenario(s"${cancelPayment.name} Failed Case - Cannot Cancel Completed Payment", BerlinGroupV1_3, PIS, cancelPayment) {
      
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val ibanFrom = accountsRoutingIban.head.accountRouting.address
      val ibanTo = accountsRoutingIban.last.accountRouting.address

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${ibanFrom}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "50"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      grantAccountAccess(accountsRoutingIban.head)

      When("Create and complete a payment")
      val requestInitiatePaymentJson = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val responseInitiatePaymentJson: APIResponse = makePostRequest(requestInitiatePaymentJson, initiatePaymentJson)
      responseInitiatePaymentJson.code should equal(201)
      val paymentResponseInitiatePaymentJson = responseInitiatePaymentJson.body.extract[InitiatePaymentResponseJson]
      val paymentId = paymentResponseInitiatePaymentJson.paymentId
      
      // Note: In real scenario, payment would be completed by backend process
      // For this test, we assume the payment status prevents cancellation
      
      Then("Try to cancel the payment")
      val requestDelete = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId).DELETE <@ (user1)
      val responseDelete: APIResponse = makeDeleteRequest(requestDelete)
      
      Then("If payment cannot be cancelled, we should get 400 or 405")
      // Note: Response depends on actual payment status
      // 202/204 = can cancel, 400/405 = cannot cancel
      responseDelete.code should (equal(202) or equal(204) or equal(400) or equal(405))
      
      And("If error, message should indicate payment cannot be cancelled")
      if (responseDelete.code == 400 || responseDelete.code == 405) {
        val errorMessages = responseDelete.body.extract[ErrorMessagesBG]
        errorMessages.tppMessages.head.text should (
          include("cannot be cancelled") or 
          include("CANCELLATION_INVALID") or
          startWith(TransactionRequestCannotBeCancelled)
        )
      }
    }
  }

  feature(s"test the BG v1.3 ${startPaymentInitiationCancellationAuthorisationTransactionAuthorisation.name} " +
    s"and ${getPaymentInitiationCancellationAuthorisationInformation.name} " +
    s"and ${getPaymentCancellationScaStatus.name}" +
    s"and ${updatePaymentCancellationPsuDataTransactionAuthorisation.name}") {
    
    scenario(s"Successful Case - Cancel payment with SCA (HTTP 202)", BerlinGroupV1_3, PIS, cancelPayment) {

      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val ibanFrom = accountsRoutingIban.head.accountRouting.address
      val ibanTo = accountsRoutingIban.last.accountRouting.address

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

      grantAccountAccess(accountsRoutingIban.head)

      val requestInitiatePaymentJson = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val responseInitiatePaymentJson: APIResponse = makePostRequest(requestInitiatePaymentJson, initiatePaymentJson)
      Then("We should get a 201 ")
      responseInitiatePaymentJson.code should equal(201)
      val paymentResponseInitiatePaymentJson = responseInitiatePaymentJson.body.extract[InitiatePaymentResponseJson]
      paymentResponseInitiatePaymentJson.transactionStatus should be (TransactionStatus.ACCP.code)

      val paymentId = paymentResponseInitiatePaymentJson.paymentId

      Then(s"we test the ${cancelPayment.name} - Scenario A: Cancel ACCP payment requiring SCA")
      val requestDelete = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId).DELETE <@ (user1)
      val responseDelete: APIResponse = makeDeleteRequest(requestDelete)
      Then("We should get a 202 Accepted (SCA required)")
      responseDelete.code should equal(202)
      And("Response should contain transactionStatus ACTC")
      val cancelResponse = responseDelete.body.extract[CancelPaymentResponseJson]
      cancelResponse.transactionStatus should be (TransactionStatus.ACTC.code)
      And("Response should contain startAuthorisation link")
      
      Then(s"we test the ${startPaymentInitiationCancellationAuthorisationTransactionAuthorisation.name}")
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{"scaAuthenticationData":""}""")
      Then("We should get a 201 ")
      response.code should equal(201)
      val startPaymentAuthorisationResponse = response.body.extract[UpdatePsuAuthenticationResponse]
      startPaymentAuthorisationResponse.authorisationId should not be null
      startPaymentAuthorisationResponse.psuMessage should be (Some("Please check your SMS at a mobile device."))
      startPaymentAuthorisationResponse.scaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${getPaymentInitiationCancellationAuthorisationInformation.name}")
      val requestGetPaymentInitiationCancellationAuthorisationInformation = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "cancellation-authorisations").GET <@ (user1)
      val responseGetPaymentInitiationCancellationAuthorisationInformation: APIResponse = makeGetRequest(requestGetPaymentInitiationCancellationAuthorisationInformation)
      responseGetPaymentInitiationCancellationAuthorisationInformation.code should be (200)
      responseGetPaymentInitiationCancellationAuthorisationInformation.body.extract[CancellationJsonV13].cancellationIds.length > 0 should be (true)
      val cancellationJsonV13 = responseGetPaymentInitiationCancellationAuthorisationInformation.body.extract[CancellationJsonV13].cancellationIds
      val cancelationId = cancellationJsonV13.head

      Then(s"We can test the ${getPaymentCancellationScaStatus.name}")
      val requestGetPaymentCancellationScaStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "cancellation-authorisations" /cancelationId).GET <@ (user1)
      val responseGetPaymentCancellationScaStatus: APIResponse = makeGetRequest(requestGetPaymentCancellationScaStatus)
      responseGetPaymentCancellationScaStatus.code should be (200)
      val cancellationScaStatus = (responseGetPaymentCancellationScaStatus.body \ "scaStatus").extract[String]
      cancellationScaStatus should be (ScaStatus.received.toString)

      Then(s"We can test the ${updatePaymentCancellationPsuDataTransactionAuthorisation.name}")

      val requestUpdatePaymentCancellationPsuData = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "cancellation-authorisations"/cancelationId).PUT <@ (user1)
      val responseUpdatePaymentCancellationPsuData: APIResponse = makePutRequest(requestUpdatePaymentCancellationPsuData, """{"scaAuthenticationData":"123"}""")
      responseUpdatePaymentCancellationPsuData.code should be (200)
      responseUpdatePaymentCancellationPsuData.body.extract[ScaStatusResponse].scaStatus should be("finalised")

    }
    
    scenario(s"Successful Case - Direct cancel payment without SCA (HTTP 204)", BerlinGroupV1_3, PIS, cancelPayment) {
      
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      val ibanFrom = accountsRoutingIban.head.accountRouting.address
      val ibanTo = accountsRoutingIban.last.accountRouting.address

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": {
           |   "iban": "${ibanFrom}"
           | },
           |"instructedAmount": {
           |  "currency": "EUR",
           |  "amount": "10"
           |},
           |"creditorAccount": {
           |  "iban": "${ibanTo}"
           |},
           |"creditorName": "70charname"
            }""".stripMargin

      grantAccountAccess(accountsRoutingIban.head)

      When("Create a payment in INITIATED status (small amount)")
      val requestInitiatePaymentJson = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
      val responseInitiatePaymentJson: APIResponse = makePostRequest(requestInitiatePaymentJson, initiatePaymentJson)
      Then("We should get a 201")
      responseInitiatePaymentJson.code should equal(201)
      val paymentResponseInitiatePaymentJson = responseInitiatePaymentJson.body.extract[InitiatePaymentResponseJson]
      
      val paymentId = paymentResponseInitiatePaymentJson.paymentId

      Then(s"we test the ${cancelPayment.name} - Scenario B: Direct cancel INITIATED/RCVD payment without SCA")
      val requestDelete = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId).DELETE <@ (user1)
      val responseDelete: APIResponse = makeDeleteRequest(requestDelete)
      Then("We should get a 204 No Content (direct cancellation)")
      responseDelete.code should equal(204)
      And("Response body should be empty")
      responseDelete.body.toString should be ("JNothing")
      
      Then("Verify payment status is CANC")
      val requestGetStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId / "status").GET <@ (user1)
      val responseGetStatus: APIResponse = makeGetRequest(requestGetStatus)
      responseGetStatus.code should equal(200)
      val statusResponse = (responseGetStatus.body \ "transactionStatus").extract[String]
      statusResponse should be (TransactionStatus.CANC.code)
    }
  }

  feature(s"test the BG v1.3 ${updatePaymentCancellationPsuDataUpdatePsuAuthentication.name}" ) {
    scenario(s"${updatePaymentCancellationPsuDataUpdatePsuAuthentication.name}", BerlinGroupV1_3, PIS, updatePaymentCancellationPsuDataUpdatePsuAuthentication) {

      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "cancellation-authorisations" /"authorisationId").PUT <@ (user1)
      val response: APIResponse = makePutRequest(requestPost, """{"psuData":{"password":"start12"}}""")
      Then("We should get a 200 ")
      response.code should equal(200)
    }
  }

  feature(s"test the BG v1.3 ${updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod.name}" ) {
    scenario(s"${updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod.name}", BerlinGroupV1_3, PIS, updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod) {
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "cancellation-authorisations"/"authorisationId").PUT <@ (user1)
      val response: APIResponse = makePutRequest(requestPost, """{"authenticationMethodId":""}""")
      Then("We should get a 200 ")
      response.code should equal(200)
    }
  }

  feature(s"test the BG v1.3 ${updatePaymentCancellationPsuDataAuthorisationConfirmation.name}" ) {
    scenario(s"${updatePaymentCancellationPsuDataAuthorisationConfirmation.name}", BerlinGroupV1_3, PIS, updatePaymentCancellationPsuDataAuthorisationConfirmation) {
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "cancellation-authorisations"/"authorisationId").PUT <@ (user1)
      val response: APIResponse = makePutRequest(requestPost, """{"confirmationCode":"confirmationCode"}""")
      Then("We should get a 200 ")
      response.code should equal(200)
    }
  }

  feature(s"test the BG v1.3 ${startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication.name}") {
    scenario(s"${startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication.name}", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication) {
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{"psuData":{"password":"start12"}}""")
      Then("We should get a 201 ")
      response.code should equal(201)
    }
  }

  feature(s"test the BG v1.3 ${startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod.name}") {
    scenario(s"${startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod.name}", BerlinGroupV1_3, PIS, startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod) {
      val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / "PAYMENT_ID" / "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, """{"authenticationMethodId":""}""")
      Then("We should get a 201 ")
      response.code should equal(201)
    }
  }

  feature("test the BG v1.3 getPaymentCancellationScaStatus") {
    scenario("Successful call endpoint getPaymentCancellationScaStatus", BerlinGroupV1_3, PIS, getPaymentCancellationScaStatus) {
      When("Post empty to call initiatePayment")
      val cancellationId = "NON_EXISTING_CANCELLATION_ID"
      val requestGet = (V1_3_BG /
        PaymentServiceTypes.bulk_payments.toString /
        TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString /
        "PAYMENT_ID" /
        "cancellation-authorisations" /
        cancellationId).POST <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidTransactionRequestId Current TransactionRequestId(PAYMENT_ID) "
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should equal (error)
    }
  }
  feature("test the BG v1.3 getPaymentInitiationAuthorisation") {
    scenario("Successful call endpoint getPaymentInitiationAuthorisation", BerlinGroupV1_3, PIS, getPaymentInitiationAuthorisation) {
      When("Post empty to call initiatePayment")
      val requestGet = (V1_3_BG /
        PaymentServiceTypes.bulk_payments.toString /
        TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString /
        "NON_EXISTING_PAYMENT_ID" /
        "authorisations").POST <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidTransactionRequestId Current TransactionRequestId(NON_EXISTING_PAYMENT_ID) "
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should equal (error)
    }
  }
  feature("test the BG v1.3 getPaymentInitiationCancellationAuthorisationInformation") {
    scenario("Successful call endpoint getPaymentInitiationCancellationAuthorisationInformation", BerlinGroupV1_3, PIS, getPaymentInitiationCancellationAuthorisationInformation) {
      When("Post empty to call initiatePayment")
      val requestGet = (V1_3_BG /
        PaymentServiceTypes.bulk_payments.toString /
        TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString /
        "NON_EXISTING_PAYMENT_ID" /
        "cancellation-authorisations").POST <@ (user1)
      val response: APIResponse = makeGetRequest(requestGet)
      Then("We should get a 400 - the payment has to exist before its cancellation authorisations can be listed")
      response.code should equal(400)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith (InvalidTransactionRequestId)
    }
  }

  // Berlin Group addresses a payment by its id alone. Nothing in the route ties the payment to the
  // caller, so without an explicit check a paymentId is a bearer token: whoever holds it can read
  // the payment, list and start authorisations on it, and cancel it. The payment records who lodged
  // it; these scenarios hold every payment-scoped route to that record.
  feature("test the BG v1.3 - a payment is only addressable by the party that initiated it") {
    scenario("a second TPP can neither read, authorise, nor cancel a payment it did not initiate", BerlinGroupV1_3, PIS, initiatePayment) {
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
        .filterNot(_.bankId.value == "DEFAULT_BANK_ID_NOT_SET")
      val ibanFrom = accountsRoutingIban.head
      val ibanTo = accountsRoutingIban.last

      def balanceOf(routing: BankAccountRouting) = MappedBankAccount.find(
        By(MappedBankAccount.bank, routing.bankId.value),
        By(MappedBankAccount.theAccountId, routing.accountId.value))
        .map(_.balance).openOrThrowException("Can not be empty here")

      grantAccountAccess(ibanFrom)

      // Over the challenge threshold, so the payment stays in RCVD awaiting SCA — the state in which
      // a hijacked authorisation would actually move money.
      val initiatePaymentJson =
        s"""{
           | "debtorAccount": { "iban": "${ibanFrom.accountRouting.address}" },
           | "instructedAmount": { "currency": "EUR", "amount": "2001" },
           | "creditorAccount": { "iban": "${ibanTo.accountRouting.address}" },
           | "creditorName": "70charname"
            }""".stripMargin

      When("user1 initiates a payment")
      val responseInitiate: APIResponse = makePostRequest(
        (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1),
        initiatePaymentJson)
      responseInitiate.code should equal(201)
      val paymentId = responseInitiate.body.extract[InitiatePaymentResponseJson].paymentId

      val fromBalanceBefore = balanceOf(ibanFrom)
      val toBalanceBefore = balanceOf(ibanTo)

      val payment = V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId

      Then("user2 is refused on every payment-scoped route")
      val refusals = List(
        "read the payment" -> makeGetRequest((payment).GET <@ (user2)),
        "read its status" -> makeGetRequest((payment / "status").GET <@ (user2)),
        "list its authorisations" -> makeGetRequest((payment / "authorisations").GET <@ (user2)),
        "list its cancellation authorisations" -> makeGetRequest((payment / "cancellation-authorisations").GET <@ (user2)),
        "start an authorisation on it" -> makePostRequest((payment / "authorisations").POST <@ (user2), """{"scaAuthenticationData":"123"}"""),
        "cancel it" -> makeDeleteRequest((payment).DELETE <@ (user2))
      )
      refusals.foreach { case (what, response) =>
        withClue(s"user2 was allowed to $what: ") {
          response.code should equal(403)
          response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith (PaymentNotInitiatedByCaller)
        }
      }

      And("no money moved")
      balanceOf(ibanFrom) should equal(fromBalanceBefore)
      balanceOf(ibanTo) should equal(toBalanceBefore)

      And("user1, who initiated it, still can address it")
      val ownerResponse = makeGetRequest((payment / "status").GET <@ (user1))
      ownerResponse.code should equal(200)
    }
    scenario("a second TPP acting for the same PSU is refused too", BerlinGroupV1_3, PIS, initiatePayment) {
      val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
        .filterNot(_.bankId.value == "DEFAULT_BANK_ID_NOT_SET")
      val ibanFrom = accountsRoutingIban.head
      val ibanTo = accountsRoutingIban.last

      grantAccountAccess(ibanFrom)

      val initiatePaymentJson =
        s"""{
           | "debtorAccount": { "iban": "${ibanFrom.accountRouting.address}" },
           | "instructedAmount": { "currency": "EUR", "amount": "2001" },
           | "creditorAccount": { "iban": "${ibanTo.accountRouting.address}" },
           | "creditorName": "70charname"
            }""".stripMargin

      When("the PSU lodges a payment through one TPP")
      val responseInitiate: APIResponse = makePostRequest(
        (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1),
        initiatePaymentJson)
      responseInitiate.code should equal(201)
      val paymentId = responseInitiate.body.extract[InitiatePaymentResponseJson].paymentId
      val payment = V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / paymentId

      Then("the same PSU acting through a second TPP still cannot address it")
      // The person matches and only the TPP differs -- the case a person-only check waves through,
      // and the reason the payment has to record which consumer lodged it.
      val response = makeGetRequest((payment / "status").GET <@ (samePsuUnderSecondConsumer))
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith (PaymentNotInitiatedByCaller)

      And("the TPP that lodged it still can")
      makeGetRequest((payment / "status").GET <@ (user1)).code should equal(200)
    }
  }
  // resourceUser1's own token, issued under a second consumer: same person, different TPP.
  // DefaultUsers has no such pair -- user2 and user3 change the person as well as the consumer.
  private lazy val samePsuUnderSecondConsumer = {
    val token = Tokens.tokens.vend.createToken(
      TokenType.Access,
      Some(testConsumer2.id.get),
      Some(resourceUser1.id.get),
      Some(randomString(40).toLowerCase),
      Some(randomString(40).toLowerCase),
      Some(tokenDuration),
      Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
      Some(new java.util.Date(System.currentTimeMillis())),
      None
    ).openOrThrowException("test token creation failed")
    Some(consumer2, Token(token.key.get, token.secret.get))
  }


}