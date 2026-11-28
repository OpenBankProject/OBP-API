package code.chat

import code.setup.ServerSetup

import java.util.Date

/**
 * chat_email_digest_state has to be cleared between test classes like every other table.
 *
 * The row it holds is "when this user was last emailed a digest", and the scheduler reads it back
 * to decide whether to skip a user. A row surviving into the next test class therefore suppresses
 * a digest that class expects to be sent - a failure whose appearance depends on which suites share
 * the shard's JVM and in what order, not on either suite.
 *
 * Its two neighbours in the same feature, participant and chatroom, are both in
 * ServerSetup.resetDatabaseForTestClass; this table was added by the merge with develop and was
 * missed. Asserting the reset here rather than trusting the list to stay complete: the cost of
 * being wrong is a test that fails somewhere else entirely.
 */
class ChatEmailDigestStateResetTest extends ServerSetup {

  Feature("chat_email_digest_state participates in the per-class database reset") {

    Scenario("a digest state row written in one class does not survive the reset") {
      val userId = "digest-reset-probe"
      ChatEmailDigestState.recordNotified(userId, new Date())
      withClue("the fixture must actually write, or the assertion below proves nothing: ") {
        ChatEmailDigestState.lastNotifiedAt(userId) should not be empty
      }

      // The same call every test class makes on entry.
      resetDatabaseForTestClass()

      withClue("chat_email_digest_state must be in the reset list, as participant and chatroom " +
        "are - otherwise the row leaks into whichever class runs next: ") {
        ChatEmailDigestState.lastNotifiedAt(userId) shouldBe empty
      }
    }
  }
}
