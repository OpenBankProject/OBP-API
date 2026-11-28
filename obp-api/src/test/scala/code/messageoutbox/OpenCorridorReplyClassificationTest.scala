package code.messageoutbox

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

/**
 * This class tests which Bank Node error replies the outbox relay parks as STICKY at once, because
 * resending the same message cannot change the answer, and which it keeps retrying.
 *
 * Everything here is a pure function call: no broker, no database.
 */
class OpenCorridorReplyClassificationTest extends AnyFeatureSpec with Matchers with GivenWhenThen {

  feature("Bank Node error replies") {

    scenario("A settlement below the rail's minimum output is final, so it goes STICKY at once") {
      MessageOutboxRelay.isStickyOpenCorridorError("OBP-BANK-NODE-SETTLEMENT-BELOW-MINIMUM") shouldBe true
    }

    scenario("Refusals that resending cannot fix are STICKY") {
      List(
        "OBP-BANK-NODE-COMMITMENT-MISMATCH",
        "OBP-BANK-NODE-BAD-MESSAGE",
        "OBP-BANK-NODE-NOT-IMPLEMENTED",
        "OBP-BANK-NODE-SETTLEMENT-NOT-CONFIGURED",
        "OBP-BANK-NODE-CBS-REJECTED"
      ).foreach(code => MessageOutboxRelay.isStickyOpenCorridorError(code) shouldBe true)
    }

    scenario("Transient failures are retried") {
      MessageOutboxRelay.isStickyOpenCorridorError("OBP-BANK-NODE-SETTLEMENT-FAILED") shouldBe false
      MessageOutboxRelay.isStickyOpenCorridorError("OBP-BANK-NODE-CBS-DELIVERY-FAILED") shouldBe false
    }
  }
}
