/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.metrics

import java.util.Date

import code.util.MappedUUID
import net.liftweb.mapper._

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
 *
 * Naming: this is a DB entity, so the class must not start with `Mapped` and the
 * column objects must not start with `m` + uppercase (see `MappedClassNameTest`).
 */
class MetricsArchiveRun extends LongKeyedMapper[MetricsArchiveRun] with IdPK {

  def getSingleton = MetricsArchiveRun

  object RunId extends MappedUUID(this)
  object ApiInstanceId extends MappedString(this, 100)
  object StartedAt extends MappedDateTime(this)
  object EndedAt extends MappedDateTime(this)
  object DurationMs extends MappedLong(this)
  object RowsMovedToArchive extends MappedInt(this)
  object RowsDeletedFromArchive extends MappedInt(this)
  object Success extends MappedBoolean(this)
  object Remark extends MappedText(this)
}

object MetricsArchiveRun extends MetricsArchiveRun with LongKeyedMetaMapper[MetricsArchiveRun] {

  override def dbIndexes: List[BaseIndex[MetricsArchiveRun]] =
    UniqueIndex(RunId) :: Index(StartedAt) :: super.dbIndexes

  /** Keep only the most recent N runs; older rows are pruned on every write. */
  val maxRowsToKeep: Int = 1000

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
                remark: Option[String]): MetricsArchiveRun = {
    val saved = MetricsArchiveRun.create
      .RunId(runId)
      .ApiInstanceId(apiInstanceId)
      .StartedAt(startedAt)
      .EndedAt(endedAt)
      .DurationMs(endedAt.getTime - startedAt.getTime)
      .RowsMovedToArchive(rowsMovedToArchive)
      .RowsDeletedFromArchive(rowsDeletedFromArchive)
      .Success(success)
      .Remark(remark.getOrElse(""))
      .saveMe()
    pruneToMostRecent(maxRowsToKeep)
    saved
  }

  /**
   * Delete all but the most recent `keep` rows (by primary key, which is
   * monotonic). No-op when the table holds `keep` or fewer rows.
   */
  def pruneToMostRecent(keep: Int): Unit =
    MetricsArchiveRun
      .findAll(OrderBy(id, Descending), MaxRows(keep))
      .lastOption
      .foreach(oldestToKeep => MetricsArchiveRun.bulkDelete_!!(By_<(id, oldestToKeep.id.get)))

  /** Most recent run by start time, if any. */
  def lastRun: Option[MetricsArchiveRun] =
    MetricsArchiveRun.findAll(OrderBy(StartedAt, Descending), MaxRows(1)).headOption

  /** Most recent successful run by start time, if any. */
  def lastSuccessfulRun: Option[MetricsArchiveRun] =
    MetricsArchiveRun.findAll(By(Success, true), OrderBy(StartedAt, Descending), MaxRows(1)).headOption
}
