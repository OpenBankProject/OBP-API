package code.scheduler

import code.util.MappedUUID
import net.liftweb.mapper._

class JobScheduler extends JobSchedulerTrait with LongKeyedMapper[JobScheduler] with IdPK with CreatedUpdated {

  def getSingleton = JobScheduler

  object JobId extends MappedUUID(this)
  object Name extends MappedString(this, 100)
  object ApiInstanceId extends MappedString(this, 100)

  override def primaryKey: Long = id.get
  override def jobId: String = JobId.get
  override def name: String = Name.get
  override def apiInstanceId: String = ApiInstanceId.get
  
}

object JobScheduler extends JobScheduler with LongKeyedMetaMapper[JobScheduler] {
  override def dbIndexes: List[BaseIndex[JobScheduler]] = UniqueIndex(JobId) :: super.dbIndexes

  /**
   * The most recent scheduler-lock rows, newest first, capped at `limit`.
   *
   * Note: `jobscheduler` is a lock table, not a job-history log — a row exists
   * only while a job holds the lock and is deleted when the job finishes. In
   * healthy operation this returns an empty list; any rows present are either
   * currently running or stale locks left by a dead JVM.
   */
  def mostRecent(limit: Int): List[JobScheduler] =
    findAll(OrderBy(JobScheduler.createdAt, Descending), MaxRows(limit))

  /** Delete the lock row with the given JobId; returns true if a row was removed. */
  def deleteByJobId(jobId: String): Boolean =
    find(By(JobScheduler.JobId, jobId)) match {
      case net.liftweb.common.Full(job) => delete_!(job)
      case _                            => false
    }
}





