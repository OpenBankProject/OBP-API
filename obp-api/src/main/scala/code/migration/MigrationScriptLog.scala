package code.migration

import code.util.MappedUUID
import net.liftweb.mapper._

class MigrationScriptLog extends MigrationScriptLogTrait with LongKeyedMapper[MigrationScriptLog] with IdPK with CreatedUpdated {

  def getSingleton = MigrationScriptLog

  object MigrationScriptLogId extends MappedUUID(this)
  object Name extends MappedString(this, 100)
  object CommitId extends MappedString(this, 100)
  object IsSuccessful extends MappedBoolean(this)
  object StartDate extends MappedLong(this)
  object EndDate extends MappedLong(this)
  object Remark extends MappedString(this, 1024)

  override def primaryKey: Long = id.get
  override def migrationScriptLogId: String = MigrationScriptLogId.get
  override def name: String = Name.get
  override def commitId: String = CommitId.get  
  override def isSuccessful: Boolean = IsSuccessful.get  
  override def startDate: Long = StartDate.get  
  override def endDate: Long = EndDate.get  
  override def remark: String = Remark.get  
  
}

object MigrationScriptLog extends MigrationScriptLog with LongKeyedMetaMapper[MigrationScriptLog] {
  override def dbIndexes: List[BaseIndex[MigrationScriptLog]] = UniqueIndex(Name, IsSuccessful) :: super.dbIndexes
}



