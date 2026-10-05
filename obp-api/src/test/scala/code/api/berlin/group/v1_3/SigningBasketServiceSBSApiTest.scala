/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.berlin.group.v1_3

import org.json4s._
import code.api.Constant.SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{AuthorisationJsonV13, ErrorMessagesBG, InitiatePaymentResponseJson, PostSigningBasketJsonV13, SigningBasketGetResponseJson, SigningBasketResponseJson}
import code.api.berlin.group.v1_3.model.TransactionStatus
import code.api.berlin.group.v1_3.{Http4sBGv13SigningBaskets => APIMethods_SigningBasketsApi}
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.model.TokenType
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import code.setup.APIResponse
import com.openbankproject.commons.model.User
import code.signingbaskets.{MappedSigningBasket, MappedSigningBasketPayment, SigningBasketX}
import code.token.Tokens
import code.transactionChallenge.Challenges
import code.transactionrequests.MappedTransactionRequest
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ViewId
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, PaymentServiceTypes, StrongCustomerAuthenticationStatus, TransactionRequestTypes}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import net.liftweb.util.TimeHelpers.TimeSpan
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SigningBasketServiceSBSApiTest extends BerlinGroupConsentFixtures {
  object SBS extends Tag("Signing Baskets Service (SBS)")
  object createSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.createSigningBasket))
  object getSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasket))
  object getSigningBasketStatus extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketStatus))
  object deleteSigningBasket extends Tag(nameOf(APIMethods_SigningBasketsApi.deleteSigningBasket))
  object startSigningBasketAuthorisation extends Tag(nameOf(APIMethods_SigningBasketsApi.startSigningBasketAuthorisation))
  object getSigningBasketScaStatus extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketScaStatus))
  object getSigningBasketAuthorisation extends Tag(nameOf(APIMethods_SigningBasketsApi.getSigningBasketAuthorisation))
  object updateSigningBasketPsuData extends Tag(nameOf(APIMethods_SigningBasketsApi.updateSigningBasketPsuData))

  // ───────────────────────────── fixtures ─────────────────────────────

  // Spec references below are lines of psd2-api_v1.3.16-2025-11-27.openapi.yaml ("L1234") and sections
  // of the Implementation Guidelines 1.3.16 ("IG §x"). Where the standard leaves a choice to the ASPSP the
  // scenario says so and states the choice made.

  /** The tppMessage codes the standard allows for each status of a signing basket call (L11514-11749: MessageCode400_SBS L11514, 401 L11597, 403 L11648, 404 L11680, 409 L11744). */
  private val allowedTppCodes: Map[Int, Set[String]] = Map(
    400 -> Set("FORMAT_ERROR", "PARAMETER_NOT_CONSISTENT", "PARAMETER_NOT_SUPPORTED", "SERVICE_INVALID", "RESOURCE_UNKNOWN",
      "RESOURCE_EXPIRED", "RESOURCE_BLOCKED", "TIMESTAMP_INVALID", "PERIOD_INVALID", "SCA_METHOD_UNKNOWN", "SCA_INVALID",
      "CONSENT_UNKNOWN", "REFERENCE_MIX_INVALID"),
    401 -> Set("CERTIFICATE_INVALID", "ROLE_INVALID", "CERTIFICATE_EXPIRED", "CERTIFICATE_BLOCKED", "CERTIFICATE_REVOKE",
      "CERTIFICATE_MISSING", "SIGNATURE_INVALID", "SIGNATURE_MISSING", "CORPORATE_ID_INVALID", "PSU_CREDENTIALS_INVALID",
      "CONSENT_INVALID", "CONSENT_EXPIRED", "TOKEN_UNKNOWN", "TOKEN_INVALID", "TOKEN_EXPIRED"),
    403 -> Set("CONSENT_UNKNOWN", "SERVICE_BLOCKED", "RESOURCE_UNKNOWN", "RESOURCE_EXPIRED"),
    404 -> Set("RESOURCE_UNKNOWN"),
    409 -> Set("REFERENCE_STATUS_INVALID", "STATUS_INVALID")
  )

  private def ibanAccounts = BankAccountRouting
    .findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
    .filterNot(_.bankId.value == "DEFAULT_BANK_ID_NOT_SET")

  private def balanceOf(routing: BankAccountRouting) = MappedBankAccount.find(
    By(MappedBankAccount.bank, routing.bankId.value),
    By(MappedBankAccount.theAccountId, routing.accountId.value))
    .map(_.balance).openOrThrowException("Can not be empty here")

  private def basketsUrl = V1_3_BG / "signing-baskets"
  private def basketUrl(basketId: String) = V1_3_BG / "signing-baskets" / basketId
  private def authorisationsUrl(basketId: String) = V1_3_BG / "signing-baskets" / basketId / "authorisations"
  private def authorisationUrl(basketId: String, authorisationId: String) =
    V1_3_BG / "signing-baskets" / basketId / "authorisations" / authorisationId

  /**
   * Lodges a SEPA payment as user1 and returns its id. The default amount is over the challenge
   * threshold, so the payment sits at RCVD awaiting SCA, which is the only state a basket may take
   * a payment in. A payment of 10 is booked on creation (ACCP) and can no longer be authorised by
   * anything.
   */
  private def lodgePayment(amount: String = "2001", as: Option[(Consumer, Token)] = user1, initiator: User = resourceUser1): String = {
    val ibanFrom = ibanAccounts.head
    val ibanTo = ibanAccounts.last
    Views.views.vend.systemView(ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)).foreach(view =>
      Views.views.vend.grantAccessToSystemView(ibanFrom.bankId, ibanFrom.accountId, view, initiator)
    )
    val initiatePaymentJson =
      s"""{
         | "debtorAccount": { "iban": "${ibanFrom.accountRouting.address}" },
         | "instructedAmount": { "currency": "EUR", "amount": "$amount" },
         | "creditorAccount": { "iban": "${ibanTo.accountRouting.address}" },
         | "creditorName": "TestCreditor"
         |}""".stripMargin
    val requestPost = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString).POST <@ (as)
    val response: APIResponse = makePostRequest(requestPost, initiatePaymentJson)
    withClue(s"lodging a payment of $amount: ") { response.code should equal(201) }
    response.body.extract[InitiatePaymentResponseJson].paymentId
  }

  /** A payment lodged the way a client-credentials TPP lodges one: on its own session, with no PSU in it. */
  private def lodgePaymentAsClientCredentialsTpp(): String =
    lodgePayment(as = clientCredentialsSession, initiator = pseudoUserOfTestConsumer)

  private def createRealPaymentId(): String = lodgePayment()

  /** The stored status of a payment that awaits SCA, and of one that was booked on creation. */
  private val awaitingSca = "RCVD"
  private val bookedOnCreation = "ACCP"

  private def idList(ids: List[String]): String = ids.map(id => s""""$id"""").mkString("[", ",", "]")

  private def postBasket(body: String, as: Option[(Consumer, Token)] = user1): APIResponse =
    makePostRequest(basketsUrl.POST <@ (as), body)

  private def createBasket(paymentIds: List[String], as: Option[(Consumer, Token)] = user1): String = {
    val response = postBasket(s"""{"paymentIds":${idList(paymentIds)}}""", as)
    withClue(s"creating a basket of $paymentIds: ") { response.code should equal(201) }
    response.body.extract[SigningBasketResponseJson].basketId
  }

  private def startAuthorisation(basketId: String, as: Option[(Consumer, Token)] = user1, body: String = "{}"): APIResponse =
    makePostRequest(authorisationsUrl(basketId).POST <@ (as), body)

  private def answerAuthorisation(basketId: String, authorisationId: String, as: Option[(Consumer, Token)] = user1,
                                  body: String = """{"scaAuthenticationData":"123"}"""): APIResponse =
    makePutRequest(authorisationUrl(basketId, authorisationId).PUT <@ (as), body)

  /** Everything a basket needs for its SCA to be answered: a basket of real payments, and an authorisation on it. */
  private case class StartedBasket(basketId: String, paymentIds: List[String], authorisationId: String)

  private def startedBasket(paymentCount: Int = 1): StartedBasket = {
    enableBasketAuthorisation()
    val paymentIds = List.fill(paymentCount)(lodgePayment())
    val basketId = createBasket(paymentIds)
    val started = startAuthorisation(basketId)
    started.code should equal(201)
    StartedBasket(basketId, paymentIds, (started.body \ "authorisationId").extract[String])
  }

  /** Tests that answer an SCA need a challenge whose answer is known, and an instance that lets baskets be authorised. */
  private def enableBasketAuthorisation(): Unit = {
    setPropsValues("suggested_default_sca_method" -> "DUMMY", "signing_basket_authorisation_enabled" -> "true")
  }

  // What the database says, as opposed to what an HTTP response claims.
  private def storedBasketStatus(basketId: String): Option[String] =
    SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId).toOption.map(_.basket.status)

  private def storedPaymentStatus(paymentId: String): String =
    MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, paymentId))
      .map(_.mStatus.get).openOrThrowException(s"payment $paymentId must exist")

  private def storedChallengeCount(basketId: String): Int =
    Challenges.ChallengeProvider.vend.getChallengesByBasketId(basketId).map(_.size).openOrThrowException("challenges must be readable")

  private def storedBasketCount(): Long = MappedSigningBasket.count()

  private def tppCode(response: APIResponse): String = response.body.extract[ErrorMessagesBG].tppMessages.head.code

  /** The status is as expected, the tppMessage code is the expected one, and that code is one the standard allows for that status. */
  private def expectRefusal(response: APIResponse, status: Int, code: String, what: String): Unit =
    withClue(s"$what: ") {
      response.code should equal(status)
      tppCode(response) should equal(code)
      allowedTppCodes(status) should contain(code)
    }

  // resourceUser1's own token, issued under a second consumer: same person, different TPP.
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
      createdBasket.transactionStatus should be(ConstantsBG.SigningBasketsStatus.RCVD.toString)
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
      basket.transactionStatus should be(ConstantsBG.SigningBasketsStatus.RCVD.toString)
      basket.payments.isDefined should be(true)
      basket.payments.get should contain(paymentId1)
      basket.payments.get should contain(paymentId2)

      // Each payment still awaits SCA, which Berlin Group reports as RCVD (ACCP would mean it is already booked)
      Then("Each payment in the basket should return RCVD status")
      basket.payments.get.foreach { pid =>
        val requestPaymentStatus = (V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString / pid / "status").GET <@ (user1)
        val responsePaymentStatus = makeGetRequest(requestPaymentStatus)
        responsePaymentStatus.code should be(200)
        val txStatus = (responsePaymentStatus.body \ "transactionStatus").extract[String]
        txStatus should be(TransactionStatus.RCVD.code)
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


  // ───────────────────────── the happy path, with real payments ─────────────────────────

  feature(s"BG v1.3 - $createSigningBasket, $getSigningBasket, $getSigningBasketStatus, $deleteSigningBasket, $startSigningBasketAuthorisation, $getSigningBasketAuthorisation, $getSigningBasketScaStatus, $updateSigningBasketPsuData") {
    scenario("a basket of real payments is created, read, authorised and its authorisation listed", BerlinGroupV1_3, SBS, createSigningBasket, getSigningBasket, getSigningBasketStatus, startSigningBasketAuthorisation, getSigningBasketAuthorisation, getSigningBasketScaStatus, updateSigningBasketPsuData) {
      val started = startedBasket(paymentCount = 2)

      Then(s"We test the $getSigningBasket")
      val responseGet = makeGetRequest(basketUrl(started.basketId).GET <@ (user1))
      responseGet.code should be(200)
      responseGet.body.extract[SigningBasketGetResponseJson].transactionStatus should be("RCVD") // L4497-4518

      Then(s"We test the $getSigningBasketStatus")
      val responseStatus = makeGetRequest((basketUrl(started.basketId) / "status").GET <@ (user1))
      responseStatus.code should be(200)
      (responseStatus.body \ "transactionStatus").extract[String] should be("RCVD")

      Then(s"We test the $getSigningBasketAuthorisation")
      val responseAuths = makeGetRequest(authorisationsUrl(started.basketId).GET <@ (user1))
      responseAuths.code should be(200)
      responseAuths.body.extract[AuthorisationJsonV13].authorisationIds should equal(List(started.authorisationId)) // L4827

      Then(s"We test the $getSigningBasketScaStatus")
      val responseAuthStatus = makeGetRequest(authorisationUrl(started.basketId, started.authorisationId).GET <@ (user1))
      responseAuthStatus.code should be(200)
      (responseAuthStatus.body \ "scaStatus").extract[String] should be(StrongCustomerAuthenticationStatus.received.toString)
    }
  }

  // ───────────────────────── response shape: C1, C2, C3, C12 ─────────────────────────

  feature("BG v1.3 signing baskets - response shape follows the standard") {
    scenario("C1: transactionStatus is upper case in every response that carries it (L4497-4518)", BerlinGroupV1_3, SBS, createSigningBasket, getSigningBasket, getSigningBasketStatus) {
      val response = postBasket(s"""{"paymentIds":${idList(List(lodgePayment()))}}""")
      response.code should equal(201)
      (response.body \ "transactionStatus").extract[String] should equal("RCVD")
      val basketId = response.body.extract[SigningBasketResponseJson].basketId

      (makeGetRequest(basketUrl(basketId).GET <@ (user1)).body \ "transactionStatus").extract[String] should equal("RCVD")
      (makeGetRequest((basketUrl(basketId) / "status").GET <@ (user1)).body \ "transactionStatus").extract[String] should equal("RCVD")
    }

    scenario("C12: the 201 carries Location (IG §8.1, Mandatory) and ASPSP-SCA-Approach (IG §8.1, Conditional on the approach being fixed)", BerlinGroupV1_3, SBS, createSigningBasket) {
      val response = postBasket(s"""{"paymentIds":${idList(List(lodgePayment()))}}""")
      response.code should equal(201)
      val basketId = response.body.extract[SigningBasketResponseJson].basketId
      val headers = response.headers.getOrElse(fail("the response has no headers"))
      Option(headers.get("Location")).getOrElse(fail("Location is missing")) should endWith(s"/signing-baskets/$basketId")
      Option(headers.get("ASPSP-SCA-Approach")) should not be empty
    }

    scenario("C2: _links.scaStatus of a started authorisation is a {href} object (L4801, L10752)", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      val basketId = createBasket(List(lodgePayment()))
      val response = startAuthorisation(basketId)
      response.code should equal(201)
      val authorisationId = (response.body \ "authorisationId").extract[String]
      (response.body \ "scaStatus").extract[String] should equal("received")
      withClue("ASPSP-SCA-Approach is sent when the authorisation resource is created (IG §7.1): ") {
        Option(response.headers.getOrElse(fail("the response has no headers")).get("ASPSP-SCA-Approach")) should not be empty
      }
      (response.body \ "_links" \ "scaStatus" \ "href").extract[String] should endWith(s"/signing-baskets/$basketId/authorisations/$authorisationId")
    }

    scenario("C3: the answer to an authorisation links to the basket's authorisation, not to a payment (L8828, L15675)", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val started = startedBasket()
      val response = answerAuthorisation(started.basketId, started.authorisationId)
      response.code should equal(200)
      (response.body \ "scaStatus").extract[String] should equal("finalised")
      val href = (response.body \ "_links" \ "scaStatus" \ "href").extract[String]
      href should endWith(s"/signing-baskets/${started.basketId}/authorisations/${started.authorisationId}")
      href should not include "/payments/"
    }
  }

  // ───────────────────────── request validation: C5, C7 ─────────────────────────

  feature("BG v1.3 signing baskets - requests are validated against the schema") {
    scenario("C5: an empty id list is refused, whichever list it is (L4325, L4365; body 'shall contain at least one entry' L4742)", BerlinGroupV1_3, SBS, createSigningBasket) {
      val payment = lodgePayment()
      val basketsBefore = storedBasketCount()
      List(
        """{"paymentIds":[]}""",
        """{"consentIds":[]}""",
        """{"paymentIds":[],"consentIds":[]}""",
        s"""{"paymentIds":${idList(List(payment))},"consentIds":[]}"""
      ).foreach { body =>
        expectRefusal(postBasket(body), 400, "FORMAT_ERROR", s"body $body")
      }
      withClue("a refused request leaves no basket behind: ") { storedBasketCount() should equal(basketsBefore) }
    }

    scenario("C5: the same id twice in one list is refused (the standard sets no rule; refused as a format error)", BerlinGroupV1_3, SBS, createSigningBasket) {
      val payment = lodgePayment()
      expectRefusal(postBasket(s"""{"paymentIds":${idList(List(payment, payment))}}"""), 400, "FORMAT_ERROR", "duplicate payment id")
    }

    scenario("C7: POST authorisations refuses the body variants it does not support instead of discarding them (L3653)", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      val basketId = createBasket(List(lodgePayment()))
      expectRefusal(startAuthorisation(basketId, body = """{"psuData":{"password":"secret"}}"""), 400, "SERVICE_INVALID", "updatePsuAuthentication")
      expectRefusal(startAuthorisation(basketId, body = """{"authenticationMethodId":"sms"}"""), 400, "SERVICE_INVALID", "selectPsuAuthenticationMethod")
      withClue("neither refused request minted a challenge: ") { storedChallengeCount(basketId) should equal(0) }
    }

    scenario("C7: POST authorisations accepts the two variants it supports (L3653)", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      val basketId = createBasket(List(lodgePayment()))
      startAuthorisation(basketId, body = "{}").code should equal(201)
      startAuthorisation(basketId, body = """{"scaAuthenticationData":"123"}""").code should equal(201)
    }

    scenario("C7: PUT refuses the variants it does not support, and a body matching no variant is a format error (L3867, L8250-8300)", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val started = startedBasket()
      expectRefusal(answerAuthorisation(started.basketId, started.authorisationId, body = """{"confirmationCode":"123"}"""), 400, "SERVICE_INVALID", "authorisationConfirmation")
      expectRefusal(answerAuthorisation(started.basketId, started.authorisationId, body = """{"psuData":{"password":"x"}}"""), 400, "SERVICE_INVALID", "updatePsuAuthentication")
      expectRefusal(answerAuthorisation(started.basketId, started.authorisationId, body = """{"foo":"bar"}"""), 400, "FORMAT_ERROR", "matches no variant")
      withClue("nothing was authorised: ") {
        storedBasketStatus(started.basketId) should equal(Some("RCVD"))
        started.paymentIds.foreach(storedPaymentStatus(_) should equal(awaitingSca))
      }
    }
  }

  // ───────────────────────── states and transitions: C6, C9, C10 ─────────────────────────

  feature("BG v1.3 signing baskets - a basket only moves along the transitions the standard and this ASPSP allow") {
    scenario("C9: a deleted basket cannot be authorised, and nothing it held is touched (L3399-3403)", BerlinGroupV1_3, SBS, deleteSigningBasket, startSigningBasketAuthorisation, updateSigningBasketPsuData) {
      val started = startedBasket()
      makeDeleteRequest(basketUrl(started.basketId).DELETE <@ (user1)).code should equal(204)
      storedBasketStatus(started.basketId) should equal(Some("CANC"))

      expectRefusal(startAuthorisation(started.basketId), 409, "STATUS_INVALID", "starting an authorisation on a CANC basket")
      expectRefusal(answerAuthorisation(started.basketId, started.authorisationId), 409, "STATUS_INVALID", "answering an authorisation on a CANC basket")
      withClue("the basket stayed CANC and its payment was not touched: ") {
        storedBasketStatus(started.basketId) should equal(Some("CANC"))
        started.paymentIds.foreach(storedPaymentStatus(_) should equal(awaitingSca))
      }
      withClue("deleting a deleted basket is idempotent: ") {
        makeDeleteRequest(basketUrl(started.basketId).DELETE <@ (user1)).code should equal(204)
      }
    }

    scenario("C6: a basket whose authorisation has been applied cannot be deleted or restarted (L3399-3403)", BerlinGroupV1_3, SBS, deleteSigningBasket, startSigningBasketAuthorisation, updateSigningBasketPsuData) {
      val started = startedBasket()
      answerAuthorisation(started.basketId, started.authorisationId).code should equal(200)
      storedBasketStatus(started.basketId) should equal(Some("ACTC"))

      expectRefusal(makeDeleteRequest(basketUrl(started.basketId).DELETE <@ (user1)), 409, "STATUS_INVALID", "deleting an authorised basket")
      expectRefusal(startAuthorisation(started.basketId), 409, "STATUS_INVALID", "starting another authorisation on an authorised basket")
      withClue("the basket is still ACTC, not CANC: ") { storedBasketStatus(started.basketId) should equal(Some("ACTC")) }
    }

    scenario("C6: a started but unanswered authorisation does not stop a delete (L3399-3403)", BerlinGroupV1_3, SBS, deleteSigningBasket) {
      val started = startedBasket()
      makeDeleteRequest(basketUrl(started.basketId).DELETE <@ (user1)).code should equal(204)
      storedBasketStatus(started.basketId) should equal(Some("CANC"))
    }

    scenario("C9: answering the same authorisation twice is a conflict and does not repeat anything", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val started = startedBasket()
      answerAuthorisation(started.basketId, started.authorisationId).code should equal(200)
      expectRefusal(answerAuthorisation(started.basketId, started.authorisationId), 409, "STATUS_INVALID", "the repeated answer")
      storedBasketStatus(started.basketId) should equal(Some("ACTC"))
    }

    scenario("C10: a basket with a consent member is refused at authorisation before anything changes (consent activation is not supported yet)", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      enableBasketAuthorisation()
      val consentResponse = makePostRequest((V1_3_BG / "consents").POST <@ (user1), write(bgConsentPostBody()))
      consentResponse.code should equal(201)
      val consentId = (consentResponse.body \ "consentId").extract[String]
      val payment = lodgePayment()

      val consentOnly = postBasket(s"""{"consentIds":${idList(List(consentId))}}""")
      consentOnly.code should equal(201)
      val consentOnlyBasket = consentOnly.body.extract[SigningBasketResponseJson].basketId
      val consentOnlyAuth = (startAuthorisation(consentOnlyBasket).body \ "authorisationId").extract[String]
      expectRefusal(answerAuthorisation(consentOnlyBasket, consentOnlyAuth), 400, "SERVICE_INVALID", "authorising a consent-only basket")
      storedBasketStatus(consentOnlyBasket) should equal(Some("RCVD"))

      // The consent is held by the first basket, so a mixed basket needs another one.
      val secondConsent = makePostRequest((V1_3_BG / "consents").POST <@ (user1), write(bgConsentPostBody()))
      val secondConsentId = (secondConsent.body \ "consentId").extract[String]
      val mixed = postBasket(s"""{"paymentIds":${idList(List(payment))},"consentIds":${idList(List(secondConsentId))}}""")
      mixed.code should equal(201)
      val mixedBasket = mixed.body.extract[SigningBasketResponseJson].basketId
      val mixedAuth = (startAuthorisation(mixedBasket).body \ "authorisationId").extract[String]
      expectRefusal(answerAuthorisation(mixedBasket, mixedAuth), 400, "SERVICE_INVALID", "authorising a mixed basket")
      withClue("the payment was not marked completed and the basket was not marked ACTC: ") {
        storedBasketStatus(mixedBasket) should equal(Some("RCVD"))
        storedPaymentStatus(payment) should equal(awaitingSca)
      }
    }

    // Pending until consent activation is specified; the target is recorded so it is not forgotten.
    ignore("C10 (target, execution phase): authorising a consent-only basket leaves the consent valid", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {}
  }

  // ───────────────────────── unknown resources: C4, C8, C11 ─────────────────────────

  feature("BG v1.3 signing baskets - unknown resources are refused with codes the standard allows") {
    scenario("C8/D1: an unknown basket is answered 403 RESOURCE_UNKNOWN by every operation that names one (L11648, L11680)", BerlinGroupV1_3, SBS, getSigningBasket, getSigningBasketStatus, deleteSigningBasket, getSigningBasketAuthorisation, startSigningBasketAuthorisation, getSigningBasketScaStatus, updateSigningBasketPsuData) {
      enableBasketAuthorisation()
      val unknown = UUID.randomUUID().toString
      val unknownAuthorisation = UUID.randomUUID().toString
      val challengesBefore = Challenges.ChallengeProvider.vend.getChallengesByBasketId(unknown).map(_.size).openOrThrowException("x")
      List(
        "GET basket" -> makeGetRequest(basketUrl(unknown).GET <@ (user1)),
        "GET status" -> makeGetRequest((basketUrl(unknown) / "status").GET <@ (user1)),
        "DELETE basket" -> makeDeleteRequest(basketUrl(unknown).DELETE <@ (user1)),
        "GET authorisations" -> makeGetRequest(authorisationsUrl(unknown).GET <@ (user1)),
        "POST authorisation" -> startAuthorisation(unknown),
        "GET authorisation" -> makeGetRequest(authorisationUrl(unknown, unknownAuthorisation).GET <@ (user1)),
        "PUT authorisation" -> answerAuthorisation(unknown, unknownAuthorisation)
      ).foreach { case (what, response) => expectRefusal(response, 403, "RESOURCE_UNKNOWN", what) }
      withClue("starting an authorisation on a basket that does not exist minted no challenge: ") {
        Challenges.ChallengeProvider.vend.getChallengesByBasketId(unknown).map(_.size).openOrThrowException("x") should equal(challengesBefore)
      }
    }

    scenario("C4: an authorisation id the basket does not have is 404 RESOURCE_UNKNOWN, never a 200 with a made-up scaStatus (L4521, L11680)", BerlinGroupV1_3, SBS, getSigningBasketScaStatus, updateSigningBasketPsuData) {
      val started = startedBasket()
      val other = startedBasket()
      expectRefusal(makeGetRequest(authorisationUrl(started.basketId, UUID.randomUUID().toString).GET <@ (user1)), 404, "RESOURCE_UNKNOWN", "an id nobody issued")
      expectRefusal(makeGetRequest(authorisationUrl(started.basketId, other.authorisationId).GET <@ (user1)), 404, "RESOURCE_UNKNOWN", "an id issued for another basket")
      expectRefusal(answerAuthorisation(started.basketId, other.authorisationId), 404, "RESOURCE_UNKNOWN", "answering an id issued for another basket")
    }

    scenario("C11: a refused creation uses codes from the standard's lists (L11514, L11744, IG §14.11.5)", BerlinGroupV1_3, SBS, createSigningBasket) {
      val invented = UUID.randomUUID().toString
      expectRefusal(postBasket(s"""{"paymentIds":${idList(List(invented))}}"""), 400, "RESOURCE_UNKNOWN", "invented payment id")
      expectRefusal(postBasket("""{"wrongFieldName":["x"]}"""), 400, "FORMAT_ERROR", "unknown field")
    }
  }

  // ───────────────────────── ownership: S1 ─────────────────────────

  feature("BG v1.3 signing baskets - a basket belongs to the TPP that created it") {
    scenario("S1: a second TPP is refused on every operation, and nothing about the basket changes (IG §4.11)", BerlinGroupV1_3, SBS, getSigningBasket, getSigningBasketStatus, deleteSigningBasket, getSigningBasketAuthorisation, startSigningBasketAuthorisation, getSigningBasketScaStatus, updateSigningBasketPsuData) {
      val started = startedBasket()
      val challengesBefore = storedChallengeCount(started.basketId)

      List(
        "GET basket" -> makeGetRequest(basketUrl(started.basketId).GET <@ (user2)),
        "GET status" -> makeGetRequest((basketUrl(started.basketId) / "status").GET <@ (user2)),
        "DELETE basket" -> makeDeleteRequest(basketUrl(started.basketId).DELETE <@ (user2)),
        "GET authorisations" -> makeGetRequest(authorisationsUrl(started.basketId).GET <@ (user2)),
        "POST authorisation" -> startAuthorisation(started.basketId, as = user2),
        "GET authorisation" -> makeGetRequest(authorisationUrl(started.basketId, started.authorisationId).GET <@ (user2)),
        "PUT authorisation" -> answerAuthorisation(started.basketId, started.authorisationId, as = user2)
      ).foreach { case (what, response) => expectRefusal(response, 403, "RESOURCE_UNKNOWN", s"user2 tried to $what") }

      withClue("the basket, its payments and its challenges are as they were: ") {
        storedBasketStatus(started.basketId) should equal(Some("RCVD"))
        started.paymentIds.foreach(storedPaymentStatus(_) should equal(awaitingSca))
        storedChallengeCount(started.basketId) should equal(challengesBefore)
      }
      And("the TPP that created it still can")
      makeGetRequest((basketUrl(started.basketId) / "status").GET <@ (user1)).code should equal(200)
    }

    scenario("S1: the same PSU acting through a second TPP is refused too (IG §4.11)", BerlinGroupV1_3, SBS, getSigningBasketStatus, deleteSigningBasket) {
      val basketId = createBasket(List(lodgePayment()))
      expectRefusal(makeGetRequest((basketUrl(basketId) / "status").GET <@ (samePsuUnderSecondConsumer)), 403, "RESOURCE_UNKNOWN", "status read")
      expectRefusal(makeDeleteRequest(basketUrl(basketId).DELETE <@ (samePsuUnderSecondConsumer)), 403, "RESOURCE_UNKNOWN", "delete")
      storedBasketStatus(basketId) should equal(Some("RCVD"))
    }

    scenario("S1: a basket created before ownership was recorded is quarantined, for everybody", BerlinGroupV1_3, SBS, getSigningBasket, deleteSigningBasket, startSigningBasketAuthorisation) {
      // The shape every basket had before ownership existed: a status, members, and no consumer or PSU.
      val payment = lodgePayment()
      val legacy = MappedSigningBasket.create.Status("RCVD").saveMe()
      MappedSigningBasketPayment.create.BasketId(legacy.basketId).PaymentId(payment).saveMe()

      List(
        "GET basket" -> makeGetRequest(basketUrl(legacy.basketId).GET <@ (user1)),
        "DELETE basket" -> makeDeleteRequest(basketUrl(legacy.basketId).DELETE <@ (user1)),
        "POST authorisation" -> startAuthorisation(legacy.basketId)
      ).foreach { case (what, response) => expectRefusal(response, 403, "RESOURCE_UNKNOWN", s"the payment's own TPP tried to $what on a legacy basket") }
      withClue("the legacy basket is kept as it was, for audit: ") {
        storedBasketStatus(legacy.basketId) should equal(Some("RCVD"))
        storedChallengeCount(legacy.basketId) should equal(0)
      }
    }
  }

  // ───────────────────────── whose challenge: the PSU, not the calling TPP ─────────────────────────

  feature("BG v1.3 signing baskets - an authorisation is minted for the PSU, which is where the one-time password goes") {
    scenario("S1: a client-credentials TPP naming the PSU in PSU-ID gets a challenge for that PSU, and the basket binds to them", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val basketId = createBasket(List(lodgePaymentAsClientCredentialsTpp()), as = clientCredentialsSession)
      val response = makePostRequest(authorisationsUrl(basketId).POST <@ (clientCredentialsSession), "{}", List(("PSU-ID", resourceUser1.name)))
      response.code should equal(201)
      val authorisationId = (response.body \ "authorisationId").extract[String]
      Challenges.ChallengeProvider.vend.getChallenge(authorisationId).openOrThrowException("challenge").expectedUserId should equal(resourceUser1.userId)
      SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId).map(_.basket.psuUserId) should equal(net.liftweb.common.Full(Some(resourceUser1.userId)))
    }

    scenario("S1: a client-credentials TPP that names nobody gets no challenge (L11597, IG §14.11 PSU_CREDENTIALS_INVALID)", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      val basketId = createBasket(List(lodgePaymentAsClientCredentialsTpp()), as = clientCredentialsSession)
      expectRefusal(startAuthorisation(basketId, as = clientCredentialsSession), 401, "PSU_CREDENTIALS_INVALID", "no PSU anywhere")
      expectRefusal(makePostRequest(authorisationsUrl(basketId).POST <@ (clientCredentialsSession), "{}", List(("PSU-ID", "nobody-by-this-name"))), 401, "PSU_CREDENTIALS_INVALID", "an unknown PSU-ID")
      storedChallengeCount(basketId) should equal(0)
    }

    scenario("S1: once a PSU is bound, a PSU-ID naming someone else is refused like any other refusal to address the basket", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val basketId = createBasket(List(lodgePaymentAsClientCredentialsTpp()), as = clientCredentialsSession)
      makePostRequest(authorisationsUrl(basketId).POST <@ (clientCredentialsSession), "{}", List(("PSU-ID", resourceUser1.name))).code should equal(201)
      val challengesBefore = storedChallengeCount(basketId)
      expectRefusal(makePostRequest(authorisationsUrl(basketId).POST <@ (clientCredentialsSession), "{}", List(("PSU-ID", resourceUser2.name))), 403, "RESOURCE_UNKNOWN", "another PSU")
      storedChallengeCount(basketId) should equal(challengesBefore)
    }
  }

  // ───────────────────────── members: S5, D8 ─────────────────────────

  feature("BG v1.3 signing baskets - a basket only takes members the caller may authorise") {
    scenario("S5: an id that names no payment is refused, and no basket is left behind", BerlinGroupV1_3, SBS, createSigningBasket) {
      val basketsBefore = storedBasketCount()
      expectRefusal(postBasket(s"""{"paymentIds":${idList(List("123qwert456789", "12345qwert7899"))}}"""), 400, "RESOURCE_UNKNOWN", "invented payment ids")
      storedBasketCount() should equal(basketsBefore)
    }

    scenario("S5: a payment another TPP lodged looks exactly like one that does not exist", BerlinGroupV1_3, SBS, createSigningBasket) {
      val payment = lodgePayment() // lodged by user1
      val basketsBefore = storedBasketCount()
      val foreign = postBasket(s"""{"paymentIds":${idList(List(payment))}}""", as = user2)
      val invented = postBasket(s"""{"paymentIds":${idList(List(UUID.randomUUID().toString))}}""", as = user2)
      expectRefusal(foreign, 400, "RESOURCE_UNKNOWN", "another TPP's payment")
      expectRefusal(invented, 400, "RESOURCE_UNKNOWN", "an invented payment")
      storedBasketCount() should equal(basketsBefore)
    }

    scenario("D8: a payment that is already booked cannot be put in a basket (IG §14.11.5, L11748)", BerlinGroupV1_3, SBS, createSigningBasket) {
      val booked = lodgePayment(amount = "10") // under the threshold: booked on creation
      storedPaymentStatus(booked) should equal(bookedOnCreation)
      expectRefusal(postBasket(s"""{"paymentIds":${idList(List(booked))}}"""), 409, "REFERENCE_STATUS_INVALID", "a booked payment")
    }

    scenario("D8: a payment sits in one active basket at a time, and is released when that basket is cancelled", BerlinGroupV1_3, SBS, createSigningBasket, deleteSigningBasket) {
      val payment = lodgePayment()
      val first = createBasket(List(payment))
      val basketsBefore = storedBasketCount()
      expectRefusal(postBasket(s"""{"paymentIds":${idList(List(payment))}}"""), 409, "REFERENCE_STATUS_INVALID", "the same payment in a second active basket")
      withClue("the refused request left no basket behind: ") { storedBasketCount() should equal(basketsBefore) }

      makeDeleteRequest(basketUrl(first).DELETE <@ (user1)).code should equal(204)
      postBasket(s"""{"paymentIds":${idList(List(payment))}}""").code should equal(201)
    }
  }

  feature("BG v1.3 signing baskets - a consent joins a basket only if its TPP is the basket's and it is still to be authorised") {
    scenario("D8: an unauthorised consent of the same TPP is admitted; an authorised one, or another TPP's, is not", BerlinGroupV1_3, SBS, createSigningBasket) {
      val own = createUnclaimedBerlinGroupConsent().consentId
      postBasket(s"""{"consentIds":${idList(List(own))}}""").code should equal(201)

      val authorised = createUnclaimedBerlinGroupConsent().consentId
      code.consent.Consents.consentProvider.vend.updateConsentStatus(authorised, code.consent.ConsentStatus.valid)
      expectRefusal(postBasket(s"""{"consentIds":${idList(List(authorised))}}"""), 409, "REFERENCE_STATUS_INVALID", "an authorised consent")

      val anotherTpp = createUnclaimedBerlinGroupConsent().consentId
      expectRefusal(postBasket(s"""{"consentIds":${idList(List(anotherTpp))}}""", as = user2), 400, "RESOURCE_UNKNOWN", "another TPP's consent")
      expectRefusal(postBasket(s"""{"consentIds":${idList(List(UUID.randomUUID().toString))}}"""), 400, "RESOURCE_UNKNOWN", "a consent nobody created")
    }
  }

  // ───────────────────────── challenge binding and ordering: S3 ─────────────────────────

  feature("BG v1.3 signing baskets - an authorisation can only be answered through the basket it was issued for") {
    scenario("S3: a challenge that was finalised for one basket cannot be replayed to execute another (SB PUT order)", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val first = startedBasket()
      val second = startedBasket()
      answerAuthorisation(first.basketId, first.authorisationId).code should equal(200) // finalises first's challenge

      // The challenge already answered is presented, with a wrong answer, against the other basket.
      val replay = answerAuthorisation(second.basketId, first.authorisationId, body = """{"scaAuthenticationData":"wrong"}""")
      replay.code should be >= 400
      withClue("the second basket and its payment are untouched, whatever the response said: ") {
        storedBasketStatus(second.basketId) should equal(Some("RCVD"))
        second.paymentIds.foreach(storedPaymentStatus(_) should equal(awaitingSca))
      }
    }

    scenario("S3: a wrong answer is the standard's incorrect-OTP refusal and changes nothing (IG §14.11 PSU_CREDENTIALS_INVALID, L11597)", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val started = startedBasket()
      expectRefusal(
        answerAuthorisation(started.basketId, started.authorisationId, body = """{"scaAuthenticationData":"wrong"}"""),
        401, "PSU_CREDENTIALS_INVALID", "a wrong one-time password")
      storedBasketStatus(started.basketId) should equal(Some("RCVD"))
      started.paymentIds.foreach(storedPaymentStatus(_) should equal(awaitingSca))
      withClue("the authorisation can still be answered correctly: ") {
        answerAuthorisation(started.basketId, started.authorisationId).code should equal(200)
      }
    }

    scenario("S3: a client-credentials TPP relays the PSU's answer, and it is checked as the PSU the challenge names", BerlinGroupV1_3, SBS, startSigningBasketAuthorisation, updateSigningBasketPsuData) {
      enableBasketAuthorisation()
      val basketId = createBasket(List(lodgePaymentAsClientCredentialsTpp()), as = clientCredentialsSession)
      val started = makePostRequest(authorisationsUrl(basketId).POST <@ (clientCredentialsSession), "{}", List(("PSU-ID", resourceUser1.name)))
      started.code should equal(201)
      val authorisationId = (started.body \ "authorisationId").extract[String]
      answerAuthorisation(basketId, authorisationId, as = clientCredentialsSession).code should equal(200)
      storedBasketStatus(basketId) should equal(Some("ACTC"))
    }

    scenario("S2: the same correct answer sent twice at once is accepted once and refused once (contract 3.7)", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      import scala.concurrent.ExecutionContext.Implicits.global
      (1 to 5).foreach { round =>
        val started = startedBasket()
        val answers = (1 to 2).map(_ => Future(answerAuthorisation(started.basketId, started.authorisationId)))
        val codes = Await.result(Future.sequence(answers), 60.seconds).map(_.code).sorted
        withClue(s"round $round: ") {
          codes should equal(List(200, 409))
          storedBasketStatus(started.basketId) should equal(Some("ACTC"))
        }
      }
    }

    scenario("D9: a basket cannot be authorised unless the instance enables it, and nothing changes while it is not", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY") // signing_basket_authorisation_enabled is left at its default
      val payment = lodgePayment()
      val basketId = createBasket(List(payment))
      val authorisationId = (startAuthorisation(basketId).body \ "authorisationId").extract[String]
      expectRefusal(answerAuthorisation(basketId, authorisationId), 403, "SERVICE_BLOCKED", "an instance that has not enabled it")
      storedBasketStatus(basketId) should equal(Some("RCVD"))
      storedPaymentStatus(payment) should equal(awaitingSca)
      withClue("the answer was not consumed: ") {
        Challenges.ChallengeProvider.vend.getChallenge(authorisationId).map(_.successful) should equal(net.liftweb.common.Full(false))
      }
      setPropsValues("signing_basket_authorisation_enabled" -> "true")
      answerAuthorisation(basketId, authorisationId).code should equal(200)
    }
  }

  // ───────────────────────── execution: S4 (the next phase, recorded here so it is not forgotten) ─────────────────────────

  feature("BG v1.3 signing baskets - a payment the basket reports as authorised is actually booked") {
    // Ignored until payment execution is reworked (the next phase). Measured on the baseline: the final answer
    // returns 200, the payment is stored COMPLETED and the basket ACTC, and neither account moves, even after
    // eight seconds. The booking is started without being awaited and its outcome is never read.
    ignore("S4 (target, execution phase): after the final answer the debtor account is debited and the creditor account credited", BerlinGroupV1_3, SBS, updateSigningBasketPsuData) {
      val ibanFrom = ibanAccounts.head
      val ibanTo = ibanAccounts.last
      val started = startedBasket()
      val fromBefore = balanceOf(ibanFrom)
      val toBefore = balanceOf(ibanTo)
      answerAuthorisation(started.basketId, started.authorisationId).code should equal(200)
      val deadline = System.currentTimeMillis() + 8000
      while (System.currentTimeMillis() < deadline && balanceOf(ibanTo) == toBefore) Thread.sleep(250)
      withClue(s"payment status ${storedPaymentStatus(started.paymentIds.head)}, basket ${storedBasketStatus(started.basketId)}: ") {
        balanceOf(ibanFrom) should equal(fromBefore - 2001)
        balanceOf(ibanTo) should equal(toBefore + 2001)
      }
    }
  }

  // ───────────────────────── concurrency ─────────────────────────

  feature("BG v1.3 signing baskets - a delete racing the final answer has exactly one winner") {
    scenario("S2: PUT and DELETE at the same time never both succeed", BerlinGroupV1_3, SBS, deleteSigningBasket, updateSigningBasketPsuData) {
      import scala.concurrent.ExecutionContext.Implicits.global
      (1 to 5).foreach { round =>
        val started = startedBasket()
        val put = Future(answerAuthorisation(started.basketId, started.authorisationId))
        val delete = Future(makeDeleteRequest(basketUrl(started.basketId).DELETE <@ (user1)))
        val (putResponse, deleteResponse) = (Await.result(put, 60.seconds), Await.result(delete, 60.seconds))
        withClue(s"round $round (put ${putResponse.code}, delete ${deleteResponse.code}): ") {
          (putResponse.code == 200 && deleteResponse.code == 204) should be(false)
          storedBasketStatus(started.basketId) match {
            case Some("CANC") => started.paymentIds.foreach(storedPaymentStatus(_) should equal(awaitingSca))
            case Some("ACTC") => deleteResponse.code should equal(409)
            case other => fail(s"the basket ended in $other")
          }
        }
      }
    }
  }
}
