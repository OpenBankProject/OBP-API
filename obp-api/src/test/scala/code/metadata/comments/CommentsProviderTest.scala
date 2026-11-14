package code.metadata.comments

import java.util.Date

import code.setup.ServerSetup
import net.liftweb.common.Full
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId, UserPrimaryKey, ViewId}

/**
 * Characterization of the comments provider, written before the implementation moves to Doobie.
 *
 * The provider had no test of its own. What is pinned here is the behaviour the Lift
 * implementation has today:
 *
 *  - comments are scoped by (bank, account, transaction) AND by view, so the same transaction
 *    seen through another view has its own comments;
 *  - addComment returns the stored comment, carrying back the text, poster and date it was
 *    given, plus an id that deleteComment accepts;
 *  - deleting a comment removes only that one;
 *  - bulkDeleteCommentsOnTransaction clears one transaction and leaves other transactions on the
 *    same account alone;
 *  - bulkDeleteComments clears a whole account.
 *
 * Routed through Comments.comments.vend rather than the concrete object, so it keeps testing
 * whichever implementation buildOne returns.
 */
class CommentsProviderTest extends ServerSetup {

  private val bankId = BankId("comment-test-bank")
  private val accountId = AccountId("comment-test-account")
  private val transactionId = TransactionId("comment-test-transaction")
  private val otherTransactionId = TransactionId("comment-test-transaction-2")
  private val viewId = ViewId("owner")
  private val otherViewId = ViewId("auditor")
  private val poster = UserPrimaryKey(1)

  private def provider = Comments.comments.vend

  override def beforeEach() = {
    super.beforeEach()
    provider.bulkDeleteComments(bankId, accountId)
  }

  private def add(t: TransactionId, v: ViewId, text: String) =
    provider.addComment(bankId, accountId, t)(poster, v, text, new Date())

  Feature("comment storage") {

    Scenario("a transaction with no comments reads as an empty list") {
      provider.getComments(bankId, accountId, transactionId)(viewId) should equal(Nil)
    }

    Scenario("a comment can be added and read back with its text intact") {
      val added = add(transactionId, viewId, "first comment")
      added.isDefined should equal(true)
      added.openOrThrowException("just asserted").text should equal("first comment")

      val found = provider.getComments(bankId, accountId, transactionId)(viewId)
      found.size should equal(1)
      found.head.text should equal("first comment")
    }

    Scenario("comments are scoped to the view they were posted on") {
      add(transactionId, viewId, "on owner view")

      Then("another view on the same transaction sees none of them")
      provider.getComments(bankId, accountId, transactionId)(otherViewId) should equal(Nil)
    }

    Scenario("deleting a comment removes only that comment") {
      val first = add(transactionId, viewId, "keep me").openOrThrowException("added")
      val second = add(transactionId, viewId, "delete me").openOrThrowException("added")

      provider.deleteComment(bankId, accountId, transactionId)(second.id_) should equal(Full(true))

      val left = provider.getComments(bankId, accountId, transactionId)(viewId)
      left.size should equal(1)
      left.head.id_ should equal(first.id_)
    }

    Scenario("bulk delete on a transaction leaves other transactions alone") {
      add(transactionId, viewId, "on transaction one")
      add(otherTransactionId, viewId, "on transaction two")

      provider.bulkDeleteCommentsOnTransaction(bankId, accountId, transactionId) should equal(true)

      provider.getComments(bankId, accountId, transactionId)(viewId) should equal(Nil)
      provider.getComments(bankId, accountId, otherTransactionId)(viewId).size should equal(1)
    }

    Scenario("bulk delete on an account clears every transaction's comments") {
      add(transactionId, viewId, "one")
      add(otherTransactionId, viewId, "two")

      provider.bulkDeleteComments(bankId, accountId) should equal(true)

      provider.getComments(bankId, accountId, transactionId)(viewId) should equal(Nil)
      provider.getComments(bankId, accountId, otherTransactionId)(viewId) should equal(Nil)
    }
  }
}
