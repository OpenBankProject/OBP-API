package code.messageoutbox

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._

/**
 * The outbox store: the id enqueue returns is the id of the row it wrote, and a row whose reply or
 * error column is NULL (written by an older version, or stored as NULL by Oracle for '') can still
 * be settled.
 */
class MessageOutboxStoreTest extends ServerSetup {

  Feature("message outbox store") {

    Scenario("enqueue returns the id of the row it inserted, even when another row shares subject and operation") {
      val first = MessageOutbox.enqueue(MessageOutbox.TYPE_EMAIL, "subject-1", "customer", "op", "a@example.com", "{}")
      val second = MessageOutbox.enqueue(MessageOutbox.TYPE_EMAIL, "subject-1", "customer", "op", "b@example.com", "{}")

      second.id should not equal first.id
      MessageOutbox.findById(second.id).openOrThrowException("row").targetId shouldBe "b@example.com"
      MessageOutbox.findById(first.id).openOrThrowException("row").targetId shouldBe "a@example.com"
    }

    Scenario("a row with NULL last_error and last_reply_json can be settled") {
      val row = MessageOutbox.enqueue(MessageOutbox.TYPE_EMAIL, "subject-2", "customer", "op", "c@example.com", "{}")
      DoobieUtil.runUpdate(
        sql"UPDATE message_outbox SET last_error = NULL, last_reply_json = NULL WHERE id = ${row.id}".update.run)

      MessageOutbox.setOutcome(row.id, MessageOutbox.STATUS_STICKY, null, null)

      val settled = MessageOutbox.findById(row.id).openOrThrowException("row")
      settled.status shouldBe MessageOutbox.STATUS_STICKY
    }
  }
}
