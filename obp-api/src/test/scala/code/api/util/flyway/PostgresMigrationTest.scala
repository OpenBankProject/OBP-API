package code.api.util.flyway

import java.sql.{Connection, DriverManager}
import org.postgresql.ds.PGSimpleDataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The Postgres migration scripts must build the same schema the H2 ones do.
 *
 * `vendorFolder` maps a postgresql driver to db/migration/postgres, and Flyway treats a location
 * with no migrations as nothing to do — so until those scripts existed, a fresh Postgres
 * deployment came up with an empty database and no error. The scripts are translated from the H2
 * ones by scripts/h2_to_postgres_migrations.py; this is what says the translation actually works,
 * rather than that it looks right.
 *
 * It builds a database of its own, migrates it, checks it, and drops it. That needs a reachable
 * Postgres, which CI does not have, so it cancels itself when there is none rather than failing:
 * a developer with Postgres running gets the check, everyone else gets a skip. Point it somewhere
 * else with OBP_TEST_POSTGRES_URL / _USER / _PASSWORD.
 */
class PostgresMigrationTest extends AnyFlatSpec with Matchers {

  private val adminUrl = sys.env.getOrElse("OBP_TEST_POSTGRES_URL",
    "jdbc:postgresql://localhost:5432/postgres")
  private val user = sys.env.getOrElse("OBP_TEST_POSTGRES_USER", sys.props("user.name"))
  private val password = sys.env.getOrElse("OBP_TEST_POSTGRES_PASSWORD", "")

  // A name of its own, so this can never touch a database anybody cares about.
  private val databaseName = "obp_flyway_migration_test"

  private def withAdmin[A](f: Connection => A): A = {
    val c = DriverManager.getConnection(adminUrl, user, password)
    try f(c) finally c.close()
  }

  private def execute(c: Connection, sql: String): Unit = {
    val st = c.createStatement()
    try st.execute(sql) finally st.close()
  }

  private def scalar(c: Connection, sql: String): Int = {
    val st = c.createStatement()
    try {
      val rs = st.executeQuery(sql)
      rs.next()
      rs.getInt(1)
    } finally st.close()
  }

  private def postgresReachable: Boolean =
    try withAdmin(_ => true) catch { case _: Throwable => false }

  private def dataSourceFor(db: String): PGSimpleDataSource = {
    val ds = new PGSimpleDataSource()
    ds.setUrl(adminUrl.replaceAll("/[^/]+$", s"/$db"))
    ds.setUser(user)
    ds.setPassword(password)
    ds
  }

  "the postgres migration scripts" should "build the same schema the h2 ones build" in {
    assume(postgresReachable,
      s"no Postgres at $adminUrl — skipping (set OBP_TEST_POSTGRES_URL to run this)")

    // Defence in depth. databaseName is a literal today, so this cannot fire - but a CREATE
    // DATABASE / DROP DATABASE pair is worth guarding against whatever it becomes later.
    withClue(s"refusing to CREATE/DROP a database that is not disposable: $databaseName ") {
      code.setup.DisposableDatabaseGuard.isDisposable(
        s"jdbc:postgresql://localhost:5432/$databaseName") should equal(true)
    }

    withAdmin { admin =>
      execute(admin, s"DROP DATABASE IF EXISTS $databaseName")
      execute(admin, s"CREATE DATABASE $databaseName")
    }
    try {
      val result = FlywaySchemaSetup.configure(dataSourceFor(databaseName), "postgres").migrate()
      withClue("every script must apply: ") {
        result.migrationsExecuted should be > 100
      }

      val ds = dataSourceFor(databaseName)
      val c = ds.getConnection
      try {
        // The H2 side of this is MigratedTablesExistTest; the count has to agree with it, or the
        // two vendors have drifted apart.
        val tables = scalar(c,
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' " +
            "AND table_name <> 'flyway_schema_history'")
        withClue("table count must match the H2 schema: ") {
          tables should equal(146)
        }

        // Names have to arrive lowercase. A quoted "MAPPEDATM" would be a distinct,
        // case-sensitive name in Postgres, and every query the application issues is unquoted
        // lowercase — the table would exist and never be found.
        val lowercased = scalar(c,
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' " +
            "AND table_name <> lower(table_name)")
        withClue("no table may keep an uppercase name: ") {
          lowercased should equal(0)
        }

        // Lift's MappedText is past Postgres's varchar ceiling; it has to land as TEXT.
        val unboundedText = scalar(c,
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
            "AND data_type = 'text'")
        withClue("MappedText columns must be TEXT on Postgres: ") {
          unboundedText should be > 0
        }

        // The indexes V116-V118 restored have to be here too, not just on H2.
        val restored = scalar(c,
          "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname IN " +
            "('connector_trace_correlationid', 'consent_item_bank_id', " +
            "'mappednarrative_bank_account_transaction_c')")
        withClue("the indexes restored in V116-V118 must exist on Postgres: ") {
          restored should equal(3)
        }
      } finally c.close()
    } finally {
      withAdmin { admin =>
        // Postgres refuses to drop a database with sessions on it; Flyway's are closed by now,
        // but terminate anything left rather than leaving the database behind.
        execute(admin, "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
          s"WHERE datname = '$databaseName' AND pid <> pg_backend_pid()")
        execute(admin, s"DROP DATABASE IF EXISTS $databaseName")
      }
    }
  }
}
