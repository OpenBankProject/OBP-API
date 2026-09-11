package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.{AppType, Consumer}
import net.liftweb.common.Full
import net.liftweb.util.{DefaultConnectionIdentifier, Helpers}

object MigrationOfConsumer {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populateNamAndAppType(name: String): Boolean = {
    DbFunction.tableExistsByName("consumer") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val emptyNameConsumers = 
          for {
            consumer <- Consumer.findAll() if consumer.name.isEmpty()
          } yield {
            Consumer.update(consumer.copy(name = Helpers.randomString(10).toLowerCase()))
          }

        val emptyAppTypeConsumers =
          for {
            consumer <- Consumer.findAll() if consumer.appType.isEmpty()
          } yield {
            Consumer.update(consumer.copy(appType = AppType.Confidential.toString()))
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


  def alterTypeofAud(name: String): Boolean = {
    DbFunction.tableExistsByName("consumer") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |ALTER TABLE consumer ALTER COLUMN aud VARCHAR(MAX) NULL;
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |ALTER TABLE consumer ALTER COLUMN aud TYPE text;
                    |""".stripMargin
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
          s"""consumer table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }


}
