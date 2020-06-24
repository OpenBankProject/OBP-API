package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.{AppType, Consumer}
import net.liftweb.mapper.DB
import net.liftweb.util.{DefaultConnectionIdentifier, Helpers}

object MigrationOfConsumer {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populateNamAndAppType(name: String): Boolean = {
    DbFunction.tableExists(Consumer, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val emptyNameConsumers = 
          for {
            consumer <- Consumer.findAll() if consumer.name.get.isEmpty()
          } yield {
            consumer
              .name(Helpers.randomString(10).toLowerCase())
              .saveMe()
          }

        val emptyAppTypeConsumers =
          for {
            consumer <- Consumer.findAll() if consumer.appType.get.isEmpty()
          } yield {
            consumer
              .appType(AppType.Web.toString())
              .saveMe()
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
          s"""${Consumer._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }  
  def populateAzpAndSub(name: String): Boolean = {
    DbFunction.tableExists(Consumer, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val emptyNameConsumers = 
          for {
            consumer <- Consumer.findAll() if consumer.azp.equals(null)
          } yield {
            consumer
              .azp(APIUtil.generateUUID())
              .saveMe()
          }

        val emptyAppTypeConsumers =
          for {
            consumer <- Consumer.findAll() if consumer.sub.equals(null)
          } yield {
            consumer
              .sub(APIUtil.generateUUID())
              .saveMe()
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
          s"""${Consumer._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
