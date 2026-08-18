package code.api.util.liquibase

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

/**
 * Liquibase schema management, taking the schema over from Flyway.
 *
 * The reason for the change is the shape of the problem rather than any complaint about Flyway.
 * Flyway applies hand-written SQL, so a vendor is supported only once somebody writes its whole
 * script set in its own dialect: this branch has 118 scripts for h2 and 118 more for postgres, and
 * nothing at all for mysql, sqlserver or oracle - three drivers `FlywaySchemaSetup.vendorFolder`
 * names and would happily boot against, silently, with no tables. OBP does not choose the database;
 * the bank's data source does. Liquibase describes each change once and generates the dialect per
 * vendor, so those three become configurations that work rather than folders nobody filled in.
 *
 * During the changeover both tools are on the classpath and exactly one of them is enabled - two
 * tools creating tables on boot is the one state that must never exist. `liquibase.enabled`
 * therefore defaults to FALSE for as long as Flyway owns the schema, and flips to true in the same
 * commit that removes Flyway. Not before: the CI workflows write their props from scratch and
 * mention no database prop at all, so the code's default IS the CI configuration - which is how
 * `flyway.enabled` defaulting to false, with Schemifier already empty, put every CI shard on a
 * database with no tables while local runs stayed green off a hand-edited props file.
 * LiquibaseSchemaSetupTest pins both halves of that.
 */
object LiquibaseSchemaSetup extends MdcLoggable {

  /**
   * The changelog, as a classpath resource path.
   *
   * One path for every vendor - which is the whole point of the change. There is no counterpart
   * to `FlywaySchemaSetup.vendorFolder` here, and there is deliberately no fallback: Flyway's
   * `case _ => "h2"` sends an unrecognised driver to H2's dialect, and Liquibase reads the vendor
   * off the live connection instead of off a string.
   */
  val changeLogPath: String = "db/changelog/db.changelog-master.yaml"

  /**
   * Whether Liquibase runs when `liquibase.enabled` is absent from the props.
   *
   * False while Flyway is still the authority. Named rather than inlined for the same reason
   * Flyway's is: so the mutual exclusion between the two can be asserted rather than remembered.
   */
  val enabledByDefault: Boolean = false

  /**
   * The Liquibase instance, with the DataSource passed in so a test can run the real configuration
   * against a database it built itself rather than reproducing the configuration alongside it.
   *
   * The caller owns the connection: Liquibase wraps it and closes it through `close()`, so this
   * hands back both and lets the caller decide the lifetime.
   */
  private[liquibase] def configure(dataSource: javax.sql.DataSource): Liquibase = {
    val connection = dataSource.getConnection
    val database = DatabaseFactory.getInstance
      .findCorrectDatabaseImplementation(new JdbcConnection(connection))
    new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(getClass.getClassLoader), database)
  }

  def runIfEnabled(): Unit = {
    if (APIUtil.getPropsAsBoolValue("liquibase.enabled", enabledByDefault)) {
      logger.info(s"Liquibase: running migrations from classpath:$changeLogPath")
      val liquibase = configure(APIUtil.vendor.HikariDatasource.ds)
      try {
        liquibase.update("")
        logger.info("Liquibase: schema is up to date")
      } finally {
        liquibase.close()
      }
    } else {
      logger.info("Liquibase: disabled (liquibase.enabled=false) - Flyway is the schema authority")
    }
  }
}
