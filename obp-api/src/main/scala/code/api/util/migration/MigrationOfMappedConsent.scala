package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.consent.MappedConsent
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfMappedConsent {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def alterColumnJsonWebToken(name: String): Boolean = {
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql = 
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
              APIUtil.getPropsValue("db.driver") match    {
                case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                  () => "ALTER TABLE mappedconsent ALTER COLUMN mjsonwebtoken text;"
                case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") => // MySQL
                  () => "ALTER TABLE mappedconsent MODIFY COLUMN mjsonwebtoken TEXT;"
                case _ =>
                  () => "ALTER TABLE mappedconsent ALTER COLUMN mjsonwebtoken type text;"
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
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def alterColumnChallenge(name: String): Boolean = {
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mchallenge varchar(50);
                    |""".stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") => // MySQL
                () =>
                  """ALTER TABLE mappedconsent MODIFY COLUMN mchallenge varchar(50);
                    |""".stripMargin
              case _ =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mchallenge type varchar(50);
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
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  // The mConsumerId column was originally MappedUUID (varchar(36)), but Consumer.consumerId
  // is MappedString(250) and can hold composite IDs like "{azp_value}_UUID" generated
  // by OAuth2.getOrCreateConsumer when the azp claim is not a UUID.
  // This migration widens mConsumerId to match the Consumer model.
  def alterColumnConsumerIdLength(name: String): Boolean = {
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mconsumerid varchar(250);
                    |""".stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") => // MySQL
                () =>
                  """ALTER TABLE mappedconsent MODIFY COLUMN mconsumerid varchar(250);
                    |""".stripMargin
              case _ =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mconsumerid TYPE character varying(250);
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
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  def alterColumnStatus(name: String): Boolean = {
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mstatus varchar(40);
                    |""".stripMargin
              case _ =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mstatus type varchar(40);
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
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
