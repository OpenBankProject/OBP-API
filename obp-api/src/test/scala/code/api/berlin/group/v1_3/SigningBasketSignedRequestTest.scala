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

import code.api.Constant.SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
import code.api.berlin.group.signing.{PSD2RequestSigner, PSD2SigningTestSupport}
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{ErrorMessagesBG, InitiatePaymentResponseJson, SigningBasketResponseJson}
import code.api.util.APIUtil.OAuth._
import code.model.dataAccess.BankAccountRouting
import code.regulatedentities.RegulatedEntityX
import code.regulatedentities.attribute.RegulatedEntityAttributeX
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import code.setup.{APIResponse, DefaultUsers, OBPReq}
import code.views.Views
import com.openbankproject.commons.model.{RegulatedEntityId, ViewId}
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, PaymentServiceTypes, TransactionRequestTypes}
import net.liftweb.mapper.By
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Signing basket requests with the Berlin Group request signature enforced.
 *
 * The test setup runs with berlin_group_mandatory_headers empty and no certificate check, so nothing
 * else exercises the signature, the mandatory headers or the PSP role on the signing basket routes.
 * Here the TPP's certificate is registered as a regulated entity, as it would be at an ASPSP, and every
 * call is signed.
 */
class SigningBasketSignedRequestTest extends BerlinGroupServerSetupV1_3 with PSD2SigningTestSupport with DefaultUsers {
  object SBS extends Tag("Signing Baskets Service (SBS)")

  override protected def tppCommonName: String = "Signing Basket Test TPP"

  private val mandatoryHeaders = "Date,Digest,PSU-Device-ID,PSU-Device-Name,PSU-IP-Address,Signature,TPP-Signature-Certificate,X-Request-ID"

  private def enforceSignatures(): Unit =
    setPropsValues(
      "berlin_group_mandatory_headers" -> mandatoryHeaders,
      "requirePsd2Certificates" -> "ONLINE",
      // The generated certificate is self-signed, so it cannot pass chain validation.
      "bypass_tpp_signature_validation" -> "true",
      "suggested_default_sca_method" -> "DUMMY"
    )

  /** Registers the TPP's certificate as a regulated entity that holds the given PSD2 roles. */
  private def registerTpp(roles: String): Unit = {
    val certificate = getCertificateData.getOrElse(fail("no test certificate"))
    val entity = RegulatedEntityX.regulatedEntityProvider.vend.createRegulatedEntity(
      Some("test-ca"), Some(certificate.certificatePem), Some(tppCommonName), Some(s"PSDDE-TEST-${certificate.serialNumber}"),
      Some("PSD_PI"), Some("Test Street 1"), Some("Munich"), Some("80331"), Some("DE"), Some("https://tpp.example.com"), Some(roles)
    ).openOrThrowException("the regulated entity must be created")
    List("CERTIFICATE_SERIAL_NUMBER" -> certificate.serialNumber.toString, "CERTIFICATE_CA_NAME" -> tppCommonName).foreach { case (name, value) =>
      Await.result(
        RegulatedEntityAttributeX.regulatedEntityAttributeProvider.vend.createOrUpdateRegulatedEntityAttribute(
          RegulatedEntityId(entity.entityId), None, name, RegulatedEntityAttributeType.STRING, value, Some(true)),
        10.seconds).openOrThrowException("the attribute must be created")
    }
  }

  // PSD2SigningSupport builds its signer once, from the certificate of the first test to run, while
  // PSD2SigningTestSupport generates a new certificate before every test. Signing with that shared signer
  // would present a certificate other than the one registered here, so each call builds its own.
  private def sign(body: String): Map[String, String] =
    new PSD2RequestSigner(berlinGroupPrivateKey, berlinGroupCertificate, berlinGroupKeyId).signRequest(body)

  private def signedPost(request: OBPReq, body: String): APIResponse =
    makePostRequestAdditionalHeader(request <@ (user1), body, sign(body).toList)

  private def lodgeSignedPayment(): String = {
    val ibans = BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString))
      .filterNot(_.bankId.value == "DEFAULT_BANK_ID_NOT_SET")
    val (from, to) = (ibans.head, ibans.last)
    Views.views.vend.systemView(ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)).foreach(view =>
      Views.views.vend.grantAccessToSystemView(from.bankId, from.accountId, view, resourceUser1))
    val response = signedPost(
      V1_3_BG / PaymentServiceTypes.payments.toString / TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString,
      s"""{"debtorAccount":{"iban":"${from.accountRouting.address}"},"instructedAmount":{"currency":"EUR","amount":"2001"},
         |"creditorAccount":{"iban":"${to.accountRouting.address}"},"creditorName":"TestCreditor"}""".stripMargin)
    withClue(s"lodging a signed payment: ${response.body}: ") { response.code should equal(201) }
    response.body.extract[InitiatePaymentResponseJson].paymentId
  }

  private val basketBody = (paymentIds: List[String]) => s"""{"paymentIds":[${paymentIds.map(id => s""""$id"""").mkString(",")}]}"""

  feature("signing baskets with the request signature enforced") {
    scenario("a basket request without the mandatory headers is refused before it reaches the basket", SBS) {
      enforceSignatures()
      val response = makePostRequest((V1_3_BG / "signing-baskets").POST <@ (user1), basketBody(List("any")))
      response.code should equal(400)
    }

    scenario("a signed request from a certificate that is not registered is refused (CERTIFICATE_BLOCKED)", SBS) {
      enforceSignatures()
      val response = signedPost(V1_3_BG / "signing-baskets", basketBody(List("any")))
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.code should equal("CERTIFICATE_BLOCKED")
    }

    scenario("a TPP holding the payment initiation role creates a basket of its own signed payment", SBS) {
      enforceSignatures()
      registerTpp("PSP_PI")
      val payment = lodgeSignedPayment()
      val response = signedPost(V1_3_BG / "signing-baskets", basketBody(List(payment)))
      response.code should equal(201)
      val basketId = response.body.extract[SigningBasketResponseJson].basketId
      Option(response.headers.getOrElse(fail("no headers")).get("Location")).getOrElse(fail("Location is missing")) should endWith(s"/signing-baskets/$basketId")
      Option(response.headers.get.get("ASPSP-SCA-Approach")) should not be empty
    }

    scenario("a TPP holding only the account information role cannot create a basket of payments (ROLE_INVALID)", SBS) {
      enforceSignatures()
      registerTpp("PSP_AI")
      val response = signedPost(V1_3_BG / "signing-baskets", basketBody(List("any")))
      withClue(s"${response.body}: ") { response.code should equal(403) }
      response.body.extract[ErrorMessagesBG].tppMessages.head.code should equal("ROLE_INVALID")
    }
  }
}
