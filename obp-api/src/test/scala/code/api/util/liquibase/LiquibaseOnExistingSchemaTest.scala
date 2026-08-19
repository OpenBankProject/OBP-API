package code.api.util.liquibase

import java.sql.{Connection, DriverManager}
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Turning Liquibase on against a database that already has its tables must not fail.
 *
 * This is the whole upgrade path, and it is the same shape as the one Flyway needed: Schemifier
 * creates nothing any more - ToSchemify.models is Nil - so an existing deployment reaching this
 * build has a schema built by something that left no record of itself, whether that was Schemifier
 * or the Flyway scripts. Liquibase's own record is DATABASECHANGELOG, and on such a database it is
 * absent, so a plain `update` would run every createTable in the baseline against tables that are
 * already there and fail on the first one.
 *
 * Flyway's answer was baselineOnMigrate. Liquibase's is changelogSync, which writes the changesets
 * into DATABASECHANGELOG as applied without running them. `runIfEnabled` has to make that choice
 * itself, from the state of the database, because nothing else is in a position to: a deployment
 * upgrading in place has no opportunity to run a command first.
 *
 * The three paths below are the three states a database can be in when the application boots.
 * The third is the one that is easy to leave out and expensive to get wrong: a boot interrupted
 * part-way leaves a database that is neither empty nor complete, and it has to be able to finish
 * on the next start rather than needing a person.
 */
class LiquibaseOnExistingSchemaTest extends AnyFlatSpec with Matchers {

  private val h2Params = "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE"
  private def urlFor(name: String) = s"jdbc:h2:mem:$name;$h2Params"

  private def dataSourceFor(name: String): DataSource = {
    val ds = new org.h2.jdbcx.JdbcDataSource()
    ds.setURL(urlFor(name))
    ds.setUser("sa")
    ds.setPassword("")
    ds
  }

  private def withConnection[A](name: String)(f: Connection => A): A = {
    val c = DriverManager.getConnection(urlFor(name), "sa", "")
    try f(c) finally c.close()
  }

  private def execute(c: Connection, sql: String): Unit = {
    val st = c.createStatement()
    try st.execute(sql) finally st.close()
  }

  private def scalar(c: Connection, sql: String): Long = {
    val st = c.createStatement()
    try {
      val rs = st.executeQuery(sql)
      rs.next()
      rs.getLong(1)
    } finally st.close()
  }

  private def tableCount(name: String): Long = withConnection(name) { c =>
    // BASE TABLE only: the changelog also creates the three OIDC views, and adoption must not be
    // judged by a count that moves when a view is added.
    scalar(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' " +
      "AND table_type = 'BASE TABLE' " +
      "AND table_name NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK', " +
      "'flyway_schema_history', 'FLYWAY_SCHEMA_HISTORY')")
  }

  private def appliedChangesets(name: String): Long = withConnection(name) { c =>
    scalar(c, "SELECT COUNT(*) FROM DATABASECHANGELOG")
  }

  "an empty database" should "get the whole schema built" in {
    val db = "liquibase_upgrade_empty"
    withConnection(db)(execute(_, "DROP ALL OBJECTS"))
    try {
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))
      withClue("every table must have been created: ") {
        tableCount(db) should equal(146L)
      }
    } finally withConnection(db)(execute(_, "DROP ALL OBJECTS"))
  }

  "a database whose schema nothing recorded" should "be adopted rather than rebuilt" in {
    val db = "liquibase_upgrade_existing"
    withConnection(db)(execute(_, "DROP ALL OBJECTS"))
    try {
      // The state an existing deployment is actually in: every table present, and no record that
      // anything built them. Built here with the changelog and then stripped of the bookkeeping,
      // which reaches that state exactly - and is what a Schemifier-built or Flyway-built database
      // looks like from Liquibase's side, neither of them having left a DATABASECHANGELOG.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))
      withConnection(db) { c =>
        execute(c, "DROP TABLE DATABASECHANGELOG")
        execute(c, "DROP TABLE DATABASECHANGELOGLOCK")
      }
      val before = tableCount(db)
      withClue("the fixture must have built the schema: ") {
        before should equal(146L)
      }

      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      withClue("adoption must not add or drop tables: ") {
        tableCount(db) should equal(before)
      }
      withClue("every changeset must be recorded as applied, so the next boot is a no-op: ") {
        appliedChangesets(db) should be > 400L
      }

      // Idempotence: booting again must be a no-op rather than a second attempt at anything.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))
      tableCount(db) should equal(before)
    } finally withConnection(db)(execute(_, "DROP ALL OBJECTS"))
  }

  "a database left half-built by an interrupted boot" should "be finished on the next start" in {
    val db = "liquibase_upgrade_interrupted"
    withConnection(db)(execute(_, "DROP ALL OBJECTS"))
    try {
      // Stopping Liquibase part-way is what an interrupted boot leaves behind: some changesets
      // applied and recorded, the rest not. `update` with a count reproduces it exactly.
      val partial = LiquibaseSchemaSetup.configure(dataSourceFor(db))
      try partial.update(20, "") finally partial.close()

      val partialTables = tableCount(db)
      withClue("the fixture must have stopped part-way, not at either end: ") {
        partialTables should (be > 0L and be < 146L)
      }

      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      withClue("the remaining changesets must run, without redoing the applied ones: ") {
        tableCount(db) should equal(146L)
      }
    } finally withConnection(db)(execute(_, "DROP ALL OBJECTS"))
  }
}
