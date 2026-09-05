package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.{AppType, Consumer}
import net.liftweb.common.Full
import net.liftweb.util.{DefaultConnectionIdentifier, Helpers}

object MigrationOfCustomerAttributes {

  private val customerAttributeTableName = "mappedcustomerattribute"

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterColumnValue(name: String): Boolean = {
    DbFunction.tableExistsByName(customerAttributeTableName) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () => "ALTER TABLE mappedcustomerattribute ALTER COLUMN mvalue varchar(2000);"
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") => // MySQL
                () => "ALTER TABLE mappedcustomerattribute MODIFY COLUMN mvalue varchar(2000);"
              case _ =>
                () => "ALTER TABLE mappedcustomerattribute ALTER COLUMN mvalue type varchar(2000);"
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL: 
             |$executedSql
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""$customerAttributeTableName table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  def populateAzpAndSub(name: String): Boolean = {
    DbFunction.tableExistsByName("consumer") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Mapper compared the MappedString field object - not the value it holds - against null,
        // so neither filter ever matched and this migration has always been a no-op. It is kept
        // that way deliberately: comparing values instead would make a migration that databases
        // recorded as run years ago start rewriting azp and sub on them.
        val comparesTheFieldObject = (_: Consumer) => false

        val emptyNameConsumers =
          for {
            consumer <- Consumer.findAll() if comparesTheFieldObject(consumer)
          } yield {
            Consumer.update(consumer.copy(azp = APIUtil.generateUUID()))
          }

        val emptyAppTypeConsumers =
          for {
            consumer <- Consumer.findAll() if comparesTheFieldObject(consumer)
          } yield {
            Consumer.update(consumer.copy(sub = APIUtil.generateUUID()))
          }
        
        val consumersAll = (emptyNameConsumers++emptyAppTypeConsumers).distinct
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows: 
             |${consumersAll.size}
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""consumer table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
