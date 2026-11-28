package code.metadata.wheretags

import java.util.Date

import code.setup.ServerSetup
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId, UserPrimaryKey, ViewId}

/**
 * Characterization of the where-tags (geo tag) provider, written before the implementation moves
 * to Doobie.
 *
 * The behaviour that is easy to lose here is that a where tag is a single value per
 * (transaction, view) rather than a list: adding a second one for the same view REPLACES the
 * first. Every other provider in this package appends, so a rewrite that follows the neighbours'
 * shape would silently start accumulating rows.
 *
 * Also pinned: coordinates survive the round trip, tags are scoped by view, and the two bulk
 * deletes differ in scope (one transaction vs the whole account).
 *
 * Routed through WhereTags.whereTags.vend so it keeps testing whichever implementation buildOne
 * returns.
 */
class WhereTagsProviderTest extends ServerSetup {

  private val bankId = BankId("wheretag-test-bank")
  private val accountId = AccountId("wheretag-test-account")
  private val transactionId = TransactionId("wheretag-test-transaction")
  private val otherTransactionId = TransactionId("wheretag-test-transaction-2")
  private val viewId = ViewId("owner")
  private val otherViewId = ViewId("auditor")
  private val poster = UserPrimaryKey(1)

  private def provider = WhereTags.whereTags.vend

  override def beforeEach() = {
    super.beforeEach()
    provider.bulkDeleteWhereTags(bankId, accountId)
  }

  private def add(t: TransactionId, v: ViewId, lon: Double, lat: Double) =
    provider.addWhereTag(bankId, accountId, t)(poster, v, new Date(), lon, lat)

  Feature("where tag storage") {

    Scenario("a transaction with no where tag reads as an empty box") {
      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId).isDefined should equal(false)
    }

    Scenario("a where tag can be added and its coordinates read back") {
      add(transactionId, viewId, 12.5, -3.25) should equal(true)

      val found = provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId)
      found.isDefined should equal(true)
      val tag = found.openOrThrowException("just asserted")
      tag.longitude should equal(12.5)
      tag.latitude should equal(-3.25)
    }

    Scenario("adding a second where tag for the same view replaces the first") {
      add(transactionId, viewId, 1.0, 2.0)
      add(transactionId, viewId, 10.0, 20.0)

      Then("the latest coordinates are the ones stored")
      val tag = provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId)
        .openOrThrowException("added twice")
      tag.longitude should equal(10.0)
      tag.latitude should equal(20.0)

      And("deleting once leaves nothing, i.e. there was only ever one row")
      provider.deleteWhereTag(bankId, accountId, transactionId)(viewId)
      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId).isDefined should equal(false)
    }

    Scenario("where tags are scoped to the view they were posted on") {
      add(transactionId, viewId, 1.0, 2.0)
      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(otherViewId).isDefined should equal(false)
    }

    Scenario("deleting a where tag on one view leaves the other view's alone") {
      add(transactionId, viewId, 1.0, 2.0)
      add(transactionId, otherViewId, 3.0, 4.0)

      provider.deleteWhereTag(bankId, accountId, transactionId)(viewId) should equal(true)

      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId).isDefined should equal(false)
      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(otherViewId).isDefined should equal(true)
    }

    Scenario("bulk delete on a transaction leaves other transactions alone") {
      add(transactionId, viewId, 1.0, 2.0)
      add(otherTransactionId, viewId, 3.0, 4.0)

      provider.bulkDeleteWhereTagsOnTransaction(bankId, accountId, transactionId) should equal(true)

      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId).isDefined should equal(false)
      provider.getWhereTagForTransaction(bankId, accountId, otherTransactionId)(viewId).isDefined should equal(true)
    }

    Scenario("bulk delete on an account clears every transaction") {
      add(transactionId, viewId, 1.0, 2.0)
      add(otherTransactionId, viewId, 3.0, 4.0)

      provider.bulkDeleteWhereTags(bankId, accountId) should equal(true)

      provider.getWhereTagForTransaction(bankId, accountId, transactionId)(viewId).isDefined should equal(false)
      provider.getWhereTagForTransaction(bankId, accountId, otherTransactionId)(viewId).isDefined should equal(false)
    }
  }
}
