package code.users

import code.setup.ServerSetup

/**
 * Characterization of the user-init-action provider, written before the implementation moves to
 * Doobie.
 *
 * Nothing in the suite exercises this table - it is fired from AfterApiAuth on every login to
 * record one-off "has this user done X yet" flags (create-or-update-bank, add-entitlement,
 * add-bank-account, ...), and a failure there is only logged, never surfaced to a test.
 *
 * createOrUpdateInitAction is find-then-write on the full (userId, actionName, actionValue)
 * triple: a fresh triple is inserted, an existing one has its success flag and updatedAt
 * refreshed in place rather than adding a row.
 */
class UserInitActionProviderTest extends ServerSetup {

  private def provider = UserInitActionProvider

  private val userA = "user-init-action-test-A"
  private val userB = "user-init-action-test-B"

  Feature("user init action storage") {

    Scenario("a fresh (userId, actionName, actionValue) triple is inserted") {
      val created = provider.createOrUpdateInitAction(userA, "create-or-update-bank", "bank-1", true)
      created.isDefined should equal(true)
      created.openOrThrowException("just created").success should equal(true)
    }

    Scenario("the same triple again updates success in place rather than adding a row") {
      provider.createOrUpdateInitAction(userA, "create-or-update-bank", "bank-1", false)
      val updated = provider.createOrUpdateInitAction(userA, "create-or-update-bank", "bank-1", true)

      updated.openOrThrowException("updated").success should equal(true)
    }

    Scenario("actionValue is part of the key, not just actionName") {
      provider.createOrUpdateInitAction(userA, "add-entitlement", "CanCreateAccount", true)
      val other = provider.createOrUpdateInitAction(userA, "add-entitlement", "CanCreateHistoricalTransactionAtBank", true)

      other.openOrThrowException("distinct action value").actionValue should equal("CanCreateHistoricalTransactionAtBank")
    }

    Scenario("different users with the same action do not collide") {
      provider.createOrUpdateInitAction(userA, "add-bank-account", "cache", true)
      val forB = provider.createOrUpdateInitAction(userB, "add-bank-account", "cache", false)

      forB.openOrThrowException("separate user").success should equal(false)
    }
  }
}
