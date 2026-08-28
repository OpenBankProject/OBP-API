package code.api.util.liquibase

import java.sql.DriverManager
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The two views the application's own request paths read must exist on a database the changelog
 * built, with no legacy data migrations run at all.
 *
 * That last clause is the whole point. `DoobieConsentQueries` selects FROM v_consent and
 * `DoobieAccountAccessViewQueries` selects FROM v_account_access_with_views, but neither view had
 * anything in the changelog creating it - both were left to MigrationOfConsentView /
 * MigrationOfAccountAccessWithViewsView, which run only when BOTH `migration_scripts.enabled` and
 * `migration_scripts.execute_all` are true. Both default to false and ship commented out in
 * sample.props.template, so a deployment made from the shipped template came up with all 147
 * tables, the three OIDC views, nothing in the log complaining - and then 500ed on the first
 * `GET /obp/v5.1.0/my/consents` with `relation "v_consent" does not exist`, and on every
 * account-access check.
 *
 * The rest of the suite cannot see this: `ServerSetup` forces `migration_scripts.execute_all=true`,
 * so in tests the migration path always creates them, and OidcViewsTest's own comment records the
 * resulting belief that these views "always appeared". They appeared in tests. This test builds the
 * schema the way a default deployment does - `bringUpToDate` then `createOidcViews`, and nothing
 * else - which is the only arrangement that can hold the changelog responsible for them.
 */
class AppViewsTest extends AnyFlatSpec with Matchers {

  private val views = List("v_consent", "v_account_access_with_views")

  private def dataSourceFor(name: String): DataSource = {
    val ds = new org.h2.jdbcx.JdbcDataSource()
    ds.setURL(s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE")
    ds.setUser("sa")
    ds.setPassword("")
    ds
  }

  private def withConnection[A](name: String)(f: java.sql.Connection => A): A = {
    val c = DriverManager.getConnection(
      s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE", "sa", "")
    try f(c) finally c.close()
  }

  "a database built from the changelog alone" should
    "carry the views the request paths read, without any migration script having run" in {
    val db = "app_views"
    withConnection(db) { c =>
      val st = c.createStatement(); try st.execute("DROP ALL OBJECTS") finally st.close()
    }
    try {
      // Boot's order, minus Migration.database.executeScripts - which is exactly what a deployment
      // with the shipped props does, and what used to leave both of these views uncreated.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))
      LiquibaseSchemaSetup.createOidcViews(dataSourceFor(db))

      withConnection(db) { c =>
        val st = c.createStatement()
        try {
          val rs = st.executeQuery(
            "SELECT LOWER(table_name) FROM information_schema.views WHERE table_schema = 'PUBLIC'")
          val found = Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toSet
          views.foreach { v =>
            withClue(s"$v is missing - the request path that selects from it 500s. Found: $found ") {
              found should contain(v)
            }
          }
        } finally st.close()
      }

      // Not merely present: selectable, which is what says the columns underneath still match. A
      // view whose definition has drifted from the tables is created happily and fails on use.
      withConnection(db) { c =>
        views.foreach { v =>
          val st = c.createStatement()
          try {
            noException should be thrownBy st.executeQuery(s"SELECT * FROM $v")
          } finally st.close()
        }
      }
    } finally {
      withConnection(db) { c =>
        val st = c.createStatement(); try st.execute("DROP ALL OBJECTS") finally st.close()
      }
    }
  }
}
