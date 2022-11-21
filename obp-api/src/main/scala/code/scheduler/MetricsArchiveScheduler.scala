package code.scheduler

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}

import code.actorsystem.ObpLookupSystem
import code.api.util.{APIUtil, OBPFromDate}
import code.metrics.{APIMetrics, MappedMetric, MetricsArchive}
import code.util.Helper.MdcLoggable
import net.liftweb.mapper.By_<=

import scala.concurrent.duration._


object MetricsArchiveScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpLookupSystem.obpLookupSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val oneDayInMillis: Long = 86400000

  def start(intervalInSeconds: Long): Unit = {
    scheduler.schedule(
      initialDelay = Duration(getMillisTillMidnight(), TimeUnit.MILLISECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          copyDataToMetricsArchive()
          deleteOutdatedRowsFromMetricsArchive()
          deleteOutdatedRowsFromMetric()
        } 
      }
    )
  }
  
  def copyDataToMetricsArchive() = {
    val currentTime = new Date()
    val twoDaysAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * 2))
    // Get the data from the table "Metric" (former "MappedMetric")
    val chunkOfData = APIMetrics.apiMetrics.vend.getAllMetrics(List(OBPFromDate(twoDaysAgo)))
    chunkOfData map { i =>
      // and copy it to the table "MetricsArchive"
      APIMetrics.apiMetrics.vend.saveMetricsArchive(
        i.getPrimaryKey(),
        i.getUserId(),
        i.getUrl(),
        i.getDate(),
        i.getDuration(),
        i.getUserName(),
        i.getAppName(),
        i.getDeveloperEmail(),
        i.getConsumerId(),
        i.getImplementedByPartialFunction(),
        i.getImplementedInVersion(),
        i.getVerb(),
        Some(i.getHttpCode()),
        i.getCorrelationId()
      )
    }
  }

  def deleteOutdatedRowsFromMetricsArchive() = {
    val currentTime = new Date()
    val defaultValue : Int = 365 * 3
    val days = APIUtil.getPropsAsLongValue("retain_archive_metrics_days", defaultValue) match {
      case days if days > 364 => days
      case _ => 365
    }
    val oneYearAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Delete the outdated rows from the table "MetricsArchive"
    MetricsArchive.bulkDelete_!!(By_<=(MetricsArchive.date, oneYearAgo))
  }
  
  def deleteOutdatedRowsFromMetric() = {
    val currentTime = new Date()
    val days = APIUtil.getPropsAsLongValue("retain_metrics_days=60", 60)
    val daysAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Delete the outdated rows from the table "Metric"
    MappedMetric.bulkDelete_!!(By_<=(MappedMetric.date, daysAgo))
  }
  
  private def getMillisTillMidnight(): Long = {
    val c = Calendar.getInstance
    c.add(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.getTimeInMillis - System.currentTimeMillis
  }
  
}
