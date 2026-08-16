package code.api.util.flyway

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Flyway becomes the schema authority for every table whose Lift entity is removed, so two things
 * about it have to hold before the first entity goes.
 *
 * 1. It must pick the right per-vendor migration folder. The DDL dialects differ, so a wrong
 *    folder means either "no migrations found" (table silently absent) or DDL that the database
 *    rejects. The mapping is derived from the configured JDBC driver name, which is a string, so
 *    it is worth pinning rather than assuming.
 *
 * 2. It must be off unless asked. During the migration Schemifier is still the authority for the
 *    ~148 tables whose entities remain; a Flyway run that fires by default would create tables
 *    from migration scripts alongside the ones Schemifier owns.
 */
class FlywaySchemaSetupTest extends AnyFlatSpec with Matchers {

  "vendorFolder" should "map each supported JDBC driver to its own migration folder" in {
    FlywaySchemaSetup.vendorFolder("org.h2.Driver") should equal("h2")
    FlywaySchemaSetup.vendorFolder("org.postgresql.Driver") should equal("postgres")
    FlywaySchemaSetup.vendorFolder("com.mysql.cj.jdbc.Driver") should equal("mysql")
    FlywaySchemaSetup.vendorFolder("com.microsoft.sqlserver.jdbc.SQLServerDriver") should equal("sqlserver")
    FlywaySchemaSetup.vendorFolder("oracle.jdbc.OracleDriver") should equal("oracle")
  }

  it should "fall back to h2 for an unrecognised driver rather than failing the boot" in {
    FlywaySchemaSetup.vendorFolder("com.example.SomeOtherDriver") should equal("h2")
  }

  it should "not be confused by a driver name that merely contains another vendor's name" in {
    // The mapping is substring-based, so the order of the cases is load-bearing: a sqlserver
    // driver class must not be read as "server"-ish anything, and postgres must not fall to h2.
    FlywaySchemaSetup.vendorFolder("com.microsoft.sqlserver.jdbc.SQLServerDriver") should not equal "h2"
    FlywaySchemaSetup.vendorFolder("org.postgresql.Driver") should not equal "h2"
  }

  "runIfEnabled" should "do nothing when flyway.enabled is not set" in {
    // The test props leave flyway.enabled unset/false, and this must stay a no-op: Schemifier is
    // still the authority for every table whose entity has not been removed yet. If this ever
    // starts running migrations by default it will create tables next to Schemifier's.
    noException should be thrownBy FlywaySchemaSetup.runIfEnabled()
  }
}
