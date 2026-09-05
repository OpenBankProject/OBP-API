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
 * Postgres. Where one is not required it cancels itself rather than failing, so a developer without
 * Postgres running still gets a green suite; where OBP_TEST_POSTGRES_REQUIRED=true - CI, which now
 * runs a Postgres service container - a missing one is a failure, because a cancelled test reports
 * as a pass and this check being silently absent is the exact state it exists to prevent. See
 * PostgresTestTarget. Point it somewhere else with OBP_TEST_POSTGRES_URL / _USER / _PASSWORD.
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
    PostgresTestTarget.requireReachable(postgresReachable, adminUrl)

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
      // Both passes, in Boot's order. The OIDC views are held back from the first one so the legacy
      // migrations can still alter the columns they read - see LiquibaseSchemaSetup.createOidcViews.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(databaseName))
      LiquibaseSchemaSetup.createOidcViews(dataSourceFor(databaseName))

      val c = dataSourceFor(databaseName).getConnection
      try {
        // The H2 side of this is MigratedTablesExistTest; the count has to agree with it, or the
        // two vendors have drifted apart. DATABASECHANGELOG and its lock table are Liquibase's own
        // bookkeeping, not schema.
        // BASE TABLE only: the changelog also creates the three OIDC views.
        val tables = scalar(c,
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' " +
            "AND table_type = 'BASE TABLE' " +
            "AND lower(table_name) NOT IN ('databasechangelog', 'databasechangeloglock')")
        withClue("table count must match the H2 schema: ") {
          tables should equal(147)
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

        val oidcViews = scalar(c,
          "SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'public' " +
            "AND table_name IN ('v_oidc_users', 'v_oidc_clients', 'v_oidc_admin_clients')")
        withClue("the OIDC views must be built on Postgres too - it is the vendor that runs them: ") {
          oidcViews should equal(3)
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

  /**
   * The scenario a hand-written pre-Liquibase view leaves behind: `v_oidc_users.username` frozen
   * as `text` at whatever type `authuser.username` was when the SQL script under
   * src/main/scripts/sql/OIDC ran against it - which on a real, long-lived deployment need not be
   * the `varchar(100)` the current baseline declares for that column. Postgres freezes a view's
   * column types at creation time and refuses to change them on `CREATE OR REPLACE VIEW`:
   *
   *     ERROR: cannot change data type of view column "username" from text to character varying(100)
   *
   * so a changeset whose SELECT reads `authuser.username` unchanged fails on exactly the databases
   * upgrading from that legacy script, and only those - a fresh install never has the frozen view
   * to conflict with, which is why the scenario above didn't catch it. This constructs that state
   * directly (create the legacy-shaped view by hand, matching what the real script produced) rather
   * than trying to reproduce the history that led there, since the state - not its origin - is what
   * `createOidcViews` has to tolerate.
   */
  "createOidcViews" should "replace a legacy-shaped v_oidc_users view without a type-mismatch error" in {
    PostgresTestTarget.requireReachable(postgresReachable, adminUrl)

    val db = "obp_suite_oidc_legacy_view_upgrade"
    withClue(s"refusing to CREATE/DROP a database that is not disposable: $db ") {
      code.setup.DisposableDatabaseGuard.isDisposable(
        s"jdbc:postgresql://localhost:5432/$db") should equal(true)
    }

    withAdmin { admin =>
      execute(admin, s"DROP DATABASE IF EXISTS $db")
      execute(admin, s"CREATE DATABASE $db")
    }
    try {
      // Everything except the OIDC views - authuser.username lands as varchar(100), per the
      // current baseline.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      // The state the legacy SQL script left on a real deployment: a v_oidc_users view whose
      // username column is text, regardless of what authuser.username's type is now.
      val c = dataSourceFor(db).getConnection
      try {
        execute(c,
          """CREATE VIEW v_oidc_users AS
            |SELECT
            |    ru.userid_ AS user_id,
            |    au.username::text AS username,
            |    au.firstname,
            |    au.lastname,
            |    au.email,
            |    au.validated,
            |    au.provider,
            |    au.password_pw,
            |    au.password_slt,
            |    au.createdat,
            |    au.updatedat
            |FROM authuser au
            |INNER JOIN resourceuser ru ON au.user_c = ru.id
            |WHERE au.validated = true""".stripMargin)

        val usernameType = scalar(c,
          "SELECT CASE WHEN data_type = 'text' THEN 1 ELSE 0 END FROM information_schema.columns " +
            "WHERE table_name = 'v_oidc_users' AND column_name = 'username'")
        withClue("the fixture must have built the legacy-shaped view (username as text): ") {
          usernameType should equal(1)
        }
      } finally c.close()

      // The upgrade: applying the oidc-views changeset for the first time against a database
      // that already has this view, shaped the way the legacy script left it. Must not throw.
      noException should be thrownBy LiquibaseSchemaSetup.createOidcViews(dataSourceFor(db))
    } finally {
      withAdmin { admin =>
        execute(admin, "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
          s"WHERE datname = '$db' AND pid <> pg_backend_pid()")
        execute(admin, s"DROP DATABASE IF EXISTS $db")
      }
    }
  }
}
