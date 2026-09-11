package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.Consumer
import code.ratelimiting.RateLimiting
import net.liftweb.common.Full
import net.liftweb.util.DefaultConnectionIdentifier

object TableRateLmiting {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populate(name: String): Boolean = {
    DbFunction.tableExistsByName("ratelimiting") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val consumers = Consumer.findAll()

        // Make back up
        DbFunction.makeBackUpOfTableByName("ratelimiting")
    
        // Insert rows into table "ratelimiting" based on data in the table consumer
        val insertedRows: List[Boolean] =
          for {
            consumer <- consumers
          } yield {
            RateLimiting.findAllByConsumerId(consumer.consumerId).headOption match {
              case Some(_) => // Already exist
                true
              case _ =>
                RateLimiting.insertWithLimits(
                  consumerId = consumer.consumerId,
                  fromDate = Date.from(oneDayAgo.toInstant()),
                  toDate = Date.from(oneYearInFuture.toInstant()),
                  apiVersion = None,
                  apiName = None,
                  bankId = None,
                  perSecond = consumer.perSecondCallLimit,
                  perMinute = consumer.perMinuteCallLimit,
                  perHour = consumer.perHourCallLimit,
                  perDay = consumer.perDayCallLimit,
                  perWeek = consumer.perWeekCallLimit,
                  perMonth = consumer.perMonthCallLimit)
                true
            }
          }
        val isSuccessful = insertedRows.forall(_ == true)
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Number of inserted rows: ${insertedRows.size}""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
        
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Rate limiting table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
