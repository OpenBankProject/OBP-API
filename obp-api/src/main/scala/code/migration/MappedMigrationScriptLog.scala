package code.migration

import code.util.MappedUUID
import net.liftweb.mapper._

class MappedMigrationScriptLog extends MigrationScriptLog with LongKeyedMapper[MappedMigrationScriptLog] with IdPK with CreatedUpdated {

  def getSingleton = MappedMigrationScriptLog

  object mMigrationScriptLogId extends MappedUUID(this)
  object mName extends MappedString(this, 100)
  object mCommitId extends MappedString(this, 100)
  object mIsSuccessful extends MappedBoolean(this)
  object mStartDate extends MappedLong(this)
  object mEndDate extends MappedLong(this)
  object mComment extends MappedString(this, 1024)

  override def migrationScriptLogId: String = mMigrationScriptLogId.get
  override def name: String = mName.get
  override def commitId: String = mCommitId.get  
  override def isSuccessful: Boolean = mIsSuccessful.get  
  override def startDate: Long = mStartDate.get  
  override def endDate: Long = mEndDate.get  
  override def comment: String = mComment.get  
  
}

object MappedMigrationScriptLog extends MappedMigrationScriptLog with LongKeyedMetaMapper[MappedMigrationScriptLog] {
  override def dbIndexes: List[BaseIndex[MappedMigrationScriptLog]] = UniqueIndex(mName, mIsSuccessful) :: super.dbIndexes
}



