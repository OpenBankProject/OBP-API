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

package code.entitlement

import code.api.util.ApiRole.{canCreateAccount, canCreateCustomer}
import code.messageoutbox.{MessageOutbox, MessageOutboxRelay}
import code.setup.{DefaultUsers, ServerSetup}
import net.liftweb.db.DB
import net.liftweb.mapper.By
import net.liftweb.util.DefaultConnectionIdentifier

/**
 * The "you have been granted a Role" email goes through the message outbox: it is queued in the
 * granting transaction and sent by the relay, never on the request thread.
 */
class RoleGrantedEmailOutboxTest extends ServerSetup with DefaultUsers {

  private def emailRowsFor(entitlementId: String): List[MessageOutbox] =
    MessageOutbox.findAll(By(MessageOutbox.OutboxType, MessageOutbox.TYPE_EMAIL), By(MessageOutbox.SubjectId, entitlementId))

  override def beforeEach(): Unit = {
    super.beforeEach()
    setPropsValues("mail.api.consumer.registered.sender.address" -> "noreply@example.com", "mail.test.mode" -> "true")
  }

  feature("Role granted emails are queued in the message outbox") {

    scenario("a grant queues one email, which the relay sends") {
      val bankId = testBankId1.value
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, canCreateCustomer.toString)
        .openOrThrowException("grant")

      Then("one PENDING email row is queued for the entitlement, addressed to the user")
      val queued = emailRowsFor(entitlement.entitlementId)
      queued.map(_.status) should equal(List(MessageOutbox.STATUS_PENDING))
      queued.head.operationName should equal(MessageOutbox.OPERATION_ROLE_GRANTED_EMAIL)
      MessageOutbox.emailPayload(queued.head).to should equal(List(resourceUser2.emailAddress))
      MessageOutbox.emailPayload(queued.head).subject should include(canCreateCustomer.toString)

      When("the relay runs")
      MessageOutboxRelay.relayOnePass()
      Then("the email is DELIVERED, after one attempt")
      val sent = emailRowsFor(entitlement.entitlementId)
      sent.map(_.status) should equal(List(MessageOutbox.STATUS_DELIVERED))
      sent.head.attempts should equal(1)
    }

    scenario("a row can be claimed for delivery once") {
      val entitlement = Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, canCreateAccount.toString)
        .openOrThrowException("grant")
      val read = emailRowsFor(entitlement.entitlementId).head
      Then("two relays that read the same row: only the first claims it")
      MessageOutbox.claimForDelivery(read) should equal(true)
      MessageOutbox.claimForDelivery(read) should equal(false)
    }

    scenario("a grant that is rolled back queues no email") {
      val role = "CanGetCustomersAtOneBank"
      intercept[RuntimeException] {
        DB.use(DefaultConnectionIdentifier) { _ =>
          Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, role)
          throw new RuntimeException("the request failed after the grant")
        }
      }
      Then("neither the entitlement nor its email exists")
      Entitlement.entitlement.vend.getEntitlement(testBankId1.value, resourceUser2.userId, role).isDefined should equal(false)
      MessageOutbox.findAll(By(MessageOutbox.OutboxType, MessageOutbox.TYPE_EMAIL))
        .exists(r => MessageOutbox.emailPayload(r).subject.contains(role)) should equal(false)
    }
  }
}
