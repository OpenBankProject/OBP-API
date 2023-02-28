package code.scheduler

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}

import code.actorsystem.ObpLookupSystem
import code.api.util.{APIUtil, OBPLimit, OBPToDate}
import code.metrics.{APIMetric, APIMetrics, MappedMetric, MetricArchive}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.{By, By_<=}

import scala.concurrent.duration._


object MetricsArchiveScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpLookupSystem.obpLookupSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val oneDayInMillis: Long = 86400000

  def start(intervalInSeconds: Long): Unit = {
    logger.info("Hello from MetricsArchiveScheduler.start")
    scheduler.schedule(
      initialDelay = Duration(intervalInSeconds, TimeUnit.SECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          logger.info("Hello from MetricsArchiveScheduler.start.run")
          conditionalDeleteMetricsRow()
          deleteOutdatedRowsFromMetricsArchive()
        } 
      }
    )
  }

  def deleteOutdatedRowsFromMetricsArchive() = {
    logger.info("Hello from MetricsArchiveScheduler.deleteOutdatedRowsFromMetricsArchive")
    val currentTime = new Date()
    val defaultValue : Int = 365 * 3
    val days = APIUtil.getPropsAsLongValue("retain_archive_metrics_days", defaultValue) match {
      case days if days > 364 => days
      case _ => 365
    }
    val someYearsAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Delete the outdated rows from the table "MetricsArchive"
    MetricArchive.bulkDelete_!!(By_<=(MetricArchive.date, someYearsAgo))
    logger.info("Bye from MetricsArchiveScheduler.deleteOutdatedRowsFromMetricsArchive")
  }

  def conditionalDeleteMetricsRow() = {
    logger.info("Hello from MetricsArchiveScheduler.conditionalDeleteMetricsRow")
    val currentTime = new Date()
    val days = APIUtil.getPropsAsLongValue("retain_metrics_days", 367) match {
      case days if days > 59 => days
      case _ => 60
    }
    val someDaysAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    val limit = APIUtil.getPropsAsIntValue("retain_metrics_move_limit", 50000)
    // Get the data from the table "Metric" older than specified by retain_metrics_days
    logger.info("MetricsArchiveScheduler.conditionalDeleteMetricsRow says before candidateMetricRowsToMove val")
    val candidateMetricRowsToMove = APIMetrics.apiMetrics.vend.getAllMetrics(List(OBPToDate(someDaysAgo), OBPLimit(limit)))
    logger.info("MetricsArchiveScheduler.conditionalDeleteMetricsRow says after candidateMetricRowsToMove val")
    logger.info(s"Number of rows: ${candidateMetricRowsToMove.length}")
    candidateMetricRowsToMove map { i =>
      // and copy it to the table "MetricArchive"
      copyRowToMetricsArchive(i)
    }
    logger.info("MetricsArchiveScheduler.conditionalDeleteMetricsRow says after coping all rows")
    logger.info("MetricsArchiveScheduler.conditionalDeleteMetricsRow says before maybeDeletedRows val")
    val maybeDeletedRows: List[(Boolean, Long)] = candidateMetricRowsToMove map { i =>
      // and delete it after successful coping
      MetricArchive.find(By(MetricArchive.metricId, i.getMetricId())) match {
        case Full(_) => (MappedMetric.bulkDelete_!!(By(MappedMetric.id, i.getMetricId())), i.getMetricId())
        case _ => (false, i.getMetricId())
      }
    }
    logger.info("MetricsArchiveScheduler.conditionalDeleteMetricsRow says after maybeDeletedRows val")
    maybeDeletedRows.filter(_._1 == false).map { i => 
      logger.warn(s"Row with primary key ${i._2} of the table Metric is not successfully copied.")
    }
  }

  private def copyRowToMetricsArchive(i: APIMetric): Unit = {
    APIMetrics.apiMetrics.vend.saveMetricsArchive(
      i.getMetricId(),
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
