package code.api.berlin.group.v1_3

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ErrorMessagesBG, PostSigningBasketJsonV13, SigningBasketGetResponseJson, SigningBasketResponseJson}
import code.api.builder.SigningBasketsApi.APIMethods_SigningBasketsApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class SigningBasketServiceSBSApiTest extends BerlinGroupServerSetupV1_3 with DefaultUsers {
  object SBS extends Tag("Signing Baskets Service (SBS)")
  object createSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.createSigningBasket))
  object getSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasket))
  object getSigningBasketStatus extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketStatus))

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
      val error = s"$UserNotLoggedIn"
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

  // TODO Add check that paymentId is an existing transaction request
  feature(s"test the BG v1.3 -${createSigningBasket.name}") {
    scenario("Failed Case - Successful", BerlinGroupV1_3, SBS, createSigningBasket) {
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
    }
  }


  feature(s"test the BG v1.3 - ${getSigningBasket.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, getSigningBasket) {
      val requestGet = (V1_3_BG / "signing-baskets" / "basketId").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401 ")
      responseGet.code should equal(401)
      val error = s"$UserNotLoggedIn"
      And("error should be " + error)
      responseGet.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }

  feature(s"test the BG v1.3 - ${getSigningBasketStatus.name}") {
    scenario("Failed Case - Unauthenticated Access", BerlinGroupV1_3, SBS, getSigningBasketStatus) {
      val requestGet = (V1_3_BG / "signing-baskets" / "basketId" / "status").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401 ")
      responseGet.code should equal(401)
      val error = s"$UserNotLoggedIn"
      And("error should be " + error)
      responseGet.body.extract[ErrorMessagesBG].tppMessages.head.text should startWith(error)
    }
  }


  feature(s"BG v1.3 - $createSigningBasket, $getSigningBasket and $getSigningBasketStatus") {
    scenario("Authentication User, test succeed", BerlinGroupV1_3, SBS, createSigningBasket, getSigningBasket, getSigningBasketStatus) {
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

      Then(s"We test the $getSigningBasket")
      val requestGet = (V1_3_BG / "signing-baskets" / basketId).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      responseGet.code should be(200)
      responseGet.body.extract[SigningBasketGetResponseJson].transactionStatus should be(ConstantsBG.SigningBasketsStatus.RCVD.toString.toLowerCase())

      Then(s"We test the $getSigningBasketStatus")
      val requestGetStatus = (V1_3_BG / "signing-baskets" / basketId / "status").GET <@ (user1)
      val responseGetStatus = makeGetRequest(requestGetStatus)
      responseGetStatus.code should be(200)
      responseGetStatus.body.extract[SigningBasketGetResponseJson].transactionStatus should be(ConstantsBG.SigningBasketsStatus.RCVD.toString.toLowerCase())
    }
  }


}