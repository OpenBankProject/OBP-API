package code.api.util.liquibase

import java.sql.DriverManager
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The views OBP-OIDC and the Keycloak provider read must exist on a database the changelog built.
 *
 * They were the one part of the schema a fresh deployment did not get: created by hand-run scripts
 * under src/main/scripts/sql/OIDC rather than by anything the application does, so a new database
 * came up with every table present and OIDC login broken, with nothing saying why.
 *
 * This file used to add that the four views the MigrationOf* scripts create (v_consent, v_metric,
 * v_account_access_with_views, v_fast_firehose_accounts) "always appeared". They always appeared
 * HERE: ServerSetup forces migration_scripts.execute_all=true, so the test environment always runs
 * the scripts that create them. A deployment made from the shipped props template runs neither
 * (migration_scripts.enabled and .execute_all both default false), and got no v_consent and no
 * v_account_access_with_views - which the request paths select from. Those two are now in the
 * changelog as well, held by AppViewsTest; v_metric and v_fast_firehose_accounts are not read by
 * any request path and are still left to the scripts.
 *
 * They are created in a SECOND Liquibase pass, after the legacy MigrationOf* scripts: those still
 * run `ALTER TABLE consumer ALTER COLUMN aud TYPE text`, and Postgres refuses to alter a column a
 * view depends on, which aborts the boot. H2 does not enforce that - which is why this test cannot
 * catch the ordering itself, and a real Postgres start had to.
 *
 * Only the CREATE VIEW half is automated. The scripts also create a database ROLE and GRANT SELECT
 * to it, and that is deliberately left out: the role name is the deployment's to choose (the
 * scripts use a `:OIDC_USER` placeholder for exactly that reason), and the grant - not the view -
 * is what actually exposes anything. A view adds no access its owner did not already have; it is
 * the GRANT to a separate role that hands another principal the password hash and salt, which is
 * a decision for whoever runs the deployment.
 */
class OidcViewsTest extends AnyFlatSpec with Matchers {

  private val views = List("v_oidc_users", "v_oidc_clients", "v_oidc_admin_clients")

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

  "a database built from the changelog" should "carry the three OIDC views" in {
    val db = "oidc_views"
    withConnection(db) { c =>
      val st = c.createStatement(); try st.execute("DROP ALL OBJECTS") finally st.close()
    }
    try {
      // Boot's order: the schema first, then - after the legacy data migrations, which this test
      // has none of - the OIDC views. Asserting after bringUpToDate alone would say the opposite
      // of what is wanted, since those views are deliberately held back from that pass.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))
      LiquibaseSchemaSetup.createOidcViews(dataSourceFor(db))

      withConnection(db) { c =>
        val st = c.createStatement()
        try {
          val rs = st.executeQuery(
            "SELECT LOWER(table_name) FROM information_schema.views WHERE table_schema = 'PUBLIC'")
          val found = Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toSet
          views.foreach { v =>
            withClue(s"$v is missing - OIDC login cannot work without it. Found: $found ") {
              found should contain(v)
            }
          }
        } finally st.close()
      }

      // Not merely present: selectable, which is what says the columns underneath still match.
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
