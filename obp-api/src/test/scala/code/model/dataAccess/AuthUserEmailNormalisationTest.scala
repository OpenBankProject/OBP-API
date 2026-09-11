package code.model.dataAccess

import code.setup.ServerSetup

/**
 * An email stored on authuser is lowercased and trimmed, as Mapper stored it.
 *
 * The Lift entity declared this column as `MappedEmail`, whose `setFilter` is
 * `notNull :: toLower :: trim` - so every write normalised the value, and the entity never said so
 * because the field type did it. The Doobie rewrite carries the column as a plain String and writes
 * whatever it is handed, which is a silent behaviour change: `" Bob@Example.COM "` now persists
 * verbatim where it used to persist `bob@example.com`.
 *
 * The ResourceUser half of the same migration kept the normalisation - `ResourceUser.normalizeEmail`,
 * with a comment naming MappedEmail as the reason - so the two copies of a user's address have been
 * disagreeing about case and whitespace ever since.
 *
 * Not an authentication widening: email is not a login key here, and a mismatch fails closed. It is
 * a data-consistency defect, and the kind that surfaces much later as "the password-reset link says
 * no such user" when the two spellings are compared.
 */
class AuthUserEmailNormalisationTest extends ServerSetup {

  // A real resourceuser FK. Left at the default 0, AuthUser.insert's
  // `${if (row.user > 0L) Some(row.user) else None}` renders an empty parameter and H2 rejects the
  // whole statement - which would make every assertion below fail for a reason that has nothing to
  // do with email.
  private def newResourceUserKey(suffix: String): Long =
    code.model.dataAccess.ResourceUser.insert(
      code.model.dataAccess.ResourceUser(
        userId = s"uid-$suffix",
        provider = "http://127.0.0.1:8080",
        // Distinct per user: resourceuser carries a unique index on (provider_, providerid), and
        // the default "" makes the second insert in this suite collide with the first.
        idGivenByProvider = s"pid-$suffix",
        name = suffix,
        emailAddress = "seed@example.com")).id

  Feature("authuser email is normalised on write, as MappedEmail did") {

    Scenario("insert lowercases and trims the address") {
      val stored = AuthUser.insert(AuthUser(
        firstName = "Bob", lastName = "Bobbington",
        email = "  Bob.Bobbington@Example.COM  ",
        username = "bob-normalise-insert",
        provider = "http://127.0.0.1:8080",
        user = newResourceUserKey("bob-normalise-insert"),
        validated = true))

      withClue("MappedEmail applied notNull :: toLower :: trim on every set; the Doobie write must " +
        "store the same value it used to: ") {
        stored.email should equal("bob.bobbington@example.com")
      }

      // Read it back, so this asserts what the database holds rather than what the case class
      // happened to carry out of insert.
      // findByUsernameAndProvider hands back a Lift Box, so compare the value it carries rather
      // than the wrapper - Full("x") never equals Some("x").
      val reloaded = AuthUser.findByUsernameAndProvider("bob-normalise-insert", "http://127.0.0.1:8080")
      withClue("the row in the database must hold the normalised address: ") {
        reloaded.map(_.email).toList should equal(List("bob.bobbington@example.com"))
      }
    }

    Scenario("update normalises too, not only insert") {
      val created = AuthUser.insert(AuthUser(
        firstName = "Ann", lastName = "Annington",
        email = "ann@example.com",
        username = "ann-normalise-update",
        provider = "http://127.0.0.1:8080",
        user = newResourceUserKey("ann-normalise-update"),
        validated = true))

      AuthUser.update(created.copy(email = "  Ann.NEW@Example.COM  "))

      val reloaded = AuthUser.findByUsernameAndProvider("ann-normalise-update", "http://127.0.0.1:8080")
      withClue("an update writes through the same column and must normalise the same way: ") {
        reloaded.map(_.email).toList should equal(List("ann.new@example.com"))
      }
    }
  }
}
