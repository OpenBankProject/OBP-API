package code.scheduler

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}
import code.actorsystem.ObpActorSystem
import code.api.Constant
import code.api.util.APIUtil.generateUUID
import code.metrics.{APIMetric, APIMetrics, MappedMetric, MetricArchive, MetricsArchiveRun, MetricsArchiveRunTrait, MetricsProps}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

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
case class RunCompleted(run: MetricsArchiveRunTrait) extends RunOutcome
/**
 * No run started because one was already in progress (a `JobScheduler` lock is
 * present). Carries the held lock's details so callers can tell a genuinely
 * running job from a stale lock left by a dead JVM: a `startedAt` seconds ago is
 * a real run; one minutes/hours/days old is almost certainly abandoned.
 */
case class RunSkippedAlreadyInProgress(jobId: String, apiInstanceId: String, startedAt: Date) extends RunOutcome


object MetricsArchiveScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
  implicit lazy val executor: scala.concurrent.ExecutionContextExecutor = actorSystem.dispatcher
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
    JobScheduler.findAllByApiInstanceId(apiInstanceId).map { i =>
      logger.info(s"Deleting leftover Job name: ${i.name}, Date: ${i.createdAt}, api_instance_id: $apiInstanceId")
      i
    }.map(JobScheduler.delete)
    logger.info(s"Delete all Jobs older than 5 days")
    val fiveDaysAgo: Date = new Date(new Date().getTime - (oneDayInMillis * 5))
    JobScheduler.findAllCreatedOnOrBefore(fiveDaysAgo).map { i =>
      println(s"Job name: ${i.name}, Date: ${i.createdAt}, api_instance_id: ${apiInstanceId}")
      i
    }.map(JobScheduler.delete)
    
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
    JobScheduler.findByName(jobName) match {
      case Full(job) => // There is an ongoing/hanging job
        logger.info(s"MetricsArchiveScheduler.runOnce skipped due to ongoing job. Job ID: ${job.jobId}, started at: ${job.createdAt}, api_instance_id: ${job.apiInstanceId}")
        RunSkippedAlreadyInProgress(job.jobId, job.apiInstanceId, job.createdAt)
      case _ => // Start a new job
        val uniqueId = generateUUID()
        val job = JobScheduler.createJob(uniqueId, jobName, apiInstanceId)
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
          JobScheduler.delete(job) // Allow future jobs
          logger.info(s"End of Job ID: $uniqueId (rows moved to archive: $rowsMoved, outdated archive rows deleted: $rowsDeleted)")
        }
    }
  }

  // Returns the number of outdated rows deleted from the "MetricArchive" table.
  def deleteOutdatedRowsFromMetricsArchive(): Int = {
    logger.info("Hello from MetricsArchiveScheduler.deleteOutdatedRowsFromMetricsArchive")
    val currentTime = new Date()
    val days = MetricsProps.retainArchiveMetricsDays
    val someYearsAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Count before deleting so the run log records how many rows were removed.
    val outdatedCount = MetricArchive.countOnOrBefore(someYearsAgo).toInt
    // Delete the outdated rows from the table "MetricArchive"
    MetricArchive.deleteOnOrBefore(someYearsAgo)
    logger.info(s"Bye from MetricsArchiveScheduler.deleteOutdatedRowsFromMetricsArchive (deleted $outdatedCount rows)")
    outdatedCount
  }

  // Move "Metric" rows older than retain_metrics_days into "MetricArchive", oldest
  // first, deleting each source row only after its archive copy is verified.
  def conditionalDeleteMetricsRow(): ArchiveMoveResult = {
    logger.info("Hello from MetricsArchiveScheduler.conditionalDeleteMetricsRow")
    val currentTime = new Date()
    val days = MetricsProps.retainMetricsDays
    val someDaysAgo: Date = new Date(currentTime.getTime - (oneDayInMillis * days))
    // Batch-size rationale lives at MetricsProps.RetainMetricsMoveLimitDefault.
    val limit = MetricsProps.retainMetricsMoveLimit
    // Query the live Metric table directly — oldest first, up to `limit` rows.
    // We deliberately bypass APIMetrics.getAllMetrics here: that path is wrapped in
    // a Redis cache (stable TTL for past-dated queries), and a "which rows should I
    // move and delete" query must never be served from a stale snapshot.
    //
    // Note: rows with an empty/null correlation id are NOT filtered out. They used to
    // be (the archive's correlationId is non-null), which left them — being the oldest —
    // permanently occupying the candidate window and stalling the job. copyRowToMetricsArchive
    // now assigns those rows a synthetic "ORIGINALLY_NOT_SET-<uuid>" correlation id so
    // they archive normally instead of accumulating forever in the live table.
    val candidateMetricRowsToMove: List[MappedMetric] =
      MappedMetric.findOldestOnOrBefore(someDaysAgo, limit)
    logger.info(s"MetricsArchiveScheduler.conditionalDeleteMetricsRow: ${candidateMetricRowsToMove.length} candidate rows to move")

    var moved = 0
    var failed = 0
    candidateMetricRowsToMove.foreach { i =>
      // Copy first, then delete the source row only if the archive copy both saved
      // and is verifiably readable back by metricId.
      val copied = copyRowToMetricsArchive(i)
      if (copied && MetricArchive.findByMetricId(i.getMetricId()).isDefined) {
        MappedMetric.deleteByPrimaryKey(i.getMetricId())
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
    // Legacy rows can have an empty/null correlation id (they predate correlation ids,
    // or were never authenticated via a consent / X-Request-ID). The archive's
    // correlationId is non-null, so assign a traceable synthetic id rather than skipping
    // — and thereby permanently accumulating — these rows. The "ORIGINALLY_NOT_SET-"
    // prefix makes it obvious in the archive that the original value was absent.
    val rawCorrelationId = i.getCorrelationId()
    val correlationId =
      if (rawCorrelationId == null || rawCorrelationId.isEmpty)
        s"ORIGINALLY_NOT_SET-${generateUUID()}"
      else
        rawCorrelationId
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
      correlationId,
      i.getResponseBody(),
      i.getSourceIp(),
      i.getTargetIp(),
      i.getApiInstanceId(),
      i.getConsentReferenceId(),
      i.getCertificateTrust(),
      i.getCertificateTrustDetail(),
      i.getAuthType()
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
