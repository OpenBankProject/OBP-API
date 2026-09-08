package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.dynamicResourceDoc.DynamicResourceDoc
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

/**
 * description, tags and roles of a Dynamic Resource Doc were varchar(255). A prose description written
 * for the API Explorer, or a comma-joined list of roles, routinely exceeds that and the create failed with
 * a bare "value too long for type character varying(255)". The mapper now declares them as text; this
 * widens the columns on databases created before that change.
 *
 * Column names are taken from the mapper rather than spelled out: Lift appends `_c` to any field whose
 * name is a reserved word, so `Roles` is stored as `roles_c`, not `roles`.
 *
 * The migration is recorded as successful only when every ALTER actually ran. A failure is logged with
 * isSuccessful = false and the exception text, and boot continues; because runOnce skips a script only
 * once it is logged as executed, a failed run is retried on the next start.
 */
object MigrationOfDynamicResourceDocTextFieldsLength extends MdcLoggable {
  def alterColumnsType(name: String): Boolean = {
    DbFunction.tableExists(DynamicResourceDoc) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false
        val sqlLog = new StringBuilder

        val table = DynamicResourceDoc._dbTableNameLC
        val columns = List(
          DynamicResourceDoc.Description.dbColumnName,
          DynamicResourceDoc.Tags.dbColumnName,
          DynamicResourceDoc.Roles.dbColumnName
        )
        val isSqlServer = APIUtil.getPropsValue("db.driver") match {
          case Full(dbDriver) => dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")
          case _ => false
        }
        val statements: List[String] = columns.map { column =>
          if (isSqlServer) s"ALTER TABLE $table ALTER COLUMN $column VARCHAR(MAX);"
          else s"ALTER TABLE $table ALTER COLUMN $column TYPE text;"
        }

        try {
          statements.foreach { statement =>
            sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => statement)).append("\n")
          }
          isSuccessful = true
        } catch {
          case e: Exception =>
            isSuccessful = false
            sqlLog.append(s"\nException: ${e.getMessage}\n")
            logger.error(s"Migration.database.$name failed: ${e.getMessage}", e)
        }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$sqlLog
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${DynamicResourceDoc._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
