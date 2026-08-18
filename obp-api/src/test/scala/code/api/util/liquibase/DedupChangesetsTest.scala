package code.api.util.liquibase

import java.sql.{Connection, DriverManager}
import javax.sql.DataSource
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.flywaydb.core.Flyway
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The de-duplication changesets have to work on a table that actually holds duplicates.
 *
 * Neither equivalence test can show this. Both build empty databases, where every DELETE here is a
 * no-op and the changesets would pass just as well if their SQL were nonsense. The case that
 * matters is the one they were written for: an existing deployment whose unique index was never
 * created, which therefore accumulated duplicate rows, being brought up to a schema that has the
 * index. Without the DELETE the index cannot be built at all.
 *
 * So this reproduces that: the H2 scripts are applied only up to V115, which leaves the tables
 * without the V116 unique indexes, duplicates are inserted, and the dedup changelog is run on its
 * own - the full master changelog cannot be, because its baseline would try to CREATE TABLE over
 * the tables Flyway just made.
 *
 * The assertion is not merely "one row survives" but "the survivor is the lowest id". That rule is
 * deliberate and load-bearing: the earliest-inserted row is the one most likely to have downstream
 * data already keyed to it, so collapsing onto any other id would orphan it.
 */
class DedupChangesetsTest extends AnyFlatSpec with Matchers {

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

  private def scalarLong(c: Connection, sql: String): Long = {
    val st = c.createStatement()
    try {
      val rs = st.executeQuery(sql)
      rs.next()
      rs.getLong(1)
    } finally st.close()
  }

  private def runDedup(name: String): Unit = {
    val database = DatabaseFactory.getInstance.findCorrectDatabaseImplementation(
      new JdbcConnection(dataSourceFor(name).getConnection))
    val liquibase = new Liquibase("db/changelog/db.changelog-dedup.yaml",
      new ClassLoaderResourceAccessor(getClass.getClassLoader), database)
    try liquibase.update("") finally liquibase.close()
  }

  "the dedup changesets" should "collapse duplicates onto the lowest id, so the index can be built" in {
    val db = "dedup_changesets"
    withConnection(db)(execute(_, "DROP ALL OBJECTS"))
    try {
      // Stop before V116, which is the script that both de-duplicates and creates the index. That
      // is what an existing deployment looks like: the tables are there, the constraint is not.
      val migrated = Flyway.configure(getClass.getClassLoader)
        .dataSource(dataSourceFor(db))
        .locations("classpath:db/migration/h2")
        .target("115")
        .load().migrate()
      withClue("the tables must have been created: ") {
        migrated.migrationsExecuted should be > 100
      }

      withConnection(db) { c =>
        // Three rows sharing a tagid, inserted out of id order so "lowest id" cannot be confused
        // with "inserted first in this test".
        execute(c, """INSERT INTO "MAPPEDTAG" ("ID", "TAGID", "TAG") VALUES (30, 'dup', 'third')""")
        execute(c, """INSERT INTO "MAPPEDTAG" ("ID", "TAGID", "TAG") VALUES (10, 'dup', 'first')""")
        execute(c, """INSERT INTO "MAPPEDTAG" ("ID", "TAGID", "TAG") VALUES (20, 'dup', 'second')""")
        // A NULL tagid must survive: a unique index permits many NULLs, so those rows cannot
        // violate the constraint and deleting them would lose data for nothing.
        execute(c, """INSERT INTO "MAPPEDTAG" ("ID", "TAG") VALUES (40, 'null tag a')""")
        execute(c, """INSERT INTO "MAPPEDTAG" ("ID", "TAG") VALUES (50, 'null tag b')""")

        withClue("the duplicates must be there before the dedup runs: ") {
          scalarLong(c, """SELECT COUNT(*) FROM "MAPPEDTAG" WHERE "TAGID" = 'dup'""") should equal(3L)
        }
      }

      runDedup(db)

      withConnection(db) { c =>
        withClue("exactly one row may survive per tagid: ") {
          scalarLong(c, """SELECT COUNT(*) FROM "MAPPEDTAG" WHERE "TAGID" = 'dup'""") should equal(1L)
        }
        withClue("the survivor must be the lowest id, not an arbitrary one: ") {
          scalarLong(c, """SELECT "ID" FROM "MAPPEDTAG" WHERE "TAGID" = 'dup'""") should equal(10L)
        }
        withClue("rows with a NULL key must be left alone: ") {
          scalarLong(c, """SELECT COUNT(*) FROM "MAPPEDTAG" WHERE "TAGID" IS NULL""") should equal(2L)
        }

        // The point of the exercise: the index V116 creates is now creatable. Before the dedup
        // this statement fails, which is the whole reason the DELETEs exist.
        execute(c, """CREATE UNIQUE INDEX "MAPPEDTAG_TAGID_CHECK" ON "MAPPEDTAG"("TAGID")""")
      }
    } finally {
      withConnection(db)(execute(_, "DROP ALL OBJECTS"))
    }
  }
}
