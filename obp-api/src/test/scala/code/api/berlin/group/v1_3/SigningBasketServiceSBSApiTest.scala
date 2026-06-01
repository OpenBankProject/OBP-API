package code.api.berlin.group.v1_3

import code.api.Constant.SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{AuthorisationJsonV13, ErrorMessagesBG, InitiatePaymentResponseJson, PostSigningBasketJsonV13, ScaStatusJsonV13, SigningBasketGetResponseJson, SigningBasketResponseJson, StartPaymentAuthorisationJson}
import code.api.berlin.group.v1_3.model.TransactionStatus
import code.api.berlin.group.v1_3.{Http4sBGv13SigningBaskets => APIMethods_SigningBasketsApi}
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.model.dataAccess.BankAccountRouting
import code.setup.{APIResponse, DefaultUsers}
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ViewId
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, PaymentServiceTypes, StrongCustomerAuthenticationStatus, TransactionRequestTypes}
import net.liftweb.mapper.By
import org.scalatest.Tag

class SigningBasketServiceSBSApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {
  object SBS extends Tag("Signing Baskets Service (SBS)")
  object createSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.createSigningBasket))
  object getSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasket))
  object getSigningBasketStatus extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketStatus))
  object deleteSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.deleteSigningBasket))
  object startSigningBasketAuthorisation extends Tag(nameOf(APIMethods_SigningBasketsApi.startSigningBasketAuthorisation))
  object getSigningBasketScaStatus extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketScaStatus))
  object getSigningBasketAuthorisation extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketAuthorisation))
  object updateSigningBasketPsuData extends Tag(nameOf(APIMethods_SigningBasketsApi.updateSigningBasketPsuData))

  // Helper: create a real SEPA payment via BG PIS API and return its paymentId
  private def createRealPaymentId(): String = {
    val accountsRoutingIban = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
    val ibanFrom = accountsRoutingIban.head
    val ibanTo = accountsRoutingIban.last
    Views.views.vend.systemView(ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)).foreach(view =>
      Views.views.vend.grantAccessToSystemView(ibanFrom.bankId, ibanFrom.accountId, view, resourceUser1)
    )
    val initiatePaymentJson =
      s"""{
         | "debtorAccount": { "iban": "${ibanFrom.accountRouting.address}" },
         | "instructedAmount": { "currency": "EUR", "amount": "10" },
         | "creditorAccount": { "iban": "${ibanTo.accountRouting.address}" },
         | "creditorName": "TestCreditor"
         |}""".stripMargin
    val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (user1)
    val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
    response.code should equal(201)
    val payment = response.body.extract[InitiatePaymentResponseJson]
    payment.transactionStatus should be(TransactionStatus.ACCP.code)
    payment.paymentId
  }

  feature(s"test the BG v1.3 - ${createSigningBasket.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, createSigningBasket) {
      val postJson =
        s"""{
           |  "consentIds": [
           |    "123qwert456789",
           |    "12345qwert7899"
           |  ]
           |}""".stripMargin

      val requestPost = (V1_3_BG / "signing-baskets").POST
      val response: APIResponse = makePostRequest(requestPost, postJson)
      Then("We should get a 401 ")
      response.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 -${createSigningBasket.name}") {
    scenario("Failed Case - Wrong Json format Body", BerlinGroupV1_3, SBS, createSigningBasket) {
      val wrongFieldNameJson =
        s"""{
           |  "wrongFieldName": [
           |    "123qwert456789",
           |    "12345qwert7899"
           |  ]
           |}""".stripMargin

      val requestPost = (V1_3_BG / "signing-baskets").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, wrongFieldNameJson)
      Then("We should get a 400 ")
      response.code should equal(400)
      val error = s"$InvalidJsonFormat The Json body should be the $PostSigningBasketJsonV13 "
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith (error)
    }
   }

  feature(s"test the BG v1.3 -${createSigningBasket.name}") {
    scenario("Success Case - 201 with real paymentId and response body validation", BerlinGroupV1_3, SBS, createSigningBasket) {
      val realPaymentId = createRealPaymentId()
      val postJson =
        s"""{
           |  "paymentIds": [
           |    "${realPaymentId}"
           |  ]
           |}""".stripMargin

      val requestPost = (V1_3_BG / "signing-baskets").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, postJson)
      Then("We should get a 201")
      response.code should equal(201)
      val createdBasket = response.body.extract[SigningBasketResponseJson]
      createdBasket.basketId should not be empty
      createdBasket.transactionStatus should be(ConstantsBG.SigningBasketsStatus.RCVD.toString.toLowerCase())
      createdBasket._links.self.href should not be empty
      createdBasket._links.status.href should not be empty
      createdBasket._links.startAuthorisation.href should not be empty
    }
  }


  feature(s"test the BG v1.3 - ${getSigningBasket.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, getSigningBasket) {
      val requestGet = (V1_3_BG / "signing-baskets" / "basketId").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401 ")
      responseGet.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      responseGet.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
    scenario("Success Case - 200 with multiple real paymentIds and payment status validation", BerlinGroupV1_3, SBS, getSigningBasket) {
      // Create two real payments, then create a basket referencing both
      val paymentId1 = createRealPaymentId()
      val paymentId2 = createRealPaymentId()
      val postJson =
        s"""{
           |  "paymentIds": [
           |    "${paymentId1}",
           |    "${paymentId2}"
           |  ]
           |}""".stripMargin
      val requestPost = (V1_3_BG / "signing-baskets").POST <@ (user1)
      val responsePost: APIResponse = makePostRequest(requestPost, postJson)
      responsePost.code should equal(201)
      val basketId = responsePost.body.extract[SigningBasketResponseJson].basketId

      // Verify basket GET returns correct data including both real paymentIds
      val requestGet = (V1_3_BG / "signing-baskets" / basketId).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should be(200)
      val basket = responseGet.body.extract[SigningBasketGetResponseJson]
      basket.transactionStatus should be(ConstantsBG.SigningBasketsStatus.RCVD.toString.toLowerCase())
      basket.payments.isDefined should be(true)
      basket.payments.get should contain(paymentId1)
      basket.payments.get should contain(paymentId2)

      // Verify each paymentId in the basket has ACCP status
      Then("Each payment in the basket should return ACCP status")
      basket.payments.get.foreach { pid =>
        val requestPaymentStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / pid / "status").GET <@ (user1)
        val responsePaymentStatus = makeGetRequest(requestPaymentStatus)
        responsePaymentStatus.code should be(200)
        val txStatus = (responsePaymentStatus.body \ "transactionStatus").extract[String]
        txStatus should be(TransactionStatus.ACCP.code)
      }
    }
  }

  feature(s"test the BG v1.3 - ${getSigningBasketStatus.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, getSigningBasketStatus) {
      val requestGet = (V1_3_BG / "signing-baskets" / "basketId" / "status").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401 ")
      responseGet.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      responseGet.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 - ${deleteSigningBasket.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, deleteSigningBasket) {
      val request = (V1_3_BG / "signing-baskets" / "basketId").DELETE
      val response = makeDeleteRequest(request)
      Then("We should get a 401 ")
      response.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 - ${startSigningBasketAuthorisation.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      val postJson = s"""{}""".stripMargin
      val request = (V1_3_BG / "signing-baskets" / "basketId" / "authorisations").POST
      val response = makePostRequest(request, postJson)
      Then("We should get a 401 ")
      response.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 - ${getSigningBasketScaStatus.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, getSigningBasketScaStatus) {
      val requestGet = (V1_3_BG / "signing-baskets" / "basketId" / "authorisations" / "authorisationId").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401 ")
      responseGet.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      responseGet.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 - ${getSigningBasketAuthorisation.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, getSigningBasketAuthorisation) {
      val requestGet = (V1_3_BG / "signing-baskets" / "basketId" / "authorisations").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401 ")
      responseGet.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      responseGet.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 - ${updateSigningBasketPsuData.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val putJson = s"""{"scaAuthenticationData":"123"}""".stripMargin
      val request = (V1_3_BG / "signing-baskets" / "basketId" / "authorisations" / "authorisationId").PUT
      val response = makePutRequest(request, putJson)
      Then("We should get a 401 ")
      response.code should equal(401)
      val error = s"$AuthenticatedUserIsRequired"
      And("error should be " + error)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }


  feature(s"BG v1.3 - $createSigningBasket, $getSigningBasket, $getSigningBasketStatus, $deleteSigningBasket, $startSigningBasketAuthorisation, $getSigningBasketAuthorisation, $updateSigningBasketPsuData") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, SBS, createSigningBasket, getSigningBasket, getSigningBasketStatus, deleteSigningBasket, startSigningBasketAuthorisation, getSigningBasketAuthorisation, updateSigningBasketPsuData) {
      // Create Signing Basket
      val postJson =
        s"""{
           |  "paymentIds": [
           |    "123qwert456789",
           |    "12345qwert7899"
           |  ]
           |}""".stripMargin

      val requestPost = (V1_3_BG / "signing-baskets").POST <@ (user1)
      val response: APIResponse = makePostRequest(requestPost, postJson)
      Then("We should get a 201 ")
      response.code should equal(201)

      val basketId = response.body.extract[SigningBasketResponseJson].basketId

      // Get Signing Basket
      Then(s"We test the $getSigningBasket")
      val requestGet = (V1_3_BG / "signing-baskets" / basketId).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      responseGet.code should be(200)
      responseGet.body.extract[SigningBasketGetResponseJson].transactionStatus should
        be(ConstantsBG.SigningBasketsStatus.RCVD.toString.toLowerCase())

      // Get Signing Basket Status
      Then(s"We test the $getSigningBasketStatus")
      val requestGetStatus = (V1_3_BG / "signing-baskets" / basketId / "status").GET <@ (user1)
      var responseGetStatus = makeGetRequest(requestGetStatus)
      responseGetStatus.code should be(200)
      responseGetStatus.body.extract[SigningBasketGetResponseJson].transactionStatus should
        be(ConstantsBG.SigningBasketsStatus.RCVD.toString.toLowerCase())

      // Delete Signing Basket
      val requestDelete = (V1_3_BG / "signing-baskets" / basketId).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should be(204)

      responseGetStatus = makeGetRequest(requestGetStatus)
      responseGetStatus.code should be(200)
      responseGetStatus.body.extract[SigningBasketGetResponseJson].transactionStatus should
        be(ConstantsBG.SigningBasketsStatus.CANC.toString.toLowerCase())

      // Start Signing Basket Auth Flow
      val postJsonAuth = s"""{}""".stripMargin
      val requestAuth = (V1_3_BG / "signing-baskets" / basketId / "authorisations").POST <@ (user1)
      val responseAuth = makePostRequest(requestAuth, postJsonAuth)
      Then("We should get a 201 ")
      responseAuth.code should equal(201)
      responseAuth.body.extract[StartPaymentAuthorisationJson].scaStatus should
        be(StrongCustomerAuthenticationStatus.received.toString)
      val authorisationId = responseAuth.body.extract[StartPaymentAuthorisationJson].authorisationId

      // Get Signing Basket Auth Flow Status
      val requestAuthStatus = (V1_3_BG / "signing-baskets" / basketId / "authorisations" / authorisationId).GET <@ (user1)
      val responseAuthStatus = makeGetRequest(requestAuthStatus)
      Then("We should get a 200 ")
      responseAuthStatus.code should equal(200)
      responseAuthStatus.body.extract[ScaStatusJsonV13].scaStatus should
        be(responseAuth.body.extract[StartPaymentAuthorisationJson].scaStatus)

      // Get Signing Basket Authorisations
      val requestGetAuths = (V1_3_BG / "signing-baskets" / "basketId" / "authorisations").GET <@ (user1)
      val responseGetAuths = makeGetRequest(requestGetAuths)
      Then("We should get a 200 ")
      responseGetAuths.code should equal(200)
      responseGetAuths.body.extract[AuthorisationJsonV13]

      // Failed due to unexisting paymentIds
      val putJson = s"""{"scaAuthenticationData":"123"}""".stripMargin
      val requestPut = (V1_3_BG / "signing-baskets" / basketId / "authorisations" / authorisationId).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, putJson)
      val error = s"$InvalidConnectorResponse"
      And("error should be " + error)
      responsePut.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }


}