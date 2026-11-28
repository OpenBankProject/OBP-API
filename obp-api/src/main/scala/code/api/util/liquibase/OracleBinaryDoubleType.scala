package code.api.util.liquibase

import liquibase.database.Database
import liquibase.database.core.OracleDatabase
import liquibase.datatype.{DataTypeInfo, DatabaseDataType}
import liquibase.datatype.core.DoubleType

/**
 * DOUBLE as Oracle's BINARY_DOUBLE, so that a double is stored exactly as the application wrote it.
 *
 * Liquibase writes DOUBLE on Oracle as a decimal NUMBER type, and a double bound to that comes back rounded to 15
 * significant digits: a latitude of -0.3218636066574957 is read back as -0.321863606657496. BINARY_DOUBLE is the
 * IEEE 754 double the other databases store.
 *
 * Registered in META-INF/services/liquibase.datatype.LiquibaseDataType under the name and aliases of Liquibase's
 * own DoubleType, at a higher priority, and only for Oracle. The changelog is unchanged, so no checksum moves.
 */
@DataTypeInfo(
  name = "double",
  aliases = Array("java.sql.Types.DOUBLE", "java.lang.Double"),
  minParameters = 0,
  maxParameters = 2,
  priority = 5 // LiquibaseDataType.PRIORITY_DATABASE
)
class OracleBinaryDoubleType extends DoubleType {
  override def supports(database: Database): Boolean = database.isInstanceOf[OracleDatabase]

  override def toDatabaseDataType(database: Database): DatabaseDataType = new DatabaseDataType("BINARY_DOUBLE")
}
