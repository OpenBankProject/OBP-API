package code.model.dataAccess

import code.setup.ServerSetup

/**
 * An authuser with no resourceuser attached must still be insertable.
 *
 * `user_c` is a nullable BIGINT - an AuthUser that has not been linked to a ResourceUser yet is a
 * legitimate row, and `AuthUser.insert` says so by binding
 * `${if (row.user > 0L) Some(row.user) else None}`.
 *
 * It does not work. Doobie's `sql` interpolator takes each `${...}` as one parameter with one type,
 * and an inline if whose branches are `Some(Long)` and `None` gives it nothing to fix the type to,
 * so the slot is emitted empty: the statement reaches the database as
 * `VALUES (?, ?, ?, ..., , ?, ?)` and is rejected outright - `Syntax error ... expected "DEFAULT,
 * INTERSECTS (, NOT, EXISTS, UNIQUE"`. Every column in the row is refused, not just user_c.
 *
 * Found while writing AuthUserEmailNormalisationTest: its fixture left `user` at the default 0, and
 * the resulting failure looked like a broken assertion rather than a broken INSERT.
 */
class AuthUserUnboundInsertTest extends ServerSetup {

  Feature("authuser rows that are not linked to a resourceuser") {

    Scenario("insert succeeds and stores no user_c") {
      val stored = AuthUser.insert(AuthUser(
        firstName = "Unbound", lastName = "User",
        email = "unbound@example.com",
        username = "unbound-insert",
        provider = "http://127.0.0.1:8080",
        validated = true))
      // `user` deliberately left at its 0 default - the unlinked case.

      withClue("inserting an authuser with no resourceuser must not fail: ") {
        stored.id should not equal 0L
      }

      val reloaded = AuthUser.findByUsernameAndProvider("unbound-insert", "http://127.0.0.1:8080")
      withClue("the row must be readable back, with no resourceuser attached: ") {
        reloaded.map(_.username).toList should equal(List("unbound-insert"))
        reloaded.map(_.user).toList should equal(List(0L))
      }
    }
  }
}
