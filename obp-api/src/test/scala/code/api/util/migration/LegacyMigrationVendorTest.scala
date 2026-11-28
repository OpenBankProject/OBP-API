package code.api.util.migration

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The MigrationOf* scripts reshape databases that Lift's Schemifier built, and are written for the
 * three vendors such databases ran on: H2, Postgres and SQL Server. A MySQL or Oracle database was
 * never built that way - the Liquibase changelog creates it, already in its final shape - and the
 * scripts' Postgres spelling (`ADD COLUMN IF NOT EXISTS "consumer_id" character varying`) aborts the
 * boot there. So they run on the three vendors they were written for, and on no others.
 */
class LegacyMigrationVendorTest extends AnyFlatSpec with Matchers {

  import Migration.legacyScriptsApplyTo

  "the legacy migration scripts" should "run on the vendors Schemifier-built databases ran on" in {
    legacyScriptsApplyTo("org.h2.Driver") should equal(true)
    legacyScriptsApplyTo("org.postgresql.Driver") should equal(true)
    legacyScriptsApplyTo("com.microsoft.sqlserver.jdbc.SQLServerDriver") should equal(true)
  }

  it should "not run on MySQL, MariaDB or Oracle, whose schema only ever comes from the changelog" in {
    legacyScriptsApplyTo("com.mysql.cj.jdbc.Driver") should equal(false)
    legacyScriptsApplyTo("org.mariadb.jdbc.Driver") should equal(false)
    legacyScriptsApplyTo("oracle.jdbc.OracleDriver") should equal(false)
  }
}
