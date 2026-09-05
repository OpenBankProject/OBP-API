package code.api.util.liquibase

import code.api.util.APIUtil
import code.loginattempts.LoginAttempt
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
 * `liquibase.enabled` defaults to TRUE, because nothing else creates a table: Schemifier is not
 * called anywhere in obp-api any more - the whole net.liftweb.mapper surface, ToSchemify.models
 * included, was removed once the last Mapper entity moved to Doobie - and Flyway is gone too.
 * "Off" therefore does not mean "something else handles it", it means the database has no tables -
 * set it to false only to take schema management out of the application entirely and run the
 * migrations yourself. The default is also the CI configuration, since the workflows write their
 * props from scratch and mention no database prop at all; that is how `flyway.enabled` defaulting
 * to false, with Schemifier already empty, put every CI shard on a database with no tables while
 * local runs stayed green off a hand-edited props file.
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
   * Named rather than inlined so LiquibaseSchemaSetupTest can assert on it directly: nothing but
   * Liquibase creates a table any more, so a default of false means a deployment silently gets no
   * schema.
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
  /**
   * The value `v_oidc_users` compares the bad-login counter against.
   *
   * Read from the same place LoginAttempt reads it, so the view and the HTTP login path cannot
   * disagree about when an account is locked out. Parsed to an Int rather than passed through as
   * the raw prop string for two reasons: it is substituted into DDL, so a string would let a
   * malformed prop become SQL; and a value that cannot be a number is a misconfiguration worth
   * naming here rather than discovering as a NumberFormatException on the next login.
   *
   * A misconfigured value falls back to the prop's own declared default instead of failing the
   * boot. It is not the drift the hardcoding argument was about - there is no configured value to
   * honour in that case - and refusing to start would take down a deployment that today only
   * breaks when somebody logs in.
   */
  private[liquibase] def maxBadLoginAttempts: Int = {
    val configured = LoginAttempt.maxBadLoginAttempts
    configured.trim.toIntOption match {
      case Some(value) => value
      case None =>
        logger.error(s"max.bad.login.attempts is not a number ('$configured'); v_oidc_users will " +
          s"use the default $defaultMaxBadLoginAttempts. LoginAttempt.userIsLocked will throw on " +
          s"this value, so fix the prop.")
        defaultMaxBadLoginAttempts
    }
  }

  private val defaultMaxBadLoginAttempts = 5

  def configure(
    dataSource: javax.sql.DataSource,
    classLoader: ClassLoader = getClass.getClassLoader
  ): Liquibase = {
    val connection = dataSource.getConnection
    val database = DatabaseFactory.getInstance
      .findCorrectDatabaseImplementation(new JdbcConnection(connection))
    val liquibase = new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(classLoader), database)
    // Set here rather than in createOidcViews because parameter substitution happens when the
    // changelog is parsed, and every path parses the whole master changelog - including
    // bringUpToDate, which only filters the oidc-views context out at execution time.
    liquibase.setChangeLogParameter("maxBadLoginAttempts", maxBadLoginAttempts)
    liquibase
  }

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

  /**
   * Bring the database to the changelog, whatever state it starts in.
   *
   * `update`, and nothing else. Every changeset in the baseline carries its own existence
   * precondition - `not tableExists` / `not indexExists`, `onFail: MARK_RAN` - so each one decides
   * for itself whether the object it creates is already there. That makes one code path right for
   * every state a database can be in when the application boots:
   *
   *   empty                          nothing exists, so every changeset runs.
   *   tables, no DATABASECHANGELOG   an existing deployment, whose schema was built by Schemifier
   *                                  or by the Flyway scripts - neither of which leaves a Liquibase
   *                                  record. Each changeset finds its object and records itself
   *                                  without running. What is genuinely absent is created.
   *   tables and DATABASECHANGELOG   the normal case, and a boot interrupted at any point in any of
   *                                  the above: the record says what has run, the preconditions
   *                                  cover whatever the record does not.
   *
   * It used to decide between `update` and `changeLogSync` by looking at whether DATABASECHANGELOG
   * existed. That was wrong in both directions.
   *
   * A blanket `changeLogSync` marks the whole changelog applied on the strength of the tables being
   * there - including the de-duplications and the unique indexes they clear the way for. Schemifier
   * never created those indexes; that is why V057 and V116 existed. So the databases that needed
   * them were exactly the ones that recorded them as done without building them, and were handed
   * back with their duplicate rows and no constraint.
   *
   * And a sync writes DATABASECHANGELOG row by row, committing as it goes, so a start killed during
   * one leaves the table present and short of its rows. The next start saw a DATABASECHANGELOG,
   * concluded the database was already adopted, and ran a plain `update` over objects that were
   * already there - `MigrationFailedException ... Index "METRIC_CONSUMERID" already exists`, on
   * that start and on every one after it. Both are covered by LiquibaseOnExistingSchemaTest.
   *
   * What this still does not cover is a schema that differs from the baseline in a way no
   * precondition looks at - a table that exists with the wrong columns. That was equally true of
   * changeLogSync and of Flyway's baselineOnMigrate before it; the difference is that the failure
   * is now per-object rather than whole-changelog.
   */
  def bringUpToDate(
    dataSource: javax.sql.DataSource,
    classLoader: ClassLoader = getClass.getClassLoader
  ): Unit = {
    val liquibase = configure(dataSource, classLoader)
    try withDuplicatesAllowed {
      // Everything except the OIDC views, which have to wait for the legacy data migrations.
      val everythingElse = new Contexts(s"!$oidcViewsContext")
      val noLabels = new LabelExpression()
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
