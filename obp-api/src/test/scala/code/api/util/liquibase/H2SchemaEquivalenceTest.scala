package code.api.util.liquibase

import java.sql.{Connection, DriverManager}
import javax.sql.DataSource
import scala.jdk.CollectionConverters._
import liquibase.database.{Database, DatabaseFactory}
import liquibase.database.jvm.JdbcConnection
import liquibase.diff.{DiffGeneratorFactory, DiffResult}
import liquibase.diff.compare.CompareControl
import liquibase.structure.DatabaseObject
import liquibase.structure.core.{Column, Index, PrimaryKey, Table, UniqueConstraint}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The H2 half of the equivalence proof, and the one that runs everywhere.
 *
 * SchemaEquivalenceTest does the same thing on Postgres and cancels itself when there is no server,
 * so on CI it never runs. H2 is in-memory, which makes this the only version of the check that is
 * actually enforced on every build - and H2 is where the schema for the whole test suite comes
 * from, so it is also the one that matters most.
 *
 * It is not a formality on top of the Postgres run, because the identifier-quoting trap is
 * MIRRORED here and the two databases fail in opposite directions. The baseline changelog is
 * generated from Postgres, so it holds lowercase names. H2 stores identifiers uppercase and folds
 * an unquoted query to uppercase to match. So:
 *
 *   - unquoted `mappedatm` in the changelog  -> H2 folds it to MAPPEDATM, which is what the Flyway
 *     H2 scripts create as quoted "MAPPEDATM", and what the application's unquoted queries find.
 *   - quoted `"mappedatm"`                   -> a distinct, case-sensitive lowercase table that
 *     every query the application issues folds past. The table exists and is never found.
 *
 * Which of those Liquibase emits depends on its object-quoting strategy, so it is asserted rather
 * than assumed - by diffing against the database the H2 scripts build, where a case mismatch shows
 * up as every table being simultaneously missing and unexpected.
 *
 * NON_KEYWORDS=VALUE is required in the URL and is a new dependency, not an inherited one: the
 * Flyway H2 scripts quote every identifier, so a `"VALUE"` column never met the keyword; the
 * changelog's unquoted `value` does. It is already in test.default.props and in the sample
 * template, but an H2 deployment whose URL lacks it will fail at CREATE TABLE.
 */
class H2SchemaEquivalenceTest extends AnyFlatSpec with Matchers {

  private val h2Params = "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE"
  private def urlFor(name: String) = s"jdbc:h2:mem:$name;$h2Params"

  private val bookkeeping =
    Set("flyway_schema_history", "databasechangelog", "databasechangeloglock")

  /** A DataSource over a named in-memory database; H2 keeps it alive via DB_CLOSE_DELAY=-1. */
  private def dataSourceFor(name: String): DataSource = {
    val ds = new org.h2.jdbcx.JdbcDataSource()
    ds.setURL(urlFor(name))
    ds.setUser("sa")
    ds.setPassword("")
    ds
  }

  private def databaseFor(name: String): Database =
    DatabaseFactory.getInstance.findCorrectDatabaseImplementation(
      new JdbcConnection(dataSourceFor(name).getConnection))

  private def describe(o: DatabaseObject): String = o match {
    case t: Table  => s"table ${t.getName}"
    case c: Column => s"column ${Option(c.getRelation).map(_.getName + ".").getOrElse("")}${c.getName}"
    case i: Index  => s"index ${i.getName}"
    case u: UniqueConstraint => s"unique constraint ${u.getName}"
    case p: PrimaryKey => s"primary key ${p.getName}"
    case other     => s"${other.getClass.getSimpleName} ${other.getName}"
  }

  /** Drop each tool's own tables, and whatever hangs off them. */
  private def relevant(objects: List[DatabaseObject]): List[DatabaseObject] = objects.filter {
    case t: Table  => !bookkeeping.contains(t.getName.toLowerCase)
    case c: Column => !Option(c.getRelation).exists(r => bookkeeping.contains(r.getName.toLowerCase))
    case i: Index  => !Option(i.getRelation).exists(r => bookkeeping.contains(r.getName.toLowerCase))
    case u: UniqueConstraint =>
      !Option(u.getRelation).exists(r => bookkeeping.contains(r.getName.toLowerCase))
    case p: PrimaryKey =>
      !Option(p.getTable).exists(t => bookkeeping.contains(t.getName.toLowerCase))
    case _ => true
  }

  private def objectsOf(diff: DiffResult, kind: String): List[DatabaseObject] = {
    val raw = kind match {
      case "missing"    => diff.getMissingObjects.asScala.toList
      case "unexpected" => diff.getUnexpectedObjects.asScala.toList
      case _            => diff.getChangedObjects.asScala.keys.toList
    }
    relevant(raw.filterNot { o =>
      val n = o.getClass.getSimpleName
      n == "Catalog" || n == "Schema"
    })
  }

  private def dropAll(name: String): Unit = {
    val c: Connection = DriverManager.getConnection(urlFor(name), "sa", "")
    try {
      val st = c.createStatement()
      try st.execute("DROP ALL OBJECTS") finally st.close()
    } finally c.close()
  }

  "the changelog" should "build on H2 the schema the H2 scripts build" in {
    val flywayDb = "equivalence_flyway"
    val liquibaseDb = "equivalence_liquibase"
    dropAll(flywayDb)
    dropAll(liquibaseDb)
    try {
      val migrated = code.api.util.flyway.FlywaySchemaSetup
        .configure(dataSourceFor(flywayDb), "h2").migrate()
      withClue("the reference side must actually have been built: ") {
        migrated.migrationsExecuted should be > 100
      }

      LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(liquibaseDb))

      val reference = databaseFor(flywayDb)
      val comparison = databaseFor(liquibaseDb)
      try {
        val diff = DiffGeneratorFactory.getInstance
          .compare(reference, comparison, CompareControl.STANDARD)

        val missing = objectsOf(diff, "missing")
        val unexpected = objectsOf(diff, "unexpected")
        val changed = objectsOf(diff, "changed")

        // A quoting mismatch shows up here as every table being missing AND unexpected at once,
        // so the two messages are worth reading together when this fails.
        withClue(s"the changelog did not create ${missing.size} object(s) the scripts do: " +
          missing.take(25).map(describe).mkString(", ") + " ") {
          missing shouldBe empty
        }
        withClue(s"the changelog created ${unexpected.size} object(s) the scripts do not: " +
          unexpected.take(25).map(describe).mkString(", ") + " ") {
          unexpected shouldBe empty
        }
        withClue(s"${changed.size} object(s) differ between the two: " +
          changed.take(25).map(describe).mkString(", ") + " ") {
          changed shouldBe empty
        }
      } finally {
        reference.close()
        comparison.close()
      }
    } finally {
      dropAll(flywayDb)
      dropAll(liquibaseDb)
    }
  }
}
