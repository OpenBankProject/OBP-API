package code.abacrule

import code.setup.ServerSetup

/**
 * Seeding approved hashes at boot records the current code as approved. It must not switch a rule
 * back on that an administrator had deactivated: an approval (a checker's ACTIVATE) makes a rule
 * live, the seed does not.
 */
class AbacRuleSeedApprovalTest extends ServerSetup {

  Feature("recording an approved hash") {

    Scenario("the seed keeps a deactivated rule deactivated") {
      val rule = AbacRule.insert("seed-keeps-inactive", "true", "d", "account-access", isActive = false, createdBy = "u1")

      AbacRule.setApproved(rule.abacRuleId, "hash-1", makeActive = false) shouldBe true

      val stored = AbacRule.findById(rule.abacRuleId).openOrThrowException("the rule must exist")
      stored.isActive shouldBe false
      stored.approvedHash shouldBe Some("hash-1")
    }

    Scenario("an approval makes the rule live") {
      val rule = AbacRule.insert("approval-activates", "true", "d", "account-access", isActive = false, createdBy = "u1")

      AbacRule.setApproved(rule.abacRuleId, "hash-2") shouldBe true

      AbacRule.findById(rule.abacRuleId).openOrThrowException("the rule must exist").isActive shouldBe true
    }
  }
}
