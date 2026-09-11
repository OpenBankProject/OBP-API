package code.scheduler

import java.util.Date

import code.metrics.{MappedMetric, MetricArchive, MetricBatchWriter, MetricsArchiveRun}
import code.setup.ServerSetup

/**
 * Exercises the actual metrics-archiving logic (not just the HTTP wiring) by seeding
 * real `Metric` / `MetricArchive` rows and calling `MetricsArchiveScheduler.runOnce()`.
 *
 * Default retention props apply (none set in test props):
 *   retain_metrics_days        = 367 (effective 367)
 *   retain_archive_metrics_days = 1095 (effective 1095)
 * so a row 800 days old is past the metric window, and an archive row 2000 days old
 * is past the archive window — both unambiguous against the defaults.
 *
 * Package code.scheduler is not in an explicit shard allowlist, so it runs in the
 * shard-4 catch-all.
 */
class MetricsArchiveSchedulerTest extends ServerSetup {

  private val oneDayMs = 86400000L
  private def daysAgo(numberOfDaysAgo: Long): Date = new Date(new Date().getTime - numberOfDaysAgo * oneDayMs)
  private def validUuid(): String = java.util.UUID.randomUUID().toString
  private val jobName = "MetricsArchiveScheduler"

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Clean slate: flush any async metric writes, then wipe the three tables and any
    // leftover scheduler lock so runOnce isn't skipped.
    MetricBatchWriter.flush()
    MappedMetric.deleteAll()
    MetricArchive.deleteAll()
    MetricsArchiveRun.bulkDelete_!!()
    JobScheduler.findAllByName(jobName).foreach(JobScheduler.delete)
  }

  private def seedMetric(date: Date, correlationId: String): MappedMetric = {
    val id = MappedMetric.insert(
      userId = "user-1",
      url = "http://example.com/foo",
      date = date,
      duration = 1L,
      userName = "uname",
      appName = "app",
      developerEmail = "dev@example.com",
      consumerId = "consumer-1",
      implementedByPartialFunction = "fn",
      implementedInVersion = "v7.0.0",
      verb = "GET",
      httpCode = 200,
      correlationId = correlationId,
      responseBody = "body",
      sourceIp = "127.0.0.1",
      targetIp = "127.0.0.1",
      apiInstanceId = "test",
      consentReferenceId = "",
      certificateTrust = null,
      certificateTrustDetail = null)
    MappedMetric.findByPrimaryKey(id).openOrThrowException("the metric just seeded must be readable")
  }

  private def seedArchive(metricId: Long, date: Date): MetricArchive = {
    MetricArchive.upsertByMetricId(
      metricId = metricId,
      userId = "user-1",
      url = "http://example.com/foo",
      date = date,
      duration = 1L,
      userName = "uname",
      appName = "app",
      developerEmail = "dev@example.com",
      consumerId = "consumer-1",
      implementedByPartialFunction = "fn",
      implementedInVersion = "v7.0.0",
      verb = "GET",
      httpCode = Some(200),
      correlationId = validUuid(),
      responseBody = "body",
      sourceIp = "127.0.0.1",
      targetIp = "127.0.0.1",
      apiInstanceId = "test",
      consentReferenceId = "",
      certificateTrust = null,
      certificateTrustDetail = null,
      authType = null)
    MetricArchive.findByMetricId(metricId)
      .openOrThrowException("the archive row just seeded must be readable")
  }

  Feature("MetricsArchiveScheduler.runOnce") {

    Scenario("Old rows with a valid correlation id are copied to the archive and deleted from metric") {
      val oldRow = seedMetric(daysAgo(800), validUuid())
      val recentRow = seedMetric(daysAgo(10), validUuid())

      val outcome = MetricsArchiveScheduler.runOnce()
      outcome shouldBe a[RunCompleted]

      Then("the old row is gone from metric and present in the archive")
      MappedMetric.findByPrimaryKey(oldRow.metricPrimaryKey).isDefined should equal(false)
      MetricArchive.findByMetricId(oldRow.metricPrimaryKey).isDefined should equal(true)

      And("the recent row is untouched")
      MappedMetric.findByPrimaryKey(recentRow.metricPrimaryKey).isDefined should equal(true)
      MetricArchive.findByMetricId(recentRow.metricPrimaryKey).isDefined should equal(false)

      And("the run records exactly one moved row and is successful")
      val run = outcome.asInstanceOf[RunCompleted].run
      run.success should equal(true)
      run.rowsMovedToArchive should equal(1)
    }

    Scenario("Old rows with an empty correlation id are archived with a synthetic ORIGINALLY_NOT_SET correlation id") {
      val noCorr = seedMetric(daysAgo(800), "")

      val outcome = MetricsArchiveScheduler.runOnce()
      outcome shouldBe a[RunCompleted]

      Then("the row is moved out of metric and into the archive")
      MappedMetric.findByPrimaryKey(noCorr.metricPrimaryKey).isDefined should equal(false)
      val archived = MetricArchive.findByMetricId(noCorr.metricPrimaryKey)
      archived.isDefined should equal(true)

      And("the archived copy was given a generated ORIGINALLY_NOT_SET correlation id")
      archived.openOrThrowException("expected archived row").correlationId should startWith("ORIGINALLY_NOT_SET-")

      And("exactly one row was moved")
      outcome.asInstanceOf[RunCompleted].run.rowsMovedToArchive should equal(1)
    }

    Scenario("Outdated archive rows are deleted; recent archive rows are kept") {
      val oldArchive = seedArchive(999001L, daysAgo(2000))
      val recentArchive = seedArchive(999002L, daysAgo(100))

      val outcome = MetricsArchiveScheduler.runOnce()
      outcome shouldBe a[RunCompleted]

      Then("the outdated archive row is deleted and the recent one is kept")
      MetricArchive.findByPrimaryKey(oldArchive.archivePrimaryKey).isDefined should equal(false)
      MetricArchive.findByPrimaryKey(recentArchive.archivePrimaryKey).isDefined should equal(true)

      And("the run records exactly one deleted archive row")
      outcome.asInstanceOf[RunCompleted].run.rowsDeletedFromArchive should equal(1)
    }

    Scenario("Each run is recorded in the metricsarchiverun log") {
      seedMetric(daysAgo(800), validUuid())
      MetricsArchiveRun.count() should equal(0L)

      MetricsArchiveScheduler.runOnce()

      MetricsArchiveRun.count() should equal(1L)
      val last = MetricsArchiveRun.lastRun
      last.isDefined should equal(true)
      last.get.rowsMovedToArchive should equal(1)
    }

    Scenario("runOnce is skipped (no work, no log row) when a job lock is already present") {
      seedMetric(daysAgo(800), validUuid())
      // Simulate an in-progress run on this or another node.
      val lockJobId = validUuid()
      JobScheduler.createJob(lockJobId, jobName, "other-node")

      val outcome = MetricsArchiveScheduler.runOnce()

      Then("the run is skipped and nothing changed")
      outcome shouldBe a[RunSkippedAlreadyInProgress]
      val skipped = outcome.asInstanceOf[RunSkippedAlreadyInProgress]
      skipped.jobId should equal(lockJobId)
      skipped.apiInstanceId should equal("other-node")
      MetricsArchiveRun.count() should equal(0L)
      MappedMetric.count() should equal(1L)
    }

    Scenario("The run log is capped to the most recent rows (pruneToMostRecent)") {
      (1 to 10).foreach { i =>
        MetricsArchiveRun.recordRun(validUuid(), "test", daysAgo(10 - i), daysAgo(10 - i),
          rowsMovedToArchive = i, rowsDeletedFromArchive = 0, success = true, remark = None)
      }
      MetricsArchiveRun.count() should equal(10L)

      MetricsArchiveRun.pruneToMostRecent(5)
      MetricsArchiveRun.count() should equal(5L)

      And("the production cap is 1000")
      MetricsArchiveRun.maxRowsToKeep should equal(1000)
    }
  }
}
