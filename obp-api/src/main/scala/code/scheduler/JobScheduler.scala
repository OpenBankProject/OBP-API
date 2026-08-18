package code.scheduler

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import java.util.Date

/**
 * A held scheduler lock.
 *
 * The name is kept from the Lift entity rather than becoming DoobieJobScheduler: the concrete
 * type appears in JSONFactory7.0.0's createSchedulerJobsJsonV700 signature, so renaming it would
 * ripple into the v7 API layer for no gain.
 *
 * `createdAt` is carried on the row (the Mapper got it from the CreatedUpdated mixin) because
 * both schedulers and the v7 diagnostics endpoint use it to tell a genuinely-running job from a
 * stale lock: seconds old is a real run, hours old is almost certainly abandoned.
 */
case class JobScheduler(
  primaryKey: Long,
  jobId: String,
  name: String,
  apiInstanceId: String,
  createdAt: Date
) extends JobSchedulerTrait

object JobScheduler {

  private val selectColumns =
    fr"SELECT id, jobid, name, apiinstanceid, createdat FROM jobscheduler"

  private type Row = (Long, Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): JobScheduler = row match {
    case (id, jobId, name, apiInstanceId, createdAt) =>
      JobScheduler(id, jobId.orNull, name.orNull, apiInstanceId.orNull, createdAt.orNull)
  }

  private def query(condition: Fragment): List[JobScheduler] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /**
   * The most recent scheduler-lock rows, newest first, capped at `limit`.
   *
   * Note: `jobscheduler` is a lock table, not a job-history log — a row exists
   * only while a job holds the lock and is deleted when the job finishes. In
   * healthy operation this returns an empty list; any rows present are either
   * currently running or stale locks left by a dead JVM.
   */
  def mostRecent(limit: Int): List[JobScheduler] =
    query(fr"ORDER BY createdat DESC LIMIT $limit")

  def findAll(): List[JobScheduler] = query(Fragment.empty)

  def findAllByName(name: String): List[JobScheduler] =
    query(fr"WHERE name = $name")

  def findAllByApiInstanceId(apiInstanceId: String): List[JobScheduler] =
    query(fr"WHERE apiinstanceid = $apiInstanceId")

  def findAllCreatedOnOrBefore(cutoff: Date): List[JobScheduler] =
    query(fr"WHERE createdat <= ${new java.sql.Timestamp(cutoff.getTime)}")

  def findByName(name: String): Box[JobScheduler] =
    query(fr"WHERE name = $name LIMIT 1").headOption match {
      case Some(job) => Full(job)
      case None => Empty
    }

  def findByJobId(jobId: String): Box[JobScheduler] =
    query(fr"WHERE jobid = $jobId LIMIT 1").headOption match {
      case Some(job) => Full(job)
      case None => Empty
    }

  /** Take the lock: insert a row and return it. */
  def createJob(jobId: String, name: String, apiInstanceId: String): JobScheduler = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO jobscheduler (jobid, name, apiinstanceid, createdat, updatedat)
            VALUES ($jobId, $name, $apiInstanceId, $now, $now)"""
        .update.run)
    val id = DoobieUtil.runQuery(
      sql"SELECT id FROM jobscheduler WHERE jobid = $jobId".query[Long].unique)
    JobScheduler(id, jobId, name, apiInstanceId, now)
  }

  def createJob(name: String, apiInstanceId: String): JobScheduler =
    createJob(APIUtil.generateUUID(), name, apiInstanceId)

  /** Release the lock held by this row. */
  def delete(job: JobScheduler): Boolean = deleteByJobId(job.jobId)

  /** Delete the lock row with the given JobId; returns true if a row was removed. */
  def deleteByJobId(jobId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM jobscheduler WHERE jobid = $jobId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM jobscheduler".update.run)
    ()
  }
}
