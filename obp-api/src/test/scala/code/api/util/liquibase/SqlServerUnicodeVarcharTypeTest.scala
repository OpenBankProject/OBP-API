package code.api.util.liquibase

import liquibase.database.core.{H2Database, MSSQLDatabase, MySQLDatabase, OracleDatabase, PostgresDatabase}
import liquibase.datatype.DataTypeFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The changelog's VARCHAR becomes NVARCHAR on SQL Server, where VARCHAR cannot hold text outside the code page,
 * and stays VARCHAR everywhere else.
 */
class SqlServerUnicodeVarcharTypeTest extends AnyFlatSpec with Matchers {

  private def rendered(description: String, database: liquibase.database.Database): String =
    DataTypeFactory.getInstance().fromDescription(description, database).toDatabaseDataType(database).toString.toLowerCase

  "VARCHAR(n)" should "be NVARCHAR(n) on SQL Server" in {
    rendered("VARCHAR(255)", new MSSQLDatabase()) should include("nvarchar")
    rendered("VARCHAR(255)", new MSSQLDatabase()) should include("255")
  }

  it should "stay VARCHAR on the other databases" in {
    List(new PostgresDatabase(), new H2Database(), new MySQLDatabase()).foreach { db =>
      withClue(db.getShortName) {
        rendered("VARCHAR(255)", db) should not include "nvarchar"
        rendered("VARCHAR(255)", db) should include("varchar")
      }
    }
    rendered("VARCHAR(255)", new OracleDatabase()) should include("varchar2")
  }

  "DOUBLE" should "be BINARY_DOUBLE on Oracle, which stores a double exactly, and DOUBLE elsewhere" in {
    rendered("DOUBLE", new OracleDatabase()) should equal("binary_double")
    List(new PostgresDatabase(), new H2Database(), new MySQLDatabase(), new MSSQLDatabase()).foreach { db =>
      withClue(db.getShortName) { rendered("DOUBLE", db) should not include "binary_double" }
    }
  }
}
