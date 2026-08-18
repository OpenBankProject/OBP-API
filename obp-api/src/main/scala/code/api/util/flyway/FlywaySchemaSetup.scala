package code.api.util.flyway

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion

/**
 * Flyway schema management, replacing Lift Mapper's Schemifier as the schema authority.
 *
 * Rollout: the coexistence phase is over. Every Mapper entity is gone and ToSchemify.models is
 * Nil, so Schemifier creates nothing and Flyway is the only thing that can build a schema. The
 * `flyway.enabled` prop therefore defaults to TRUE. While Schemifier was still the authority the
 * gate stopped Flyway creating tables next to it; with nothing to sit next to, "off" does not mean
 * "Schemifier handles it", it means the database has no tables. Set it to false only to take
 * schema management out of the application entirely and run the migrations yourself.
 *
 * baselineOnMigrate: a pre-existing schema without a flyway_schema_history table is stamped as
 * already migrated, at the highest version on the classpath. See `configure` for why that is the
 * highest and not Flyway's default of 1. A genuinely empty database is untouched by baselining
 * and gets every script applied — preserving Schemifier's "start the jar, tables appear"
 * behaviour.
 *
 * Migration scripts live in classpath:db/migration/<vendor>/ because DDL dialects
 * differ per database; the vendor folder is derived from the configured JDBC driver.
 */
object FlywaySchemaSetup extends MdcLoggable {

  /** Map the configured JDBC driver class to the per-vendor migration folder. */
  def vendorFolder(driver: String): String = driver match {
    case d if d.contains("h2")         => "h2"
    case d if d.contains("postgresql") => "postgres"
    case d if d.contains("mysql")      => "mysql"
    case d if d.contains("sqlserver")  => "sqlserver"
    case d if d.contains("oracle")     => "oracle"
    case _                             => "h2"
  }

  /**
   * The Flyway configuration, with the DataSource and vendor folder passed in so a test can run
   * the real configuration against a database it built itself rather than reproducing it.
   */
  private[flyway] def configure(dataSource: javax.sql.DataSource, folder: String): Flyway = {
    val location = s"classpath:db/migration/$folder"

    // Stamp a pre-existing schema as fully migrated, not as version 1.
    //
    // baselineVersion defaults to 1, which was right while V001 was the whole initial schema:
    // baselining there said "what is already in the database is covered" and only later
    // migrations ran. The migration is per-table now - V001 is the ATM table alone and every
    // script after it does its own CREATE TABLE, without IF NOT EXISTS, because these are
    // Schemifier's own exported DDL. Baselining at 1 therefore runs V002 against a database
    // that already has that table and the migration fails. That is every upgrade: Schemifier
    // creates nothing any more (ToSchemify.models is Nil), so flyway.enabled=true against a
    // schema Schemifier built is the only way an existing deployment gets here.
    //
    // The version is read from the scripts rather than written down, so adding a script does
    // not silently leave the baseline behind. MigrationVersion.LATEST cannot be used - Flyway
    // rejects it when writing the baseline row ("Version may only contain 0..9 and . (dot)").
    //
    // An empty schema is unaffected either way: baselineOnMigrate applies only to a non-empty
    // schema that has no history table, so a fresh deployment still runs every script.
    val latestScriptVersion: Option[MigrationVersion] =
      scala.util.Try {
        Flyway.configure(getClass.getClassLoader)
          .dataSource(dataSource)
          .locations(location)
          .load()
          .info().all().toList
          .flatMap(info => Option(info.getVersion))
          .maxOption
      }.toOption.flatten

    val configured = Flyway.configure(getClass.getClassLoader)
      .dataSource(dataSource)
      .locations(location)
      .baselineOnMigrate(true)

    latestScriptVersion.fold(configured)(v => configured.baselineVersion(v)).load()
  }

  /**
   * Whether Flyway runs when `flyway.enabled` is absent from the props.
   *
   * Named rather than inlined so a test can hold it against ToSchemify.models: while that list is
   * empty nothing but Flyway creates a table, so a default of false means a deployment silently
   * gets no schema. That is not hypothetical - the CI props are written from scratch and never
   * mention flyway.enabled, so with the default off every shard aborted on the first table it
   * touched, while a developer whose local props happened to set it stayed green.
   */
  private[flyway] val enabledByDefault: Boolean = true

  def runIfEnabled(): Unit = {
    if (APIUtil.getPropsAsBoolValue("flyway.enabled", enabledByDefault)) {
      val folder = vendorFolder(APIUtil.driver)
      logger.info(s"Flyway: running migrations from classpath:db/migration/$folder")
      val flyway = configure(APIUtil.vendor.HikariDatasource.ds, folder)
      // Only the h2 folder has scripts today. Flyway treats a location with no migrations as
      // nothing to do, so a database whose driver maps elsewhere would boot with no tables and
      // no complaint - Schemifier is not there to create them any more.
      if (flyway.info().all().isEmpty) {
        logger.error(s"Flyway: no migration scripts found in classpath:db/migration/$folder - " +
          s"the schema will not be created. Only the h2 folder is populated; a ${APIUtil.driver} " +
          s"deployment needs its own scripts.")
      }
      val result = flyway.migrate()
      logger.info(s"Flyway: ${result.migrationsExecuted} migration(s) executed, schema version is now ${Option(result.targetSchemaVersion).getOrElse("(baseline)")}")
    } else {
      logger.warn("Flyway: disabled (flyway.enabled=false) — nothing else creates the schema, " +
        "so the database must already have every table this build expects")
    }
  }
}
