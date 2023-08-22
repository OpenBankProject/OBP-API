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
}





