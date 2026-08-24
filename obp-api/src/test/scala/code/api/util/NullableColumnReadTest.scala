package code.api.util

import code.accountattribute.DoobieAccountAttributeProvider
import code.apicollection.DoobieApiCollectionsProvider
import code.cards.MappedPhysicalCard
import code.crm.DoobieCrmEventProvider
import code.customeraccountlinks.DoobieCustomerAccountLinkProvider
import code.setup.ServerSetup
import com.openbankproject.commons.model.{AccountId, BankId}
import doobie.implicits._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Every one of these tables has exactly one NOT NULL column - its primary key - and the rest were
 * filled in by Lift Mapper, whose readers each had a per-type answer for a NULL column
 * (MappedString -> null, MappedBoolean -> false, MappedLongForeignKey -> 0L, MappedDateTime ->
 * null). Several columns are NULL in real databases for a specific, documented reason: they were
 * added to a model long after their table existed and Schemifier added them with no backfill, or
 * the sandbox importer deliberately never set them.
 *
 * The Doobie stores that replaced those entities bound such columns to non-Option Scala types,
 * which makes doobie raise `NonNullableColumnRead` and fail the WHOLE query - one legacy row takes
 * out the entire listing for that bank, as a 500.
 *
 * A fresh test database has no such rows, so the rest of the suite passes whether or not the
 * collapse is right; these tests write the NULLs explicitly and then read through the real
 * provider, which is the only way to hold that behaviour. Each one fails with
 * `NonNullableColumnRead` against the bare-bound readers.
 *
 * The INSERTs name only the columns they set, so the remaining columns take SQL NULL on both H2 and
 * Postgres, and the auto-increment primary key is left to the database.
 */
class NullableColumnReadTest extends ServerSetup {

  private def wipe(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apicollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM customeraccountlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcrmevent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedphysicalcard".update.run)
  }

  override def beforeAll() = { super.beforeAll(); wipe() }
  override def afterEach() = { super.afterEach(); wipe() }

  feature("a Doobie store reads a legacy row whose later-added columns are NULL") {

    scenario("account attributes: mproductinstancecode was added with no backfill") {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedaccountattribute
                (mbankidid, maccountid, mcode, maccountattributeid, mtype, mname, mvalue)
              VALUES ('bank-null-1', 'acc-null-1', 'code-1', 'attr-null-1', 'STRING', 'n', 'v')"""
          .update.run)

      val result = Await.result(
        DoobieAccountAttributeProvider.getAccountAttributesByAccount(
          BankId("bank-null-1"), AccountId("acc-null-1")),
        10.seconds)

      result.isDefined should equal(true)
      val attributes = result.openOrThrowException("expected the attribute list")
      attributes.size should equal(1)
      // The field is already Option-typed, so a NULL column is None - not Some(null), which is what
      // wrapping a bare bind in Some() produced.
      attributes.head.productInstanceCode should equal(None)
      attributes.head.name should equal("n")
    }

    scenario("api collections: description was added with no backfill") {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO apicollection (apicollectionid, userid, apicollectionname, issharable)
              VALUES ('coll-null-1', 'user-null-1', 'my-collection', true)""".update.run)

      val collections = DoobieApiCollectionsProvider.getApiCollectionsByUserId("user-null-1")

      collections.size should equal(1)
      collections.head.apiCollectionName should equal("my-collection")
    }

    scenario("customer account links: bankid was added with no backfill") {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO customeraccountlink
                (customeraccountlinkid, customerid, accountid, relationshiptype)
              VALUES ('link-null-1', 'cust-null-1', 'acc-null-1', 'owner')""".update.run)

      val links = DoobieCustomerAccountLinkProvider
        .getCustomerAccountLinksByCustomerId("cust-null-1")
        .openOrThrowException("expected the link list")

      links.size should equal(1)
      links.head.relationshipType should equal("owner")
    }

    scenario("crm events: the sandbox importer never set user, scheduled date or result") {
      // LocalMappedConnectorDataImport logs "Note: We are not saving API User, Result or Scheduled
      // Date" and leaves those three columns unset; mUserId was a MappedLongForeignKey and
      // mScheduledDate a MappedDateTime, both of which write SQL NULL when undefined.
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedcrmevent
                (mcrmeventid, mbankid, mcustomername, mcustomernumber, mcategory, mdetail, mchannel)
              VALUES ('crm-null-1', 'bank-null-1', 'Jane', '4242', 'Call', 'detail', 'Phone')"""
          .update.run)

      val events = DoobieCrmEventProvider
        .getCrmEvents(BankId("bank-null-1"))
        .getOrElse(fail("expected a CRM event list"))

      events.size should equal(1)
      events.head.customerName should equal("Jane")
      // MappedDateTime read a NULL column as null; the CrmEvent trait exposes the dates but not the
      // user foreign key, whose NULL collapses to 0L one layer down in CrmEventRow.
      events.head.scheduledDate should equal(null)
      events.head.result should equal(null)
    }

    scenario("physical cards: mcvv and mbrand were added with no backfill") {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedphysicalcard
                (mcardid, mbankid, mbankcardnumber, mcardtype, mnameoncard, mserialnumber)
              VALUES ('card-null-1', 'bank-null-1', '4242', 'DEBIT', 'Jane', 'serial-1')"""
          .update.run)

      val cards = MappedPhysicalCard.findAllForBank("bank-null-1", None, None)

      cards.size should equal(1)
      cards.head.cardId should equal("card-null-1")
      // MappedBoolean read a NULL column as false, and MappedLongForeignKey as 0L.
      cards.head.enabled should equal(false)
      cards.head.accountKey should equal(0L)
      // The accessors over the raw strings must still work rather than dereferencing a null - and
      // must say "none" rather than "one empty one". `"".split(",")` is `Array("")`, so a networks
      // accessor without the emptiness guard its sibling `allows` has publishes `[""]`.
      cards.head.networks should equal(Nil)
      cards.head.allows should equal(Nil)
      // mcvv/mbrand hold SQL NULL on every row written before they were added to the model. Some("")
      // would say the card has an empty CVV; the column says it has none.
      cards.head.cvv should equal(None)
      cards.head.brand should equal(None)
    }
  }
}
