package code.api.util.liquibase

import liquibase.database.Database
import liquibase.database.core.MSSQLDatabase
import liquibase.datatype.DataTypeInfo
import liquibase.datatype.core.NVarcharType

/**
 * VARCHAR as SQL Server's NVARCHAR, so that text outside the server's code page is stored as written rather
 * than as question marks.
 *
 * The changelog says VARCHAR(n), which is right for the other databases: they store it in their own encoding,
 * UTF-8 on Postgres, MySQL and Oracle as the suite runs them. SQL Server's VARCHAR is limited to the database's
 * code page, and the Mapper driver this schema replaced created NVARCHAR columns there for that reason.
 *
 * Registered in META-INF/services/liquibase.datatype.LiquibaseDataType under the name and aliases of
 * Liquibase's own VarcharType, at a higher priority, and only for SQL Server. Liquibase computes a changeset's
 * checksum from the changelog, not from the SQL it generates, so no checksum changes.
 */
@DataTypeInfo(
  name = "varchar",
  aliases = Array("java.sql.Types.VARCHAR", "java.lang.String", "varchar2", "character varying"),
  minParameters = 0,
  maxParameters = 1,
  priority = 5 // LiquibaseDataType.PRIORITY_DATABASE
)
class SqlServerUnicodeVarcharType extends NVarcharType {
  override def supports(database: Database): Boolean = database.isInstanceOf[MSSQLDatabase]
}
