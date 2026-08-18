package code.api.util.liquibase

import java.sql.{Connection, DriverManager}
import org.postgresql.ds.PGSimpleDataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The changelog has to build a working schema on Postgres, not only on H2.
 *
 * The rest of the suite runs on H2, so without this the only database ever exercised is the one
 * whose dialect happens to match. That was tolerable when each vendor had its own hand-written
 * script set and a Postgres deployment ran SQL somebody had read; it is not now, because the
 * Postgres DDL is generated from the changelog at boot and nothing else looks at it.
 *
 * What is checked is what actually went wrong when the Postgres scripts were first written:
 *
 *   - identifiers arriving lowercase. A quoted "MAPPEDATM" is a distinct, case-sensitive name in
 *     Postgres, and every query the application issues is unquoted lowercase - the table would
 *     exist and never be found. This is the mirror of the H2 trap, where the folding goes the
 *     other way.
 *   - unbounded text landing as TEXT. Lift's MappedText became CHARACTER VARYING(1000000000) under
 *     H2, which is past Postgres's varchar ceiling of 10485760, so it cannot be carried across
 *     literally; text.type is the changelog property that names each vendor's own.
 *   - the unique indexes restored late in the migration being present here too.
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
  private val databaseName = "obp_liquibase_migration_test"

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

  "the changelog" should "build a usable schema on Postgres" in {
    assume(postgresReachable,
      s"no Postgres at $adminUrl - skipping (set OBP_TEST_POSTGRES_URL to run this)")

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
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(databaseName))

      val c = dataSourceFor(databaseName).getConnection
      try {
        // The H2 side of this is MigratedTablesExistTest; the count has to agree with it, or the
        // two vendors have drifted apart. DATABASECHANGELOG and its lock table are Liquibase's own
        // bookkeeping, not schema.
        val tables = scalar(c,
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' " +
            "AND lower(table_name) NOT IN ('databasechangelog', 'databasechangeloglock')")
        withClue("table count must match the H2 schema: ") {
          tables should equal(146)
        }

        val lowercased = scalar(c,
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' " +
            "AND table_name <> lower(table_name)")
        withClue("no table may keep an uppercase name: ") {
          lowercased should equal(0)
        }

        val unboundedText = scalar(c,
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
            "AND data_type = 'text'")
        withClue("unbounded text columns must be TEXT on Postgres: ") {
          unboundedText should be > 0
        }

        val restored = scalar(c,
          "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname IN " +
            "('connector_trace_correlationid', 'consent_item_bank_id', " +
            "'mappednarrative_bank_account_transaction_c')")
        withClue("the late-restored indexes must exist on Postgres: ") {
          restored should equal(3)
        }
      } finally c.close()
    } finally {
      withAdmin { admin =>
        // Postgres refuses to drop a database with sessions on it; terminate anything left rather
        // than leaving the database behind.
        execute(admin, "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
          s"WHERE datname = '$databaseName' AND pid <> pg_backend_pid()")
        execute(admin, s"DROP DATABASE IF EXISTS $databaseName")
      }
    }
  }
}
