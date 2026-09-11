package code.api.util.liquibase

import java.sql.DriverManager
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import code.setup.EnvVarOverride

/**
 * A locked account must not be authenticable through the OIDC credential view.
 *
 * `v_oidc_users` is what OBP-OIDC (`HybridAuthService`) and the Keycloak user-storage provider
 * (`KcUserStorageProvider`, over plain JDBC) authenticate against - they read `password_pw` and
 * `password_slt` from it directly and never call OBP-API over HTTP. So every gate the HTTP login
 * path applies has to be in the view too, or it simply is not applied on that route.
 *
 * `validated` was in the view from the start. The lock was not: an operator who locks an account
 * through `PUT /banks/BANK_ID/users/USERNAME/lock` (or the v5.1.0 equivalent) sees the HTTP path
 * refuse it via `LoginAttempts.userIsLocked`, while the same credentials keep working through
 * OIDC and Keycloak. The legacy hand-run script this view was lifted from
 * (`src/main/scripts/sql/OIDC/cre_v_oidc_users.sql`) carries a TODO saying exactly this.
 *
 * `userIsLocked` is the OR of two independent conditions - a row in `userlocks` (what an operator's
 * lock writes) and `mappedbadloginattempt.mbadattemptssincelastsuccessorreset` exceeding the
 * `max.bad.login.attempts` prop. Nothing writes `userlocks` when the attempt counter overflows -
 * `lockUser` is only ever called from the two explicit admin endpoints - so the two really are
 * separate and the view has to carry both.
 *
 * The second looks unexpressible in a view, since a view cannot read a prop and a hardcoded 5 would
 * drift from any deployment that configured something else. It is expressible, because the view is
 * not static: `createOidcViews` runs on every boot and its changeset is `runOnChange: true`, so the
 * threshold is injected as a Liquibase changelog parameter and the view is rewritten whenever the
 * configured value changes. The last scenario here is the one that proves it - it moves the prop
 * and asserts the boundary moves with it, so a reintroduced hardcoded default fails.
 */
class OidcViewLockedUserTest extends AnyFlatSpec with Matchers with EnvVarOverride {

  private val url = "jdbc:h2:mem:oidc_locked_user;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE"

  private def dataSource(): DataSource = {
    val ds = new org.h2.jdbcx.JdbcDataSource()
    ds.setURL(url); ds.setUser("sa"); ds.setPassword("")
    ds
  }

  private def withConnection[A](f: java.sql.Connection => A): A = {
    val c = DriverManager.getConnection(url, "sa", "")
    try f(c) finally c.close()
  }

  private def execute(sql: String): Unit = withConnection { c =>
    val st = c.createStatement(); try st.execute(sql) finally st.close()
  }

  private def usernamesInView: Set[String] = withConnection { c =>
    val st = c.createStatement()
    try {
      val rs = st.executeQuery("SELECT username FROM v_oidc_users")
      Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toSet
    } finally st.close()
  }

  "the OIDC credential view" should "stop exposing an account once it is locked" in {
    execute("DROP ALL OBJECTS")
    try {
      LiquibaseSchemaSetup.bringUpToDate(dataSource())
      LiquibaseSchemaSetup.createOidcViews(dataSource())

      // A validated user, joined the way the view joins: authuser.user_c -> resourceuser.id.
      execute("INSERT INTO resourceuser (id, userid_, provider_, name_, email) " +
              "VALUES (901, 'uid-locked-901', 'http://127.0.0.1:8080', 'lockme', 'lockme@example.com')")
      execute("INSERT INTO authuser (id, user_c, username, provider, validated, password_pw, password_slt, firstname, lastname, email) " +
              "VALUES (901, 901, 'lockme', 'http://127.0.0.1:8080', TRUE, 'b;hash', 'salt', 'Lock', 'Me', 'lockme@example.com')")

      withClue("a validated, unlocked user must be visible - otherwise the lock assertion below " +
               "would pass for the wrong reason: ") {
        usernamesInView should contain("lockme")
      }

      // What PUT .../users/USERNAME/lock writes: a userlocks row keyed by the resource user's id.
      execute("INSERT INTO userlocks (id, userid, typeoflock, lastlockdate) " +
              "VALUES (901, 'uid-locked-901', 'MANUAL', CURRENT_TIMESTAMP)")

      withClue("a locked account is refused by the HTTP login path, so the OIDC view must not " +
               "hand its password hash out either: ") {
        usernamesInView should not contain "lockme"
      }
    } finally execute("DROP ALL OBJECTS")
  }

  /**
   * Seed a validated user plus a bad-login-attempt row, using the key `userIsLocked` uses:
   * resourceuser's provider_/name_ - the same pair DoobieUserQueries joins this table on, and the
   * pair every `LoginAttempt.userIsLocked(user.provider, user.name)` call site passes.
   */
  private def seedUser(id: Int, username: String, badAttempts: Option[Int]): Unit = {
    val provider = "http://127.0.0.1:8080"
    execute(s"INSERT INTO resourceuser (id, userid_, provider_, name_, email) " +
            s"VALUES ($id, 'uid-$username', '$provider', '$username', '$username@example.com')")
    execute(s"INSERT INTO authuser (id, user_c, username, provider, validated, password_pw, password_slt, firstname, lastname, email) " +
            s"VALUES ($id, $id, '$username', '$provider', TRUE, 'b;hash', 'salt', 'F', 'L', '$username@example.com')")
    badAttempts.foreach { n =>
      execute(s"INSERT INTO mappedbadloginattempt (id, provider, musername, mbadattemptssincelastsuccessorreset) " +
              s"VALUES ($id, '$provider', '$username', $n)")
    }
  }

  it should "stop exposing an account whose bad-login attempts exceeded the configured maximum" in {
    execute("DROP ALL OBJECTS")
    LiquibaseSchemaSetup.bringUpToDate(dataSource())
    LiquibaseSchemaSetup.createOidcViews(dataSource())

    // Default max.bad.login.attempts is 5, and userIsLocked locks on strictly greater than.
    seedUser(910, "attempts_none", None)
    seedUser(911, "attempts_at_limit", Some(5))
    seedUser(912, "attempts_over_limit", Some(6))

    val visible = usernamesInView

    withClue("a user with no recorded attempts must stay visible: ") {
      visible should contain("attempts_none")
    }
    withClue("userIsLocked locks on `> max`, not `>= max`, so exactly max is still not locked - " +
      "the view must not be stricter than the HTTP path: ") {
      visible should contain("attempts_at_limit")
    }
    withClue("over the maximum the HTTP login is refused, so the OIDC view must not expose the " +
      "credentials either - this is the half that used to be missing: ") {
      visible should not contain "attempts_over_limit"
    }
  }

  it should "take the threshold from the configured prop, not a hardcoded default" in {
    execute("DROP ALL OBJECTS")
    LiquibaseSchemaSetup.bringUpToDate(dataSource())

    // 3 attempts: locked under max=2, not locked under the default max=5. A view that hardcodes 5
    // shows this user and fails here; only a view rebuilt from the configured value hides it.
    withEnvOverride("OBP_MAX_BAD_LOGIN_ATTEMPTS" -> "2") {
      LiquibaseSchemaSetup.createOidcViews(dataSource())
      seedUser(920, "three_attempts", Some(3))

      withClue("with max.bad.login.attempts=2 a user on 3 attempts is locked, so the view must " +
        "not expose them - if this passes only under the default 5, the threshold is hardcoded: ") {
        usernamesInView should not contain "three_attempts"
      }
    }
  }
}
