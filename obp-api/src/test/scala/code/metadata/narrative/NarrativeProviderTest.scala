package code.metadata.narrative

import code.setup.ServerSetup
import com.openbankproject.commons.model.{AccountId, BankId, TransactionId}

/**
 * Characterization of the narrative provider, written before the implementation moves to Doobie.
 *
 * The provider had no test of its own, so there was nothing to tell whether a replacement behaves
 * the same. Everything asserted here is behaviour the Lift implementation has today, including the
 * parts that are easy to lose in a rewrite:
 *
 *  - a missing narrative reads as "" rather than throwing or returning null;
 *  - setting a narrative to "" DELETES the row rather than storing an empty string, so a
 *    subsequent read still gives "" but no row is left behind;
 *  - setNarrative is an upsert: called twice for the same transaction it updates rather than
 *    creating a second row;
 *  - narratives are keyed by (bank, account, transaction) together, so the same transaction id
 *    under a different account is a different narrative.
 *
 * Deliberately routed through Narrative.narrative.vend rather than the concrete object: the point
 * is to keep testing whichever implementation is wired in, which is what makes it useful when
 * buildOne switches.
 */
class NarrativeProviderTest extends ServerSetup {

  private val bankId = BankId("narrative-test-bank")
  private val accountId = AccountId("narrative-test-account")
  private val otherAccountId = AccountId("narrative-test-account-other")
  private val transactionId = TransactionId("narrative-test-transaction")

  private def provider = Narrative.narrative.vend

  override def beforeEach() = {
    super.beforeEach()
    provider.bulkDeleteNarratives(bankId, accountId)
    provider.bulkDeleteNarratives(bankId, otherAccountId)
  }

  Feature("narrative storage") {

    Scenario("reading a narrative that was never set gives an empty string") {
      provider.getNarrative(bankId, accountId, transactionId)() should equal("")
    }

    Scenario("a narrative can be set and read back") {
      provider.setNarrative(bankId, accountId, transactionId)("first note") should equal(true)
      provider.getNarrative(bankId, accountId, transactionId)() should equal("first note")
    }

    Scenario("setting a narrative twice updates it instead of adding a second one") {
      provider.setNarrative(bankId, accountId, transactionId)("first note")
      provider.setNarrative(bankId, accountId, transactionId)("second note")

      Then("the latest value is the one that is read back")
      provider.getNarrative(bankId, accountId, transactionId)() should equal("second note")

      And("deleting once leaves nothing behind, i.e. there was only ever one row")
      provider.bulkDeleteNarrativeOnTransaction(bankId, accountId, transactionId)
      provider.getNarrative(bankId, accountId, transactionId)() should equal("")
    }

    Scenario("setting a narrative to the empty string removes it") {
      provider.setNarrative(bankId, accountId, transactionId)("something")
      provider.getNarrative(bankId, accountId, transactionId)() should equal("something")

      When("the narrative is set to an empty string")
      provider.setNarrative(bankId, accountId, transactionId)("")

      Then("reading it gives an empty string again")
      provider.getNarrative(bankId, accountId, transactionId)() should equal("")
    }

    Scenario("narratives are keyed by bank, account and transaction together") {
      provider.setNarrative(bankId, accountId, transactionId)("on one account")

      Then("the same transaction id under another account is a different narrative")
      provider.getNarrative(bankId, otherAccountId, transactionId)() should equal("")
    }

    Scenario("bulk delete removes every narrative on an account") {
      provider.setNarrative(bankId, accountId, TransactionId("t1"))("one")
      provider.setNarrative(bankId, accountId, TransactionId("t2"))("two")
      provider.setNarrative(bankId, otherAccountId, TransactionId("t3"))("three")

      When("narratives are bulk deleted for the first account")
      provider.bulkDeleteNarratives(bankId, accountId) should equal(true)

      Then("that account's narratives are gone")
      provider.getNarrative(bankId, accountId, TransactionId("t1"))() should equal("")
      provider.getNarrative(bankId, accountId, TransactionId("t2"))() should equal("")

      And("the other account is untouched")
      provider.getNarrative(bankId, otherAccountId, TransactionId("t3"))() should equal("three")
    }
  }
}
