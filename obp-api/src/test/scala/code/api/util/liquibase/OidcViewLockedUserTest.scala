package code.api.util.liquibase

import java.sql.DriverManager
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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
 * Residual, deliberately not covered here and documented at the view: `userIsLocked` is the OR of
 * two independent conditions - a row in `userlocks` (what an operator's lock writes, and what this
 * closes) and `badloginattempt.mbadattemptssincelastsuccessorreset` exceeding the
 * `max.bad.login.attempts` prop. The second cannot be expressed in a view, because a view has no
 * way to read a prop, and hardcoding the default (5) would silently drift from a deployment that
 * configured a different one. Nothing writes `userlocks` when the attempt counter overflows -
 * `lockUser` is only ever called from the two explicit admin endpoints - so the two really are
 * separate, and only the explicit half is fixable here.
 */
class OidcViewLockedUserTest extends AnyFlatSpec with Matchers {

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
}
