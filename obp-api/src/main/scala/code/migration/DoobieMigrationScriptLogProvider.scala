package code.migration

import java.sql.Timestamp

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/** One migration-script-log row, standing in for the Lift entity in return types. */
case class MigrationScriptLogRow(
  primaryKey: Long,
  migrationScriptLogId: String,
  name: String,
  commitId: String,
  isSuccessful: Boolean,
  startDate: Long,
  endDate: Long,
  remark: String
) extends MigrationScriptLogTrait

/**
 * Doobie implementation of the migration-script-log store, replacing the Lift MigrationScriptLog
 * entity. This is migration bookkeeping itself - the table every historical migration script
 * writes to via Migration.saveLog and checks via isExecuted - so it comes with the same caution
 * as the rest of that machinery: it is read at boot, before any request scope exists.
 *
 * saveLog is find-then-write on (name, isSuccessful), matching the Mapper version exactly; the
 * unique index on that pair is what makes "write" always mean "at most one row per
 * (name, isSuccessful)".
 *
 * Writes go through runUpdate: outside a request scope (which boot-time migrations always are)
 * runQuery's fallback transactor is Strategy.void on a pool with autoCommit off, so the write
 * would be rolled back on return.
 */
object DoobieMigrationScriptLogProvider extends MigrationScriptLogProvider with MdcLoggable {

  private def rowOf(r: (Long, Option[String], Option[String], Option[String], Option[Boolean], Option[Long], Option[Long], Option[String])): MigrationScriptLogRow =
    MigrationScriptLogRow(r._1, r._2.orNull, r._3.orNull, r._4.orNull, r._5.getOrElse(false), r._6.getOrElse(0L), r._7.getOrElse(0L), r._8.orNull)

  private val selectCols: Fragment =
    fr"""SELECT id, migrationscriptlogid, name, commitid, issuccessful, startdate, enddate, remark
         FROM migrationscriptlog"""

  private def findOne(name: String, isSuccessful: Boolean): Option[MigrationScriptLogRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE name = $name AND issuccessful = $isSuccessful LIMIT 1")
        .query[(Long, Option[String], Option[String], Option[String], Option[Boolean], Option[Long], Option[Long], Option[String])].option
    ).map(rowOf)

  override def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String): Boolean = {
    val now = new Timestamp(System.currentTimeMillis)
    findOne(name, isSuccessful) match {
      case Some(existing) =>
        DoobieUtil.runUpdate(
          sql"""UPDATE migrationscriptlog
                SET commitid = $commitId, startdate = $startDate, enddate = $endDate, remark = $comment, updatedat = $now
                WHERE id = ${existing.primaryKey}"""
            .update.run) > 0
      case None =>
        val id = APIUtil.generateUUID()
        DoobieUtil.runUpdate(
          sql"""INSERT INTO migrationscriptlog
                  (migrationscriptlogid, name, commitid, issuccessful, startdate, enddate, remark, createdat, updatedat)
                VALUES ($id, $name, $commitId, $isSuccessful, $startDate, $endDate, $comment, $now, $now)"""
            .update.run) > 0
    }
  }

  override def isExecuted(name: String): Boolean =
    findOne(name, isSuccessful = true).isDefined

  /**
   * Remove every log row for one migration name.
   *
   * Test support: the seeding test has to re-run a once-per-database migration, which is exactly
   * what isExecuted refuses. No production path deletes from this table - it is the record of
   * what has run.
   */
  def deleteByName(name: String): Int =
    DoobieUtil.runUpdate(sql"DELETE FROM migrationscriptlog WHERE name = $name".update.run)

  override def getMigrationScriptLogs(): List[MigrationScriptLogTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"ORDER BY createdat DESC")
        .query[(Long, Option[String], Option[String], Option[String], Option[Boolean], Option[Long], Option[Long], Option[String])].to[List]
    ).map(rowOf)
}
