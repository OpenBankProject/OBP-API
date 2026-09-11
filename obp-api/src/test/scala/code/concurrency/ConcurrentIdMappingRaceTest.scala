package code.concurrency

import code.api.util.{APIUtil, DoobieUtil}
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.setup.ServerSetup
import doobie.implicits._

import java.util.UUID

/**
 * The id-mapping tables must mint exactly ONE OBP id per underlying bank reference.
 *
 * THE HAZARD (fixed by V057):
 *   getOrCreate*Id is a SELECT-then-INSERT. The unique indexes the Lift entities declared were on
 *   the id column (a fresh random UUID per insert, so it never collides) and on the composite
 *   (id, reference) - which is strictly implied by the first and therefore constrains nothing.
 *   With no constraint on the reference column itself, two concurrent calls for the same reference
 *   could both miss the SELECT, both INSERT, and both succeed: one bank reference, two different
 *   OBP ids. A later read (LIMIT 1, no ORDER BY) then returned an arbitrary one, so data written
 *   under one id was invisible under the other. Helper.convertToId runs this for every inbound
 *   message on the RabbitMQ, gRPC, REST and stored-procedure connectors.
 *
 * WHY THIS ASSERTS THE CONSTRAINT RATHER THAN RACING:
 *   A first draft fired N concurrent getOrCreateAccountId calls and asserted one row came back.
 *   It passed with the fix reverted - the first insert always won before the others got to their
 *   SELECT, so the window never opened and the test proved nothing. Racing is not a reliable way
 *   to demonstrate a missing constraint. What actually protects the invariant is the unique index,
 *   so that is what is asserted here, deterministically: a second row for the same reference must
 *   be rejected by the database. Revert V057 and the second insert succeeds and this fails.
 */
class ConcurrentIdMappingRaceTest extends ServerSetup {

  private def rowCountFor(reference: String): Int =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM accountidmapping WHERE maccountplaintextreference = $reference"
        .query[Int].unique)

  private def insertRaw(accountId: String, reference: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO accountidmapping (maccountid, maccountplaintextreference, createdat, updatedat)
            VALUES ($accountId, $reference, NOW(), NOW())"""
        .update.run)
    ()
  }

  Feature("id-mapping tables mint one id per reference") {

    Scenario("the database rejects a second mapping row for a reference that is already mapped") {
      val reference = s"__idmap_dup_${UUID.randomUUID.toString.take(12)}"

      Given("a reference that has been mapped once")
      insertRaw(APIUtil.generateUUID(), reference)
      rowCountFor(reference) should equal(1)

      When("a second row is inserted for the SAME reference but a different generated id")
      Then("the unique index rejects it - without it, both rows would coexist and one bank " +
        "account reference would resolve to two different OBP account ids")
      a[Exception] should be thrownBy insertRaw(APIUtil.generateUUID(), reference)

      And("only the original row survives")
      rowCountFor(reference) should equal(1)
    }

    Scenario("getOrCreateAccountId is idempotent for an already-mapped reference") {
      val reference = s"__idmap_repeat_${UUID.randomUUID.toString.take(12)}"

      val first = MappedAccountIdMappingProvider.getOrCreateAccountId(reference)
        .openOrThrowException("expected an account id")
      val second = MappedAccountIdMappingProvider.getOrCreateAccountId(reference)
        .openOrThrowException("expected an account id")

      second.value should equal(first.value)
      rowCountFor(reference) should equal(1)
    }
  }
}
