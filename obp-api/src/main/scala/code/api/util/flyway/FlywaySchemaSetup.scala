package code.api.util.flyway

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import org.flywaydb.core.Flyway

/**
 * Flyway schema management, replacing Lift Mapper's Schemifier as the schema authority.
 *
 * Rollout: during the Mapper -> Doobie migration both mechanisms coexist —
 * Flyway runs first (gated by the `flyway.enabled` prop, default false), then
 * Schemifier runs as before. Once the last Mapper entity is migrated, Schemifier
 * is removed and `flyway.enabled` defaults to true.
 *
 * baselineOnMigrate: a pre-existing schema without a flyway_schema_history table is
 * stamped at the baseline version (1), so the V001 initial-schema migration is treated
 * as already applied and only later migrations run. A genuinely empty database gets
 * V001 applied — preserving Schemifier's "start the jar, tables appear" behaviour.
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

  def runIfEnabled(): Unit = {
    if (APIUtil.getPropsAsBoolValue("flyway.enabled", false)) {
      val folder = vendorFolder(APIUtil.driver)
      logger.info(s"Flyway: running migrations from classpath:db/migration/$folder")
      val flyway = Flyway.configure(getClass.getClassLoader)
        .dataSource(APIUtil.vendor.HikariDatasource.ds)
        .locations(s"classpath:db/migration/$folder")
        .baselineOnMigrate(true)
        .load()
      val result = flyway.migrate()
      logger.info(s"Flyway: ${result.migrationsExecuted} migration(s) executed, schema version is now ${Option(result.targetSchemaVersion).getOrElse("(baseline)")}")
    } else {
      logger.info("Flyway: disabled (flyway.enabled=false) — Schemifier remains the schema authority")
    }
  }
}
