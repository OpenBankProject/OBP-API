package code.migration

import code.util.MappedUUID
import net.liftweb.mapper._

class MappedMigrationScriptLog extends MigrationScriptLog with LongKeyedMapper[MappedMigrationScriptLog] with IdPK with CreatedUpdated {

  def getSingleton = MappedMigrationScriptLog

  object mMigrationScriptLogId extends MappedUUID(this)
  object mName extends MappedString(this, 100)
  object mCommitId extends MappedString(this, 100)
  object mIsExecuted extends MappedBoolean(this)
  object mExecutedAt extends MappedLong(this)

  override def migrationScriptLogId: String = mMigrationScriptLogId.get
  override def name: String = mName.get
  override def commitId: String = mCommitId.get  
  override def isExecuted: Boolean = mIsExecuted.get  
  override def executedAt: Long = mExecutedAt.get  
  
}

object MappedMigrationScriptLog extends MappedMigrationScriptLog with LongKeyedMetaMapper[MappedMigrationScriptLog] {
  override def dbIndexes: List[BaseIndex[MappedMigrationScriptLog]] = UniqueIndex(mName, mIsExecuted) :: super.dbIndexes
}



