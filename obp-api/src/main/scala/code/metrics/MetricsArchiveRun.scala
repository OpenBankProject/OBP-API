package code.metrics

import java.util.Date

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * Append-only audit log of `MetricsArchiveScheduler` runs.
 *
 * One row is written at the end of every scheduler tick that actually did work
 * (i.e. was not skipped because a previous run was still in progress). It records
 * how many rows were moved `metric` -> `metricarchive`, how many outdated archive
 * rows were deleted, the wall-clock duration, and whether the run succeeded.
 *
 * This is durable history. Contrast with the `jobscheduler` table, which holds
 * only a transient lock row that exists during a run and is deleted on completion.
 *
 * The log is self-capped: only the most recent [[MetricsArchiveRun.maxRowsToKeep]]
 * rows are retained. Each write prunes anything older, so the table stays small.
 */
trait MetricsArchiveRunTrait {
  def runId: String
  def apiInstanceId: String
  def startedAt: Date
  def endedAt: Date
  def durationMs: Long
  def rowsMovedToArchive: Int
  def rowsDeletedFromArchive: Int
  def success: Boolean
  def remark: String
}

case class MetricsArchiveRunRow(
  runId: String,
  apiInstanceId: String,
  startedAt: Date,
  endedAt: Date,
  durationMs: Long,
  rowsMovedToArchive: Int,
  rowsDeletedFromArchive: Int,
  success: Boolean,
  remark: String
) extends MetricsArchiveRunTrait

object MetricsArchiveRun {

  /** Keep only the most recent N runs; older rows are pruned on every write. */
  val maxRowsToKeep: Int = 1000

  private def fromRow(row: (String, String, java.sql.Timestamp, java.sql.Timestamp, Long, Int, Int, Boolean, String)): MetricsArchiveRunTrait =
    row match {
      case (runId, apiInstanceId, startedAt, endedAt, durationMs, rowsMovedToArchive, rowsDeletedFromArchive, success, remark) =>
        MetricsArchiveRunRow(runId, apiInstanceId, startedAt, endedAt, durationMs, rowsMovedToArchive, rowsDeletedFromArchive, success, remark)
    }

  /**
   * Persist one completed run, then prune the log back to the most recent
   * [[maxRowsToKeep]] rows. The scheduler's own retention applies to `metric` /
   * `metricarchive`; this cap keeps the run log itself bounded.
   */
  def recordRun(runId: String,
                apiInstanceId: String,
                startedAt: Date,
                endedAt: Date,
                rowsMovedToArchive: Int,
                rowsDeletedFromArchive: Int,
                success: Boolean,
                remark: Option[String]): MetricsArchiveRunTrait = {
    val startedAtTs = new java.sql.Timestamp(startedAt.getTime)
    val endedAtTs = new java.sql.Timestamp(endedAt.getTime)
    val durationMs = endedAt.getTime - startedAt.getTime
    val remarkValue = remark.getOrElse("")
    DoobieUtil.runUpdate(
      sql"""INSERT INTO metricsarchiverun
            (runid, apiinstanceid, startedat, endedat, durationms, rowsmovedtoarchive, rowsdeletedfromarchive, success, remark)
            VALUES
            ($runId, $apiInstanceId, $startedAtTs, $endedAtTs, $durationMs, $rowsMovedToArchive, $rowsDeletedFromArchive, $success, $remarkValue)"""
        .update.run)
    pruneToMostRecent(maxRowsToKeep)
    MetricsArchiveRunRow(runId, apiInstanceId, startedAt, endedAt, durationMs, rowsMovedToArchive, rowsDeletedFromArchive, success, remarkValue)
  }

  /**
   * Delete all but the most recent `keep` rows (by primary key, which is
   * monotonic). No-op when the table holds `keep` or fewer rows.
   */
  def pruneToMostRecent(keep: Int): Unit = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM metricsarchiverun WHERE id < (
              SELECT MIN(id) FROM (
                SELECT id FROM metricsarchiverun ORDER BY id DESC LIMIT $keep
              )
            )"""
        .update.run)
    ()
  }

  private val selectColumns =
    fr"SELECT runid, apiinstanceid, startedat, endedat, durationms, rowsmovedtoarchive, rowsdeletedfromarchive, success, remark FROM metricsarchiverun"

  /** Most recent run by start time, if any. */
  def lastRun: Option[MetricsArchiveRunTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"ORDER BY startedat DESC LIMIT 1")
        .query[(String, String, java.sql.Timestamp, java.sql.Timestamp, Long, Int, Int, Boolean, String)]
        .option
    ).map(fromRow)

  /** Most recent successful run by start time, if any. */
  def lastSuccessfulRun: Option[MetricsArchiveRunTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE success = true ORDER BY startedat DESC LIMIT 1")
        .query[(String, String, java.sql.Timestamp, java.sql.Timestamp, Long, Int, Int, Boolean, String)]
        .option
    ).map(fromRow)

  def count(): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM metricsarchiverun".query[Long].unique)

  def bulkDelete_!!(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM metricsarchiverun".update.run)
    ()
  }
}
