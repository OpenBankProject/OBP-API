package code.api.util.liquibase

import java.sql.{Connection, DriverManager}
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Turning Liquibase on against a database that already has its tables must not fail.
 *
 * This is the whole upgrade path, and it is the same shape as the one Flyway needed: Schemifier is
 * not called anywhere in obp-api any more - the whole net.liftweb.mapper surface was removed once
 * the last Mapper entity moved to Doobie - so an existing deployment reaching this build has a
 * schema built by something that left no record of itself, whether that was Schemifier or the
 * Flyway scripts. Liquibase's own record is DATABASECHANGELOG, and on such a database it is
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
        tableCount(db) should equal(147L)
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
        before should equal(147L)
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

  /**
   * A copy of `from`, in a database Liquibase has not touched in this JVM.
   *
   * It has to be a database it has not touched, because Liquibase keeps the list of applied
   * changesets per database inside the process: after a run against one, a later run against the
   * same one answers from that list rather than from DATABASECHANGELOG. A fixture that edits the
   * table behind its back is then invisible - `update` reports "Database is up to date" and does
   * nothing, in a JVM where the same edit against a fresh process aborts the boot. Copying the
   * schema into a database with no history in this JVM is what makes an in-suite test see what a
   * restarted application would.
   */
  private def cloneSchema(from: String, to: String): Unit = {
    val script = java.io.File.createTempFile(s"liquibase-clone-$to-", ".sql")
    try {
      withConnection(from)(execute(_, s"SCRIPT TO '${script.getAbsolutePath}'"))
      withConnection(to)(execute(_, "DROP ALL OBJECTS"))
      withConnection(to)(execute(_, s"RUNSCRIPT FROM '${script.getAbsolutePath}'"))
    } finally script.delete()
  }

  "a database whose adoption was interrupted" should "be adopted the rest of the way" in {
    val source = "liquibase_interrupted_adoption_source"
    val db = "liquibase_interrupted_adoption"
    try {
      // An adoption writes DATABASECHANGELOG row by row and commits as it goes, so a start killed
      // during one leaves the table present and short of its rows. That is a different state from
      // an interrupted `update`: there the missing changesets have not run, here their objects are
      // already in the database, put there by whatever built it before Liquibase arrived.
      withConnection(source)(execute(_, "DROP ALL OBJECTS"))
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(source))
      cloneSchema(source, db)

      val fullyAdopted = appliedChangesets(db)
      withConnection(db)(execute(_,
        "DELETE FROM DATABASECHANGELOG WHERE ID IN " +
        "(SELECT ID FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED DESC LIMIT 50)"))
      withClue("the fixture must have left the record short, neither emptied nor complete: ") {
        appliedChangesets(db) should (be > 0L and be < fullyAdopted)
      }

      // The decision used to key off DATABASECHANGELOG merely existing, so a half-written one sent
      // the next start down the plain-`update` path - which tried to create objects that were
      // already there and aborted the boot. Verified against a real restart before it was fixed:
      // `MigrationFailedException ... create-index-metric_consumerid`, on every subsequent start.
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      withClue("the tables must be left alone: ") {
        tableCount(db) should equal(147L)
      }
      withClue("the record must be complete again, so the next boot is a no-op: ") {
        appliedChangesets(db) should equal(fullyAdopted)
      }
    } finally {
      withConnection(db)(execute(_, "DROP ALL OBJECTS"))
      withConnection(source)(execute(_, "DROP ALL OBJECTS"))
    }
  }

  "adopting a schema that is missing a unique index" should "build the index rather than record it as done" in {
    val source = "liquibase_missing_index_source"
    val db = "liquibase_missing_index"
    try {
      // The state the de-duplication changesets exist for. Schemifier never created these unique
      // indexes - that is why V057 and V116 had to add them - so a database reaching this build
      // from Schemifier has the tables, holds duplicate rows, and has no index. Recording the whole
      // changelog as applied hands it back unchanged: the changesets that would de-duplicate it and
      // build the index are marked done without either happening, so the databases that need them
      // are exactly the ones that skip them.
      withConnection(source)(execute(_, "DROP ALL OBJECTS"))
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(source))
      cloneSchema(source, db)

      withConnection(db) { c =>
        execute(c, "DROP TABLE DATABASECHANGELOG")
        execute(c, "DROP TABLE DATABASECHANGELOGLOCK")
        execute(c, "DROP INDEX accountidmapping_maccountplaintextreference")
        execute(c, "INSERT INTO accountidmapping (id, maccountid, maccountplaintextreference) VALUES (1, 'a1', 'ref-1')")
        execute(c, "INSERT INTO accountidmapping (id, maccountid, maccountplaintextreference) VALUES (2, 'a2', 'ref-1')")
      }
      withClue("the fixture must start with the duplicates it is about: ") {
        withConnection(db)(scalar(_, "SELECT COUNT(*) FROM accountidmapping")) should equal(2L)
      }

      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      withClue("the duplicate must be collapsed, keeping the lowest id: ") {
        withConnection(db)(scalar(_, "SELECT COUNT(*) FROM accountidmapping")) should equal(1L)
        withConnection(db)(scalar(_, "SELECT id FROM accountidmapping")) should equal(1L)
      }
      withClue("the unique index the de-duplication clears the way for must exist: ") {
        withConnection(db)(scalar(_,
          "SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema = 'PUBLIC' " +
          "AND UPPER(index_name) = 'ACCOUNTIDMAPPING_MACCOUNTPLAINTEXTREFERENCE'")) should equal(1L)
      }
    } finally {
      withConnection(db)(execute(_, "DROP ALL OBJECTS"))
      withConnection(source)(execute(_, "DROP ALL OBJECTS"))
    }
  }

  "adopting a schema whose natural-key duplicates were never collapsed" should "collapse them and build the unique index" in {
    val source = "liquibase_entitlement_dup_source"
    val db = "liquibase_entitlement_dup"
    try {
      // mappedentitlement and mapperaccountholders carry a unique index on a natural key, and the
      // rows that violate it are exactly what an existing deployment brings. The changelog's
      // de-duplications did not cover these two: Boot called
      // Migration.database.deduplicateBeforeUniqueIndexSchemify() for them instead, on the stated
      // grounds that it had to happen before schemifyAll() issued the CREATE UNIQUE INDEX. Neither
      // half of that holds any more - schemifyAll() itself is gone (renamed createDefaultChatRoom,
      // its Schemifier.schemify call removed along with the rest of obp-api's net.liftweb.mapper
      // surface), and the index comes from Liquibase, which Boot runs FOURTEEN LINES EARLIER. So the
      // de-duplication ran after the index it was there to make creatable.
      //
      // It also named the wrong table: `mapperaccountholder`, where the table is
      // `mapperaccountholders`, and `user_` where the column is `user_c`. tableExistsByName said
      // no and the call returned silently, so that half had never run at all.
      withConnection(source)(execute(_, "DROP ALL OBJECTS"))
      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(source))
      cloneSchema(source, db)

      withConnection(db) { c =>
        execute(c, "DROP TABLE DATABASECHANGELOG")
        execute(c, "DROP TABLE DATABASECHANGELOGLOCK")
        execute(c, "DROP INDEX mappedentitlement_mbankid_muserid_mrolename")
        execute(c, "DROP INDEX mapperaccountholders_user_c_accountbankpermalink_accountpermali")
        execute(c, "INSERT INTO mappedentitlement (id, mbankid, muserid, mrolename, mentitlementid) " +
                   "VALUES (1, 'gh.29.uk', 'u-1', 'CanGetAnyUser', 'e-1')")
        execute(c, "INSERT INTO mappedentitlement (id, mbankid, muserid, mrolename, mentitlementid) " +
                   "VALUES (2, 'gh.29.uk', 'u-1', 'CanGetAnyUser', 'e-2')")
        execute(c, "INSERT INTO mapperaccountholders (id, user_c, accountbankpermalink, accountpermalink) " +
                   "VALUES (1, 10, 'gh.29.uk', 'acc-1')")
        execute(c, "INSERT INTO mapperaccountholders (id, user_c, accountbankpermalink, accountpermalink) " +
                   "VALUES (2, 10, 'gh.29.uk', 'acc-1')")
      }

      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      withClue("the duplicate entitlement must be collapsed, keeping the lowest id: ") {
        withConnection(db)(scalar(_, "SELECT COUNT(*) FROM mappedentitlement")) should equal(1L)
        withConnection(db)(scalar(_, "SELECT id FROM mappedentitlement")) should equal(1L)
      }
      withClue("the duplicate account holder must be collapsed, keeping the lowest id: ") {
        withConnection(db)(scalar(_, "SELECT COUNT(*) FROM mapperaccountholders")) should equal(1L)
        withConnection(db)(scalar(_, "SELECT id FROM mapperaccountholders")) should equal(1L)
      }
      withClue("both unique indexes must exist afterwards: ") {
        withConnection(db)(scalar(_,
          "SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema = 'PUBLIC' " +
          "AND UPPER(index_name) IN ('MAPPEDENTITLEMENT_MBANKID_MUSERID_MROLENAME', " +
          "'MAPPERACCOUNTHOLDERS_USER_C_ACCOUNTBANKPERMALINK_ACCOUNTPERMALI')")) should equal(2L)
      }
    } finally {
      withConnection(db)(execute(_, "DROP ALL OBJECTS"))
      withConnection(source)(execute(_, "DROP ALL OBJECTS"))
    }
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
        partialTables should (be > 0L and be < 147L)
      }

      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db))

      withClue("the remaining changesets must run, without redoing the applied ones: ") {
        tableCount(db) should equal(147L)
      }
    } finally withConnection(db)(execute(_, "DROP ALL OBJECTS"))
  }
}
