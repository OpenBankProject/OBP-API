package code.api.util.liquibase

import java.sql.{Connection, DriverManager}
import scala.jdk.CollectionConverters._
import liquibase.database.{Database, DatabaseFactory}
import liquibase.database.jvm.JdbcConnection
import liquibase.diff.{DiffGeneratorFactory, DiffResult}
import liquibase.diff.compare.CompareControl
import liquibase.structure.DatabaseObject
import liquibase.structure.core.{Column, Index, PrimaryKey, Table, UniqueConstraint}
import org.postgresql.ds.PGSimpleDataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The changelog must build the schema the Flyway scripts build - not one that resembles it.
 *
 * The 118 Postgres scripts are a translation of the 118 H2 ones, which are in turn Schemifier's
 * own exported DDL, verbatim. That lineage is the reason the schema can be trusted, and a
 * hand-written changelog would throw it away and bet the production schema on somebody's type
 * mapping being right. So the changelog is generated from a database the scripts built, and this
 * is what says the generation actually preserved it.
 *
 * The comparison is Liquibase's own `diff` rather than a query written here: it already knows
 * how each vendor reports its metadata, which is the part that does not survive being written
 * once and pointed at five databases. The reference side is the Flyway-built database, so a
 * "missing" object is one the changelog failed to create.
 *
 * Both databases are built and dropped by this test, under names the DisposableDatabaseGuard
 * whitelist admits. It needs a reachable Postgres, which CI does not have, so it cancels itself
 * rather than failing when there is none.
 */
class SchemaEquivalenceTest extends AnyFlatSpec with Matchers {

  private val adminUrl = sys.env.getOrElse("OBP_TEST_POSTGRES_URL",
    "jdbc:postgresql://localhost:5432/postgres")
  private val user = sys.env.getOrElse("OBP_TEST_POSTGRES_USER", sys.props("user.name"))
  private val password = sys.env.getOrElse("OBP_TEST_POSTGRES_PASSWORD", "")

  private val flywayDb = "obp_suite_schema_equivalence_flyway"
  private val liquibaseDb = "obp_suite_schema_equivalence_liquibase"

  /** Each tool's own bookkeeping. Present on one side only, by definition, and not schema. */
  private val bookkeeping =
    Set("flyway_schema_history", "databasechangelog", "databasechangeloglock")

  private def withAdmin[A](f: Connection => A): A = {
    val c = DriverManager.getConnection(adminUrl, user, password)
    try f(c) finally c.close()
  }

  private def execute(c: Connection, sql: String): Unit = {
    val st = c.createStatement()
    try st.execute(sql) finally st.close()
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

  private def createFresh(db: String): Unit = withAdmin { admin =>
    withClue(s"refusing to CREATE/DROP a database that is not disposable: $db ") {
      code.setup.DisposableDatabaseGuard.isDisposable(
        s"jdbc:postgresql://localhost:5432/$db") should equal(true)
    }
    execute(admin, "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
      s"WHERE datname = '$db' AND pid <> pg_backend_pid()")
    execute(admin, s"DROP DATABASE IF EXISTS $db")
    execute(admin, s"CREATE DATABASE $db")
  }

  private def drop(db: String): Unit = withAdmin { admin =>
    execute(admin, "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
      s"WHERE datname = '$db' AND pid <> pg_backend_pid()")
    execute(admin, s"DROP DATABASE IF EXISTS $db")
  }

  private def databaseFor(db: String): Database =
    DatabaseFactory.getInstance.findCorrectDatabaseImplementation(
      new JdbcConnection(dataSourceFor(db).getConnection))

  /** The name a diff entry should be reported under, for a message worth reading. */
  private def describe(o: DatabaseObject): String = o match {
    case t: Table  => s"table ${t.getName}"
    case c: Column => s"column ${Option(c.getRelation).map(_.getName + ".").getOrElse("")}${c.getName}"
    case i: Index  => s"index ${i.getName}"
    case u: UniqueConstraint => s"unique constraint ${u.getName}"
    case p: PrimaryKey => s"primary key ${p.getName}"
    case other     => s"${other.getClass.getSimpleName} ${other.getName}"
  }

  /** Drop the two tools' own tables, and the columns and indexes hanging off them. */
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
    // Catalog and Schema always differ - the two databases have different names.
    relevant(raw.filterNot { o =>
      val n = o.getClass.getSimpleName
      n == "Catalog" || n == "Schema"
    })
  }

  "the changelog" should "build the same schema the Flyway scripts build" in {
    assume(postgresReachable,
      s"no Postgres at $adminUrl - skipping (set OBP_TEST_POSTGRES_URL to run this)")

    createFresh(flywayDb)
    createFresh(liquibaseDb)
    try {
      val migrated = code.api.util.flyway.FlywaySchemaSetup
        .configure(dataSourceFor(flywayDb), "postgres").migrate()
      withClue("the reference side must actually have been built: ") {
        migrated.migrationsExecuted should be > 100
      }

      // Not named `liquibase`: that shadows the `liquibase.*` package, and the diff below then
      // resolves to this instance's own diff method instead.
      val migration = LiquibaseSchemaSetup.configure(dataSourceFor(liquibaseDb))
      try migration.update("") finally migration.close()

      val reference = databaseFor(flywayDb)
      val comparison = databaseFor(liquibaseDb)
      try {
        val diff: DiffResult = DiffGeneratorFactory.getInstance
          .compare(reference, comparison, CompareControl.STANDARD)

        val missing = objectsOf(diff, "missing")
        val unexpected = objectsOf(diff, "unexpected")
        val changed = objectsOf(diff, "changed")

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
      drop(flywayDb)
      drop(liquibaseDb)
    }
  }
}
