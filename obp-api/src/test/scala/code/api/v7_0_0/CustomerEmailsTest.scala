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

import org.json4s.jvalue2extractable
import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.generateUUID
import code.api.util.ApiRole.CanSendEmailToCustomerAtOneBank
import code.api.util.DoobieUtil
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.messageoutbox.MessageOutbox
import code.model.dataAccess.SelfServiceBank
import code.setup.APIResponse
import com.openbankproject.commons.model.{BankId, CustomerFaceImage, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import doobie.implicits._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This suite checks Send Email to Customer: who may queue an email to a Customer, that only the space's own
 * Customers are emailed, under which limits, that it lands in the message outbox addressed to the Customer, and that
 * the address is never returned.
 */
class CustomerEmailsTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object CustomerEmails extends Tag("customerEmails")

  private lazy val bankId = testBankId1.value

  private def enable(extra: (String, String)*): Unit = {
    setPropsValues((List("customer_emails.sender_address" -> "cohort@example.com") ++ extra): _*)
  }

  private def withRole[T](f: => T): T = {
    val grant = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanSendEmailToCustomerAtOneBank.toString)
    try f finally Entitlement.entitlement.vend.deleteEntitlement(grant)
  }

  /** A fresh Customer of `bank`, created through the provider; returns its id and email address. */
  private def customer(bank: String = bankId, email: Option[String] = None): (String, String) = {
    val address = email.getOrElse(s"applicant-${generateUUID().take(8)}@example.com")
    val created = CustomerX.customerProvider.vend.addCustomer(
      BankId(bank), generateUUID().take(12), "Applicant Test", "+968 1234 5678", address,
      CustomerFaceImage(new Date(), "http://example.com/face.png"),
      new Date(), "single", 0, Nil, "Bachelor", "employed", false, new Date(),
      None, None, "", "", ""
    ).openOrThrowException("expected the customer to be created")
    (created.customerId, address)
  }

  private def emails(customerId: String) = v7 / "banks" / bankId / "customers" / customerId / "emails"
  private def body(subject: String = "Your application was received", reference: Option[String] = Some("app:1")): String =
    write(PostCustomerEmailJsonV700(subject, "Thank you for applying.", reference))
  private def message(response: APIResponse): String = response.body.extract[ErrorMessage].message

  /** Removes the self-service record of one bank; the store has no per-bank delete. */
  private def forgetSelfServiceBank(bank: String): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM selfservicebank WHERE bankid = $bank".update.run)
    ()
  }

  feature(s"Send Email to Customer - POST /banks/BANK_ID/customers/CUSTOMER_ID/emails - $VersionOfApi") {

    scenario("Customers of a self-service bank are not emailed", CustomerEmails, VersionOfApi) {
      enable()
      val (customerId, _) = customer()
      SelfServiceBank.record(bankId, resourceUser1.userId)
      val response = try withRole { makePostRequest(emails(customerId).POST <@ (user1), body()) }
        finally forgetSelfServiceBank(bankId)
      response.code should equal(403)
      message(response) should include(CustomerEmailsNotInSelfServiceBank)
    }

    scenario("It needs the Role", CustomerEmails, VersionOfApi) {
      enable()
      val (customerId, _) = customer()
      val response = makePostRequest(emails(customerId).POST <@ (user1), body())
      response.code should equal(403)
      message(response) should include(UserHasMissingRoles)
    }

    scenario("It queues the email in the outbox, to the Customer's address, without returning it", CustomerEmails, VersionOfApi) {
      enable()
      val (customerId, address) = customer()
      val response = withRole { makePostRequest(emails(customerId).POST <@ (user1), body()) }
      withClue(response.body) { response.code should equal(201) }
      val queued = response.body.extract[CustomerEmailJsonV700]
      queued.customer_id should equal(customerId)
      queued.status should equal(MessageOutbox.STATUS_PENDING)
      queued.reference should equal(Some("app:1"))
      response.body.toString should not include address

      And("the outbox row is an email to the Customer's address, saying which application sent it")
      val row = MessageOutbox.findById(queued.message_id.toLong).openOrThrowException("row")
      row.outboxType should equal(MessageOutbox.TYPE_EMAIL)
      row.targetId should equal(address)
      val payload = MessageOutbox.emailPayload(row)
      payload.subject should equal("Your application was received")
      payload.text_content.getOrElse("") should include("Thank you for applying.")
      payload.text_content.getOrElse("") should include("Sent by")
      row.metadataJson should include("app:1")
    }

    scenario("Only the space's own Customers are emailed", CustomerEmails, VersionOfApi) {
      enable()
      val (otherBanksCustomer, _) = customer(bank = testBankId2.value)
      withRole {
        makePostRequest(emails(otherBanksCustomer).POST <@ (user1), body()).code should equal(404)
        makePostRequest(emails("no-such-customer").POST <@ (user1), body()).code should equal(404)
      }
    }

    scenario("A Customer without a usable address, and a subject over more than one line, are refused", CustomerEmails, VersionOfApi) {
      enable()
      val (noAddress, _) = customer(email = Some(""))
      val (customerId, _) = customer()
      withRole {
        val response = makePostRequest(emails(noAddress).POST <@ (user1), body())
        response.code should equal(400)
        message(response) should include(CustomerHasNoEmail)
        makePostRequest(emails(customerId).POST <@ (user1), body(subject = "Hello\r\nBcc: someone@example.com")).code should equal(400)
      }
    }

    scenario("At most customer_emails.max_per_customer_per_day emails are queued for a Customer", CustomerEmails, VersionOfApi) {
      enable("customer_emails.max_per_customer_per_day" -> "2")
      val (customerId, _) = customer()
      val codes = withRole { (1 to 3).map(_ => makePostRequest(emails(customerId).POST <@ (user1), body()).code) }
      codes should equal(Seq(201, 201, 429))
    }
  }
}
