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

package code.messageoutbox

import code.setup.{DefaultUsers, ServerSetup}
import net.liftweb.mapper.By

/**
 * This test checks that an Open Corridor outbox message the relay can never deliver stops being
 * resent once it has used its attempts, and goes STICKY for an operator, as an email does. The
 * target bank has no AMQP broker configured, so every attempt is a transport failure.
 */
class OpenCorridorOutboxRetryLimitTest extends ServerSetup with DefaultUsers {

  private val maxAttempts = 3

  override def beforeEach(): Unit = {
    super.beforeEach()
    setPropsValues("open_corridor_enabled" -> "true", "open_corridor.outbox_max_attempts" -> maxAttempts.toString)
  }

  private def enqueueUndeliverable(): MessageOutbox =
    MessageOutbox.enqueue(MessageOutbox.TYPE_OPEN_CORRIDOR, java.util.UUID.randomUUID().toString,
      MessageOutbox.SUBJECT_TYPE_SETTLEMENT_ID, "obp_settlement_instruction",
      "bank-with-no-broker-" + java.util.UUID.randomUUID().toString, "{}")

  private def reload(row: MessageOutbox): MessageOutbox =
    MessageOutbox.find(By(MessageOutbox.id, row.id.get)).openOrThrowException("outbox row")

  feature("Open Corridor outbox messages have a retry limit") {

    scenario("an undeliverable message stays PENDING below the limit and goes STICKY at it") {
      val row = enqueueUndeliverable()

      When("the relay fails to deliver it one time fewer than the limit")
      (1 until maxAttempts).foreach(_ => MessageOutboxRelay.relayRow(reload(row)))
      Then("it is still PENDING, with the transport failure recorded")
      val belowLimit = reload(row)
      belowLimit.status should equal(MessageOutbox.STATUS_PENDING)
      belowLimit.attempts should equal(maxAttempts - 1)
      belowLimit.LastError.get should include("BANK_ID")

      When("the relay fails once more")
      MessageOutboxRelay.relayRow(reload(row))
      Then("it is STICKY, and last_error says why the relay gave up")
      val atLimit = reload(row)
      atLimit.status should equal(MessageOutbox.STATUS_STICKY)
      atLimit.attempts should equal(maxAttempts)
      atLimit.LastError.get should include(s"gave up after $maxAttempts attempts")
      atLimit.LastError.get should include("transport failure")

      And("a STICKY row is no longer picked up by the relay")
      MessageOutbox.pending().exists(_.id.get == row.id.get) should equal(false)
    }
  }
}
