package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.Consumer
import code.ratelimiting.RateLimiting
import net.liftweb.common.Full
import net.liftweb.mapper.{By, DB}
import net.liftweb.util.DefaultConnectionIdentifier

object TableRateLmiting {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populate(name: String): Boolean = {
    DbFunction.tableExists(RateLimiting, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val consumers = Consumer.findAll()

        // Make back up
        DbFunction.makeBackUpOfTable(RateLimiting)
    
        // Insert rows into table "ratelimiting" based on data in the table consumer
        val insertedRows: List[Boolean] =
          for {
            consumer <- consumers
          } yield {
            RateLimiting.find(By(RateLimiting.ConsumerId, consumer.consumerId.get)) match {
              case Full(_) => // Already exist
                true
              case _ =>
                RateLimiting.create
                  .ConsumerId(consumer.consumerId.get)
                  .PerSecondCallLimit(consumer.perSecondCallLimit.get)
                  .PerMinuteCallLimit(consumer.perMinuteCallLimit.get)
                  .PerHourCallLimit(consumer.perHourCallLimit.get)
                  .PerDayCallLimit(consumer.perDayCallLimit.get)
                  .PerWeekCallLimit(consumer.perWeekCallLimit.get)
                  .PerMonthCallLimit(consumer.perMonthCallLimit.get)
                  .FromDate(Date.from(oneDayAgo.toInstant()))
                  .ToDate(Date.from(oneYearInFuture.toInstant()))
                .save()
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
