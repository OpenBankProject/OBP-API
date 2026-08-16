package code.metadata.tags

import java.util.Date

import code.setup.ServerSetup
import net.liftweb.common.Full
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId, UserPrimaryKey, ViewId}

/**
 * Characterization of the tags provider, written before the implementation moves to Doobie.
 *
 * The provider has two parallel sets of methods - one for tags on a transaction, one for tags on
 * an account - stored in the same table and told apart by whether the transaction column is set.
 * That is the part most at risk in a rewrite, so it is what most of these scenarios check:
 * account tags must not leak into a transaction's tags and the reverse.
 *
 * Also pinned: tags are scoped by view, add returns the stored tag with an id that delete accepts,
 * and the two bulk deletes differ in scope (one transaction vs the whole account).
 *
 * Routed through Tags.tags.vend so it keeps testing whichever implementation buildOne returns.
 */
class TagsProviderTest extends ServerSetup {

  private val bankId = BankId("tag-test-bank")
  private val accountId = AccountId("tag-test-account")
  private val transactionId = TransactionId("tag-test-transaction")
  private val otherTransactionId = TransactionId("tag-test-transaction-2")
  private val viewId = ViewId("owner")
  private val otherViewId = ViewId("auditor")
  private val poster = UserPrimaryKey(1)

  private def provider = Tags.tags.vend

  override def beforeEach() = {
    super.beforeEach()
    provider.bulkDeleteTags(bankId, accountId)
  }

  private def addOnTransaction(t: TransactionId, v: ViewId, text: String) =
    provider.addTag(bankId, accountId, t)(poster, v, text, new Date())

  private def addOnAccount(v: ViewId, text: String) =
    provider.addTagOnAccount(bankId, accountId)(poster, v, text, new Date())

  Feature("tag storage") {

    Scenario("a transaction with no tags reads as an empty list") {
      provider.getTags(bankId, accountId, transactionId)(viewId) should equal(Nil)
    }

    Scenario("a tag can be added to a transaction and read back") {
      val added = addOnTransaction(transactionId, viewId, "holiday")
      added.isDefined should equal(true)
      added.openOrThrowException("just asserted").value should equal("holiday")

      val found = provider.getTags(bankId, accountId, transactionId)(viewId)
      found.size should equal(1)
      found.head.value should equal("holiday")
    }

    Scenario("tags are scoped to the view they were posted on") {
      addOnTransaction(transactionId, viewId, "on owner view")
      provider.getTags(bankId, accountId, transactionId)(otherViewId) should equal(Nil)
    }

    Scenario("an account tag is not a tag on any transaction") {
      addOnAccount(viewId, "account level")

      Then("the account has it")
      provider.getTagsOnAccount(bankId, accountId)(viewId).map(_.value) should equal(List("account level"))

      And("no transaction picks it up")
      provider.getTags(bankId, accountId, transactionId)(viewId) should equal(Nil)
    }

    Scenario("a transaction tag is not a tag on the account") {
      addOnTransaction(transactionId, viewId, "transaction level")

      provider.getTags(bankId, accountId, transactionId)(viewId).map(_.value) should equal(List("transaction level"))
      provider.getTagsOnAccount(bankId, accountId)(viewId) should equal(Nil)
    }

    Scenario("deleting a transaction tag removes only that tag") {
      val keep = addOnTransaction(transactionId, viewId, "keep").openOrThrowException("added")
      val drop = addOnTransaction(transactionId, viewId, "drop").openOrThrowException("added")

      provider.deleteTag(bankId, accountId, transactionId)(drop.id_) should equal(Full(true))

      val left = provider.getTags(bankId, accountId, transactionId)(viewId)
      left.size should equal(1)
      left.head.id_ should equal(keep.id_)
    }

    Scenario("deleting an account tag removes only that tag") {
      val keep = addOnAccount(viewId, "keep").openOrThrowException("added")
      val drop = addOnAccount(viewId, "drop").openOrThrowException("added")

      provider.deleteTagOnAccount(bankId, accountId)(drop.id_) should equal(Full(true))

      val left = provider.getTagsOnAccount(bankId, accountId)(viewId)
      left.size should equal(1)
      left.head.id_ should equal(keep.id_)
    }

    Scenario("bulk delete on a transaction leaves other transactions and the account alone") {
      addOnTransaction(transactionId, viewId, "one")
      addOnTransaction(otherTransactionId, viewId, "two")
      addOnAccount(viewId, "account level")

      provider.bulkDeleteTagsOnTransaction(bankId, accountId, transactionId) should equal(true)

      provider.getTags(bankId, accountId, transactionId)(viewId) should equal(Nil)
      provider.getTags(bankId, accountId, otherTransactionId)(viewId).size should equal(1)
      provider.getTagsOnAccount(bankId, accountId)(viewId).size should equal(1)
    }

    Scenario("bulk delete on an account clears transaction tags and account tags together") {
      addOnTransaction(transactionId, viewId, "one")
      addOnAccount(viewId, "account level")

      provider.bulkDeleteTags(bankId, accountId) should equal(true)

      provider.getTags(bankId, accountId, transactionId)(viewId) should equal(Nil)
      provider.getTagsOnAccount(bankId, accountId)(viewId) should equal(Nil)
    }
  }
}
