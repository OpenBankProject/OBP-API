package code.metadata.transactionimages

import java.net.URL
import java.util.Date

import code.setup.ServerSetup
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId, UserPrimaryKey, ViewId}
import net.liftweb.common.Full

/**
 * Characterization of the transaction-images provider, written before the implementation moves to
 * Doobie.
 *
 * The provider had no test of its own. Pinned here is the behaviour the Lift implementation has
 * today: images are a list per (transaction, view), the stored image carries back its description
 * and URL, delete takes the id that add returned, and the two bulk deletes differ in scope.
 *
 * The imageURL round trip is worth its own assertion - it is stored as text and handed back as a
 * URL, which is the kind of conversion a rewrite can drop or mangle.
 *
 * Routed through TransactionImages.transactionImages.vend so it keeps testing whichever
 * implementation buildOne returns.
 */
class TransactionImagesProviderTest extends ServerSetup {

  private val bankId = BankId("image-test-bank")
  private val accountId = AccountId("image-test-account")
  private val transactionId = TransactionId("image-test-transaction")
  private val otherTransactionId = TransactionId("image-test-transaction-2")
  private val viewId = ViewId("owner")
  private val otherViewId = ViewId("auditor")
  private val poster = UserPrimaryKey(1)

  private def provider = TransactionImages.transactionImages.vend

  override def beforeEach() = {
    super.beforeEach()
    provider.bulkDeleteTransactionImage(bankId, accountId)
  }

  private def add(t: TransactionId, v: ViewId, description: String, url: String) =
    provider.addTransactionImage(bankId, accountId, t)(poster, v, description, new Date(), url)

  Feature("transaction image storage") {

    Scenario("a transaction with no images reads as an empty list") {
      provider.getImagesForTransaction(bankId, accountId, transactionId)(viewId) should equal(Nil)
    }

    Scenario("an image can be added and read back with its description and url") {
      val added = add(transactionId, viewId, "receipt", "https://example.com/receipt.png")
      added.isDefined should equal(true)

      val found = provider.getImagesForTransaction(bankId, accountId, transactionId)(viewId)
      found.size should equal(1)
      found.head.description should equal("receipt")
      found.head.imageUrl should equal(new URL("https://example.com/receipt.png"))
    }

    Scenario("images are scoped to the view they were posted on") {
      add(transactionId, viewId, "on owner view", "https://example.com/a.png")
      provider.getImagesForTransaction(bankId, accountId, transactionId)(otherViewId) should equal(Nil)
    }

    Scenario("a transaction can hold more than one image") {
      add(transactionId, viewId, "first", "https://example.com/1.png")
      add(transactionId, viewId, "second", "https://example.com/2.png")

      provider.getImagesForTransaction(bankId, accountId, transactionId)(viewId)
        .map(_.description).sorted should equal(List("first", "second"))
    }

    Scenario("deleting an image removes only that image") {
      val keep = add(transactionId, viewId, "keep", "https://example.com/keep.png").openOrThrowException("added")
      val drop = add(transactionId, viewId, "drop", "https://example.com/drop.png").openOrThrowException("added")

      provider.deleteTransactionImage(bankId, accountId, transactionId)(drop.id_) should equal(Full(true))

      val left = provider.getImagesForTransaction(bankId, accountId, transactionId)(viewId)
      left.size should equal(1)
      left.head.id_ should equal(keep.id_)
    }

    Scenario("bulk delete on a transaction leaves other transactions alone") {
      add(transactionId, viewId, "one", "https://example.com/1.png")
      add(otherTransactionId, viewId, "two", "https://example.com/2.png")

      provider.bulkDeleteImagesOnTransaction(bankId, accountId, transactionId) should equal(true)

      provider.getImagesForTransaction(bankId, accountId, transactionId)(viewId) should equal(Nil)
      provider.getImagesForTransaction(bankId, accountId, otherTransactionId)(viewId).size should equal(1)
    }

    Scenario("bulk delete on an account clears every transaction's images") {
      add(transactionId, viewId, "one", "https://example.com/1.png")
      add(otherTransactionId, viewId, "two", "https://example.com/2.png")

      provider.bulkDeleteTransactionImage(bankId, accountId) should equal(true)

      provider.getImagesForTransaction(bankId, accountId, transactionId)(viewId) should equal(Nil)
      provider.getImagesForTransaction(bankId, accountId, otherTransactionId)(viewId) should equal(Nil)
    }
  }
}
