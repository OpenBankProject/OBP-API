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

package code.api.v7_0_0

import java.util.Date

import code.api.RequestHeader
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, Consent}
import code.api.v3_1_0.{ConsentJsonV310, PostConsentChallengeJsonV310}
import code.api.v6_0_0.V600ServerSetup
import code.customer.CustomerX
import code.usercustomerlinks.UserCustomerLink
import com.openbankproject.commons.model.{BankId, CustomerFaceImage, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import code.setup.OBPReq
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * The v7.0.0 "my customers" reads and consent users. ON_BEHALF_OF_USER_ID_PLAN.md Decision 11 and
 * ideas/CONSENT_MY_RESOURCES.md.
 *
 * A consent user owns nothing, so on the older versions of these endpoints it reads back nothing --
 * including the Customers it just linked for its human ("dropping stones into a well"). The v7 pair
 * reads for the on-behalf-of User instead, and only when the Consent's `my_resources.linked_customers`
 * names the Bank with the `read` action.
 *
 * The two properties worth pinning here are the ones a unit test cannot see:
 *  - a Consent that grants nothing gets 403 naming the missing entry, never an empty list -- an agent
 *    must always be able to tell "you may not see this" from "there is nothing here";
 *  - a grant is all-or-nothing within the Bank it names: never a provenance-filtered subset.
 */
class CustomerConsentUserTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ConsentUserTag extends Tag("CustomerConsentUser")

  def v7_0_0_Request: OBPReq = baseRequest / "obp" / "v7.0.0"

  private val consumerKeyHeader = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))

  /** A Customer at `bankId` linked to user1, created through the providers (no endpoint round trip). */
  private def linkedCustomerNumber(bankId: BankId): String = {
    val number = APIUtil.generateUUID().take(12)
    val customer = CustomerX.customerProvider.vend.addCustomer(
      bankId, number, "Consent User Test", "+49 123 456", "consent-user-test@example.com",
      CustomerFaceImage(new Date(), "http://example.com/face.png"),
      new Date(), "single", 0, Nil, "Bachelor", "employed", true, new Date(),
      None, None, "", "", ""
    ).openOrThrowException("expected the customer to be created")
    UserCustomerLink.userCustomerLink.vend
      .createUserCustomerLink(resourceUser1.userId, customer.customerId, new Date(), true)
      .openOrThrowException("expected the user-customer link")
    number
  }

  private def linkedCustomersGrant(bankId: String, actions: List[String]): JValue =
    "linked_customers" -> JArray(List(("bank_id" -> bankId) ~ ("actions" -> actions)))

  private def consentBody(myResources: Option[JValue]): JValue = {
    val base: JObject =
      ("everything" -> false) ~
      ("views" -> JArray(Nil)) ~
      ("entitlements" -> JArray(Nil)) ~
      ("consumer_id" -> testConsumer.consumerId.get) ~
      ("time_to_live" -> 3600)
    myResources.map(mr => base ~ ("my_resources" -> mr)).getOrElse(base)
  }

  private def postConsent(myResources: Option[JValue]) = {
    setPropsValues("consents.allowed" -> "true", "consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")
    makePostRequest(
      (v6_0_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1),
      write(consentBody(myResources)), consumerKeyHeader)
  }

  /** A Consent granted by user1 and answered, as request headers — the caller is then a consent user. */
  private def consentHeaders(myResources: Option[JValue]): List[(String, String)] = {
    val created = postConsent(myResources)
    created.code should equal(201)
    val consent = created.body.extract[ConsentJsonV310]
    val answered = makePostRequest(
      (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consent.consent_id / "challenge").POST <@ (user1),
      write(PostConsentChallengeJsonV310(answer = Consent.challengeAnswerAtTestEnvironment)))
    answered.code should equal(201)
    List((RequestHeader.`Consent-JWT`, consent.jwt)) ::: consumerKeyHeader
  }

  private def customerNumbersOf(body: JValue): List[String] =
    (body \ "customers").extract[List[JObject]].map(o => (o \ "customer_number").extract[String])

  feature("GET /my/customers and /banks/BANK_ID/my/customers with a Consent (my_resources.linked_customers)") {

    scenario("the granting User reads their own linked Customers, with no Consent involved", VersionOfApi, ConsentUserTag) {
      val number = linkedCustomerNumber(testBankId1)

      val all = makeGetRequest((v7_0_0_Request / "my" / "customers").GET <@ (user1))
      all.code should equal(200)
      customerNumbersOf(all.body) should contain(number)

      val atBank = makeGetRequest((v7_0_0_Request / "banks" / testBankId1.value / "my" / "customers").GET <@ (user1))
      atBank.code should equal(200)
      customerNumbersOf(atBank.body) should contain(number)
    }

    scenario("a consent user whose Consent grants no linked_customers is refused, not handed an empty list", VersionOfApi, ConsentUserTag) {
      linkedCustomerNumber(testBankId1)
      val headers = consentHeaders(None)

      val all = makeGetRequest((v7_0_0_Request / "my" / "customers").GET, headers)
      all.code should equal(403)
      all.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)

      val atBank = makeGetRequest((v7_0_0_Request / "banks" / testBankId1.value / "my" / "customers").GET, headers)
      atBank.code should equal(403)
      atBank.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)
      atBank.body.extract[ErrorMessage].message should include(testBankId1.value)
    }

    scenario("a consent user whose Consent names the Bank reads the granting User's Customers", VersionOfApi, ConsentUserTag) {
      val number = linkedCustomerNumber(testBankId1)
      val headers = consentHeaders(Some(linkedCustomersGrant(testBankId1.value, List("read"))))

      val all = makeGetRequest((v7_0_0_Request / "my" / "customers").GET, headers)
      all.code should equal(200)
      customerNumbersOf(all.body) should contain(number)

      val atBank = makeGetRequest((v7_0_0_Request / "banks" / testBankId1.value / "my" / "customers").GET, headers)
      atBank.code should equal(200)
      customerNumbersOf(atBank.body) should contain(number)
    }

    // The grant is per Bank, and a Bank is not looked up when the Consent is created: an entry naming a
    // Bank that does not exist is accepted and simply covers nothing.
    scenario("a grant for another Bank does not open this one", VersionOfApi, ConsentUserTag) {
      linkedCustomerNumber(testBankId1)
      val headers = consentHeaders(Some(linkedCustomersGrant("a-bank-this-consent-does-not-mean", List("read"))))

      val atBank = makeGetRequest((v7_0_0_Request / "banks" / testBankId1.value / "my" / "customers").GET, headers)
      atBank.code should equal(403)
      atBank.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)
    }

    scenario("the read action is what grants reading — a write-only grant does not", VersionOfApi, ConsentUserTag) {
      linkedCustomerNumber(testBankId1)
      val headers = consentHeaders(Some(linkedCustomersGrant(testBankId1.value, List("write"))))

      val atBank = makeGetRequest((v7_0_0_Request / "banks" / testBankId1.value / "my" / "customers").GET, headers)
      atBank.code should equal(403)
      atBank.body.extract[ErrorMessage].message should include(ConsentMyResourcesMissing)
    }

    scenario("a malformed linked_customers entry is refused when the Consent is created", VersionOfApi, ConsentUserTag) {
      val noBank = postConsent(Some(linkedCustomersGrant("", List("read"))))
      noBank.code should equal(400)
      noBank.body.extract[ErrorMessage].message should include(ConsentMyResourcesInvalid)

      val badAction = postConsent(Some(linkedCustomersGrant(testBankId1.value, List("delete"))))
      badAction.code should equal(400)
      badAction.body.extract[ErrorMessage].message should include(ConsentMyResourcesInvalid)
    }
  }
}
