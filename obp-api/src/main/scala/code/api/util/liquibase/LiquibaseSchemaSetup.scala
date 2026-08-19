package code.api.util.liquibase

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import liquibase.{Contexts, GlobalConfiguration, LabelExpression, Liquibase, Scope}
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.LockException
import liquibase.resource.ClassLoaderResourceAccessor

/**
 * Liquibase schema management, taking the schema over from Flyway.
 *
 * The reason for the change is the shape of the problem rather than any complaint about Flyway.
 * Flyway applies hand-written SQL, so a vendor is supported only once somebody writes its whole
 * script set in its own dialect: it had 118 scripts for h2 and 118 more for postgres, and nothing
 * at all for mysql, sqlserver or oracle - three drivers it named in its vendor mapping and would
 * happily boot against, silently, with no tables. OBP does not choose the database; the bank's
 * data source does. Liquibase describes each change once and generates the dialect per vendor, so
 * those three become configurations that work rather than folders nobody filled in.
 *
 * `liquibase.enabled` defaults to TRUE, because nothing else creates a table: Schemifier creates
 * nothing (ToSchemify.models is Nil) and Flyway is gone. "Off" therefore does not mean "something
 * else handles it", it means the database has no tables - set it to false only to take schema
 * management out of the application entirely and run the migrations yourself. The default is also
 * the CI configuration, since the workflows write their props from scratch and mention no database
 * prop at all; that is how `flyway.enabled` defaulting to false, with Schemifier already empty, put
 * every CI shard on a database with no tables while local runs stayed green off a hand-edited props
 * file. LiquibaseSchemaSetupTest holds the default against ToSchemify.models so the two cannot
 * drift apart again.
 */
object LiquibaseSchemaSetup extends MdcLoggable {

  /**
   * The changelog, as a classpath resource path.
   *
   * One path for every vendor - which is the whole point of the change. There is deliberately no
   * per-vendor selection and no fallback: Flyway needed one, mapping the driver name to a folder
   * and sending anything unrecognised to H2's dialect, whereas Liquibase reads the vendor off the
   * live connection.
   */
  val changeLogPath: String = "db/changelog/db.changelog-master.yaml"

  /**
   * Whether Liquibase runs when `liquibase.enabled` is absent from the props.
   *
   * Named rather than inlined so a test can hold it against ToSchemify.models: while that list is
   * empty nothing but Liquibase creates a table, so a default of false means a deployment silently
   * gets no schema.
   */
  val enabledByDefault: Boolean = true

  /**
   * Run `body` with a duplicate changelog on the classpath treated as a warning, not an error.
   *
   * The duplicate is not contrived - it is the startup OBP-STARTUP-GUIDE.md documents:
   *
   *     java -cp "obp-api/src/main/resources:obp-api/target/obp-api.jar" bootstrap.http4s.Http4sServer
   *
   * The source directory goes first deliberately, so a locally edited default.props takes effect
   * without rebuilding the jar (Lift's Props does not read `-D` flags reliably, so the classpath is
   * the mechanism). The jar also contains everything under src/main/resources, so every resource is
   * there twice. That was harmless under Flyway; Liquibase's parser refuses it outright with
   * "Found 2 files with the path ...", which turns the documented start into a boot failure.
   *
   * The refusal guards against two genuinely DIFFERENT files answering to one path. Here they are
   * the same file reached two ways, and the classpath order already says which is meant - the
   * source directory, which is the copy that start exists to prefer.
   *
   * The cost is worth naming: if the jar is stale relative to src, the warning is the only sign
   * that two versions existed. That is the same trap as the stale target/classes copy in CLAUDE.md,
   * and the same answer - rebuild, or delete the copy you do not mean.
   *
   * Scoped rather than set as a system property, so nothing outside this call is affected.
   */
  private def withDuplicatesAllowed[A](body: => A): A = {
    val settings = new java.util.HashMap[String, Object]()
    settings.put(
      GlobalConfiguration.DUPLICATE_FILE_MODE.getKey,
      GlobalConfiguration.DuplicateFileMode.WARN)
    Scope.child(settings, new Scope.ScopedRunnerWithReturn[A] { def run(): A = body })
  }

  /**
   * The Liquibase instance, with the DataSource passed in so a test can run the real configuration
   * against a database it built itself rather than reproducing the configuration alongside it.
   *
   * The caller owns the connection: Liquibase wraps it and closes it through `close()`, so this
   * hands back both and lets the caller decide the lifetime.
   *
   * The ClassLoader is a parameter only so DuplicateChangelogOnClasspathTest can hand in one that
   * really does hold the changelog twice; every caller uses the default.
   */
  def configure(
    dataSource: javax.sql.DataSource,
    classLoader: ClassLoader = getClass.getClassLoader
  ): Liquibase = {
    val connection = dataSource.getConnection
    val database = DatabaseFactory.getInstance
      .findCorrectDatabaseImplementation(new JdbcConnection(connection))
    new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(classLoader), database)
  }

  /**
   * Schemas every database keeps its own catalogue in. Never the application's.
   *
   * Only a fallback: the schema is normally read off the connection. It matters when a driver
   * returns null from getSchema, because the alternative - searching every schema - counts the
   * database's own catalogue as application tables. On H2 that alone is enough to make an empty
   * database look populated.
   */
  private val systemSchemas =
    Set("information_schema", "sys", "system", "pg_catalog", "sysibm", "mysql",
      "performance_schema")

  /**
   * The lowercased names of the application's tables, read through JDBC metadata.
   *
   * JDBC metadata rather than information_schema because Oracle has no information_schema, and a
   * vendor nobody here runs still working is the entire point of the changeover.
   *
   * Scoped to the connection's own schema, which is not a detail. Searching all schemas returns
   * the database's catalogue too - H2 reports its INFORMATION_SCHEMA tables - so a genuinely empty
   * database looks populated, `bringUpToDate` takes it for an existing deployment, and
   * changelogSync marks all 410 changesets applied against a database with no tables in it. The
   * application then starts against an empty schema with nothing left that would ever build it.
   * That is not hypothetical: it is what this did before LiquibaseOnExistingSchemaTest's
   * empty-database scenario caught it.
   */
  private def existingTableNames(connection: java.sql.Connection): Set[String] = {
    val schema = Option(connection.getSchema).filter(_.nonEmpty)
    val rs = connection.getMetaData.getTables(null, schema.orNull, "%", Array("TABLE"))
    try {
      val names = Set.newBuilder[String]
      while (rs.next()) {
        val table = Option(rs.getString("TABLE_NAME"))
        val inSystemSchema = schema.isEmpty &&
          Option(rs.getString("TABLE_SCHEM")).exists(s => systemSchemas.contains(s.toLowerCase))
        if (!inSystemSchema) table.foreach(n => names += n.toLowerCase)
      }
      names.result()
    } finally rs.close()
  }

  /**
   * Bring the database to the changelog, whatever state it starts in.
   *
   * Three states are possible when the application boots, and the choice between them has to be
   * made here: a deployment upgrading in place has no opportunity to run a command first.
   *
   *   empty                      every changeset runs. A fresh deployment, and every CI run.
   *   tables, no DATABASECHANGELOG   an existing deployment, whose schema was built by Schemifier
   *                              or by the Flyway scripts - neither of which leaves a Liquibase
   *                              record. `update` alone would run the baseline's createTable over
   *                              tables that already exist and fail on the first one, so the
   *                              changesets are marked applied without being run, and only what
   *                              comes after is executed. This is Liquibase's changelogSync, and
   *                              it is the counterpart of Flyway's baselineOnMigrate.
   *   tables and DATABASECHANGELOG   the normal case, including a boot interrupted part-way:
   *                              Liquibase records each changeset as it applies it, so the rest
   *                              simply run on the next start.
   *
   * Adoption assumes the existing schema matches the baseline, exactly as baselineOnMigrate did.
   * It is the same assumption for the same reason - the alternative is refusing to start - and it
   * holds because the baseline is generated from a database the Flyway scripts built. What it does
   * NOT cover is a schema that is older than the baseline in some way nothing recorded; that was
   * equally true of baselining at the highest script version.
   */
  /**
   * Whether a LockException is anywhere in this exception's cause chain.
   *
   * Matched on the chain rather than on the exception itself because `update` runs the change
   * through Liquibase's command layer, which is free to wrap what a step threw - and it does wrap
   * some of them, as the changelog-not-found failure shows (a ChangeLogParseException arriving
   * inside a CommandExecutionException). A `case e: LockException` would then be a message that
   * never prints, which is worse than no message at all, so this holds either way.
   */
  private[liquibase] def causedByLockException(e: Throwable): Boolean = {
    var current: Throwable = e
    var seen = 0
    // Bounded: a cause chain can be self-referential, and this runs on the boot path.
    while (current != null && seen < 20) {
      if (current.isInstanceOf[LockException]) return true
      if (current.getCause eq current) return false
      current = current.getCause
      seen += 1
    }
    false
  }

  /**
   * The context holding the views that must be created after the legacy data migrations.
   *
   * Everything else is created by `bringUpToDate`, which runs first in Boot. These three cannot be:
   * `MigrationOfConsumerAudFieldType` issues `ALTER TABLE consumer ALTER COLUMN aud TYPE text`, and
   * Postgres refuses to alter a column a view depends on -
   *
   *     ERROR: cannot alter type of a column used by a view or rule
   *     Detail: rule _RETURN on view v_oidc_admin_clients depends on column "aud"
   *
   * - which aborts the boot. H2 does not enforce that, so the suite cannot see it; it took starting
   * the application against a fresh Postgres database to find. The four views the legacy scripts
   * create for themselves never hit it, because the mechanism that alters the column is the one
   * that creates them, afterwards.
   */
  private val oidcViewsContext = "oidc-views"

  /**
   * Create the OIDC views. Called from Boot AFTER Migration.database.executeScripts, for the
   * ordering reason on `oidcViewsContext`.
   */
  def createOidcViews(dataSource: javax.sql.DataSource): Unit = {
    if (APIUtil.getPropsAsBoolValue("liquibase.enabled", enabledByDefault)) {
      val liquibase = configure(dataSource)
      try withDuplicatesAllowed {
        liquibase.update(new Contexts(oidcViewsContext), new LabelExpression())
        logger.info("Liquibase: OIDC views are up to date")
      } finally liquibase.close()
    }
  }

  def bringUpToDate(
    dataSource: javax.sql.DataSource,
    classLoader: ClassLoader = getClass.getClassLoader
  ): Unit = {
    val needsAdoption = {
      val c = dataSource.getConnection
      try {
        val tables = existingTableNames(c)
        val bookkeeping =
          Set("databasechangelog", "databasechangeloglock", "flyway_schema_history")
        (tables -- bookkeeping).nonEmpty && !tables.contains("databasechangelog")
      } finally c.close()
    }

    val liquibase = configure(dataSource, classLoader)
    try withDuplicatesAllowed {
      // Everything except the OIDC views, which have to wait for the legacy data migrations.
      val everythingElse = new Contexts(s"!$oidcViewsContext")
      val noLabels = new LabelExpression()
      if (needsAdoption) {
        logger.info("Liquibase: the database already has tables and no DATABASECHANGELOG - " +
          "recording the changelog as applied rather than rebuilding the schema")
        liquibase.changeLogSync(everythingElse, noLabels)
      }
      liquibase.update(everythingElse, noLabels)
      logger.info("Liquibase: schema is up to date")
    } catch {
      case e: Exception if causedByLockException(e) =>
        // A process killed mid-migration leaves its row in DATABASECHANGELOGLOCK, and every later
        // start then waits on a lock whose holder is gone. Say so, with the way out: the default
        // failure is a long silence, which reads as a hang rather than as this.
        logger.error("Liquibase: could not acquire the migration lock. If a previous start was " +
          "killed, DATABASECHANGELOGLOCK still holds its row and no one will release it - clear " +
          "it with `liquibase releaseLocks`, or DELETE FROM DATABASECHANGELOGLOCK, before " +
          "starting again.", e)
        throw e
    } finally {
      liquibase.close()
    }
  }

  def runIfEnabled(): Unit = {
    if (APIUtil.getPropsAsBoolValue("liquibase.enabled", enabledByDefault)) {
      logger.info(s"Liquibase: running migrations from classpath:$changeLogPath")
      bringUpToDate(APIUtil.vendor.HikariDatasource.ds)
    } else {
      logger.warn("Liquibase: disabled (liquibase.enabled=false) - nothing else creates the " +
        "schema, so the database must already have every table this build expects")
    }
  }
}
