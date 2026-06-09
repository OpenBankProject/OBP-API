package code.scheduler

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}
import code.actorsystem.ObpActorSystem
import code.api.Constant
import code.api.util.APIUtil.generateUUID
import code.api.util.APIUtil
import code.metrics.{APIMetric, APIMetrics, MappedMetric, MetricArchive, MetricsArchiveRun}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.{Ascending, By, By_<=, By_>=, MaxRows, NotBy, OrderBy}

import scala.concurrent.duration._


/**
 * Result of moving outdated rows from `Metric` to `MetricArchive`.
 * @param moved  rows copied to the archive and deleted from `Metric`
 * @param failed rows whose archive copy genuinely failed (a real problem)
 *
 * Rows that have no correlation id are not counted here at all — they are
 * excluded by the candidate query (see `conditionalDeleteMetricsRow`) so the job
 * never repeatedly scans them. Their current count is reported by the
 * `/diagnostics/metrics` endpoint instead.
 */
case class ArchiveMoveResult(moved: Int, failed: Int)

/** Outcome of a single [[MetricsArchiveScheduler.runOnce]] invocation. */
sealed trait RunOutcome
/** A run executed and was recorded (inspect `run.Success` for whether it errored). */
case class RunCompleted(run: MetricsArchiveRun) extends RunOutcome
/**
 * No run started because one was already in progress (a `JobScheduler` lock is
 * present). Carries the held lock's details so callers can tell a genuinely
 * running job from a stale lock left by a dead JVM: a `startedAt` seconds ago is
 * a real run; one minutes/hours/days old is almost certainly abandoned.
 */
case class RunSkippedAlreadyInProgress(jobId: String, apiInstanceId: String, startedAt: Date) extends RunOutcome


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
    // On boot this instance cannot have a genuinely-running job, so clear any of its
    // own leftover lock rows (e.g. orphaned by a kill -9 / OOM / container eviction
    // that bypassed the finally in runOnce). Match on ApiInstanceId, NOT Name — lock
    // rows store Name=jobName and ApiInstanceId=apiInstanceId, so the old
    // `By(Name, apiInstanceId)` never matched and a redeploy could not self-heal
    // (only the 5-day sweep below would, leaving archiving stalled up to 5 days).
    // Keyed on this instance's own id, so another node's running job is untouched.
    JobScheduler.findAll(By(JobScheduler.ApiInstanceId, apiInstanceId)).map { i =>
      logger.info(s"Deleting leftover Job name: ${i.name}, Date: ${i.createdAt}, api_instance_id: $apiInstanceId")
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
        def run(): Unit = { runOnce(); () }
      }
    )
    logger.info("Bye from MetricsArchiveScheduler.start")
  }

  /**
   * Perform a single metrics-archive run: move outdated `Metric` rows to
   * `MetricArchive`, delete outdated `MetricArchive` rows, and record the outcome
   * in the `metricsarchiverun` log. Honours the same concurrency guard as the
   * scheduler — if a `JobScheduler` lock for this job is already present (a run is
   * in progress on this or another node) it returns [[RunSkippedAlreadyInProgress]]
   * without doing anything.
   *
   * This is the single source of truth for "do an archive run", called both by
   * the timer (every tick) and by the manual trigger endpoint, so a manual run
   * respects exactly the same checks and retention rules as a scheduled one.
   */
  def runOnce(): RunOutcome = {
    JobScheduler.find(By(JobScheduler.Name, jobName)) match {
      case Full(job) => // There is an ongoing/hanging job
        logger.info(s"MetricsArchiveScheduler.runOnce skipped due to ongoing job. Job ID: ${job.JobId.get}, started at: ${job.createdAt.get}, api_instance_id: ${job.ApiInstanceId.get}")
        RunSkippedAlreadyInProgress(job.JobId.get, job.ApiInstanceId.get, job.createdAt.get)
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
          val moveResult = conditionalDeleteMetricsRow()
          rowsMoved = moveResult.moved
          rowsDeleted = deleteOutdatedRowsFromMetricsArchive()
          // A genuine copy failure marks the run NOT successful, with a remark, so the
          // run log and the diagnostics endpoint surface it instead of silently
          // leaving rows behind.
          val runSucceeded = moveResult.failed == 0
          val remark =
            if (moveResult.failed > 0) Some(s"${moveResult.failed} metric row(s) failed to copy to the archive and were left in place.")
            else None
          val run = MetricsArchiveRun.recordRun(uniqueId, apiInstanceId, startedAt, new Date(),
            rowsMoved, rowsDeleted, success = runSucceeded, remark)
          RunCompleted(run)
        } catch {
          case e: Exception =>
            logger.error(s"MetricsArchiveScheduler Job ID: $uniqueId failed", e)
            val run = MetricsArchiveRun.recordRun(uniqueId, apiInstanceId, startedAt, new Date(),
              rowsMoved, rowsDeleted, success = false, Some(Option(e.getMessage).getOrElse(e.toString)))
            RunCompleted(run)
        } finally {
          JobScheduler.delete_!(job) // Allow future jobs
          logger.info(s"End of Job ID: $uniqueId (rows moved to archive: $rowsMoved, outdated archive rows deleted: $rowsDeleted)")
        }
    }
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

  // Move "Metric" rows older than retain_metrics_days into "MetricArchive", oldest
  // first, deleting each source row only after its archive copy is verified.
  def conditionalDeleteMetricsRow(): ArchiveMoveResult = {
    logger.info("Hello from MetricsArchiveScheduler.conditionalDeleteMetricsRow")
    val currentTime = new Date()
    val days = APIUtil.getPropsAsLongValue("retain_metrics_days", 367) match {
      case days if days > 59 => days
      case _ => 60
    }
    val someDaysAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Default tuned for smaller, more frequent runs: 10k rows every ~10 min
    // (see retain_metrics_scheduler_interval_in_seconds, default 599s) = ~60k rows/hr
    // ≈ 16.7 rows/sec max drain rate, i.e. it keeps up with a sustained ~16
    // metric-generating calls/sec. Much shorter per-run passes than a single 50k
    // batch — fewer mid-run failures (stale locks) and less memory/lock contention.
    val limit = APIUtil.getPropsAsIntValue("retain_metrics_move_limit", 10000)
    // Query the live Metric table directly — oldest first, up to `limit` rows.
    // We deliberately bypass APIMetrics.getAllMetrics here: that path is wrapped in
    // a Redis cache (stable TTL for past-dated queries), and a "which rows should I
    // move and delete" query must never be served from a stale snapshot.
    //
    // We also exclude rows with an empty correlation id: the archive's correlationId
    // column requires a UUID, so those rows can't be archived. Filtering them in the
    // query (rather than skipping them in the loop) means the job never repeatedly
    // re-scans the same un-archivable rows — which, being the oldest, would otherwise
    // permanently occupy the oldest-first candidate window. Their count is surfaced
    // by the /diagnostics/metrics endpoint instead.
    val candidateMetricRowsToMove: List[MappedMetric] = MappedMetric.findAll(
      By_<=(MappedMetric.date, someDaysAgo),
      NotBy(MappedMetric.correlationId, ""),
      OrderBy(MappedMetric.date, Ascending),
      MaxRows(limit)
    )
    logger.info(s"MetricsArchiveScheduler.conditionalDeleteMetricsRow: ${candidateMetricRowsToMove.length} candidate rows to move")

    var moved = 0
    var failed = 0
    candidateMetricRowsToMove.foreach { i =>
      // Copy first, then delete the source row only if the archive copy both saved
      // and is verifiably readable back by metricId.
      val copied = copyRowToMetricsArchive(i)
      if (copied && MetricArchive.find(By(MetricArchive.metricId, i.getMetricId())).isDefined) {
        MappedMetric.bulkDelete_!!(By(MappedMetric.id, i.getMetricId()))
        moved += 1
      } else {
        failed += 1
        logger.warn(s"Row with primary key ${i.getMetricId()} of the table Metric was not successfully copied to the archive; leaving it in place.")
      }
    }
    logger.info(s"Bye from MetricsArchiveScheduler.conditionalDeleteMetricsRow (moved $moved, failed $failed)")
    ArchiveMoveResult(moved, failed)
  }

  // Returns true when the archive copy was persisted, false otherwise.
  private def copyRowToMetricsArchive(i: APIMetric): Boolean = {
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
