package code.scheduler

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}
import code.actorsystem.ObpActorSystem
import code.api.Constant
import code.api.util.APIUtil.generateUUID
import code.api.util.{APIUtil, OBPLimit, OBPToDate}
import code.metrics.{APIMetric, APIMetrics, MappedMetric, MetricArchive, MetricsArchiveRun}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.{By, By_<=, By_>=}

import scala.concurrent.duration._


object MetricsArchiveScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val oneDayInMillis: Long = 86400000
  private val jobName = "MetricsArchiveScheduler"
  private val apiInstanceId = Constant.ApiInstanceId

  def start(intervalInSeconds: Long): Unit = {
    logger.info("Hello from MetricsArchiveScheduler.start")

    logger.info(s"--------- Clean up Jobs ---------")
    logger.info(s"Delete all Jobs created by api_instance_id=$apiInstanceId")
    JobScheduler.findAll(By(JobScheduler.Name, apiInstanceId)).map { i => 
      println(s"Job name: ${i.name}, Date: ${i.createdAt}")
      i
    }.map(_.delete_!)
    logger.info(s"Delete all Jobs older than 5 days")
    val fiveDaysAgo: Date = new Date(new Date().getTime - (oneDayInMillis * 5))
    JobScheduler.findAll(By_<=(JobScheduler.createdAt, fiveDaysAgo)).map { i =>
      println(s"Job name: ${i.name}, Date: ${i.createdAt}, api_instance_id: ${apiInstanceId}")
      i
    }.map(_.delete_!)
    
    scheduler.schedule(
      initialDelay = Duration(intervalInSeconds, TimeUnit.SECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          JobScheduler.find(By(JobScheduler.Name, jobName)) match {
            case Full(job) => // There is an ongoing/hanging job
              logger.info(s"Cannot start MetricsArchiveScheduler.start.run due to ongoing job. Job ID: ${job.JobId}")
            case _ => // Start a new job
              val uniqueId = generateUUID()
              val job = JobScheduler.create
                .JobId(uniqueId)
                .Name(jobName)
                .ApiInstanceId(apiInstanceId)
                .saveMe()
              logger.info(s"Starting Job ID: $uniqueId")
              val startedAt = new Date()
              var rowsMoved = 0
              var rowsDeleted = 0
              try {
                rowsMoved = conditionalDeleteMetricsRow()
                rowsDeleted = deleteOutdatedRowsFromMetricsArchive()
                MetricsArchiveRun.recordRun(uniqueId, apiInstanceId, startedAt, new Date(),
                  rowsMoved, rowsDeleted, success = true, None)
              } catch {
                case e: Exception =>
                  logger.error(s"MetricsArchiveScheduler Job ID: $uniqueId failed", e)
                  MetricsArchiveRun.recordRun(uniqueId, apiInstanceId, startedAt, new Date(),
                    rowsMoved, rowsDeleted, success = false, Some(Option(e.getMessage).getOrElse(e.toString)))
              } finally {
                JobScheduler.delete_!(job) // Allow future jobs
                logger.info(s"End of Job ID: $uniqueId (rows moved to archive: $rowsMoved, outdated archive rows deleted: $rowsDeleted)")
              }
          }
        } 
      }
    )
    logger.info("Bye from MetricsArchiveScheduler.start")
  }

  // Returns the number of outdated rows deleted from the "MetricArchive" table.
  def deleteOutdatedRowsFromMetricsArchive(): Int = {
    logger.info("Hello from MetricsArchiveScheduler.deleteOutdatedRowsFromMetricsArchive")
    val currentTime = new Date()
    val defaultValue : Int = 365 * 3
    val days = APIUtil.getPropsAsLongValue("retain_archive_metrics_days", defaultValue) match {
      case days if days > 364 => days
      case _ => 365
    }
    val someYearsAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Count before deleting so the run log records how many rows were removed.
    val outdatedCount = MetricArchive.count(By_<=(MetricArchive.date, someYearsAgo)).toInt
    // Delete the outdated rows from the table "MetricArchive"
    MetricArchive.bulkDelete_!!(By_<=(MetricArchive.date, someYearsAgo))
    logger.info(s"Bye from MetricsArchiveScheduler.deleteOutdatedRowsFromMetricsArchive (deleted $outdatedCount rows)")
    outdatedCount
  }

  // Returns the number of rows successfully moved from "Metric" to "MetricArchive".
  def conditionalDeleteMetricsRow(): Int = {
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
    val movedCount = maybeDeletedRows.count(_._1 == true)
    logger.info(s"Bye from MetricsArchiveScheduler.conditionalDeleteMetricsRow (moved $movedCount rows)")
    movedCount
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
      i.getCorrelationId(),
      i.getResponseBody(),
      i.getSourceIp(),
      i.getTargetIp(),
      i.getApiInstanceId(),
      i.getConsentReferenceId()
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
