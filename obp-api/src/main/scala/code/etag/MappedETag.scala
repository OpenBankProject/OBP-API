package code.etag

import net.liftweb.mapper._

class MappedETag extends MappedCacheTrait with LongKeyedMapper[MappedETag] with IdPK {
  
  def getSingleton = MappedETag

  object ETagResource extends MappedString(this, 1000)
  object ETagValue extends MappedString(this, 256)
  object LastUpdatedMSSinceEpoch extends MappedLong(this)

  override def eTagResource: String = ETagResource.get
  override def eTagValue: String = ETagValue.get
  override def lastUpdatedMSSinceEpoch: Long = LastUpdatedMSSinceEpoch.get
}

object MappedETag extends MappedETag with LongKeyedMetaMapper[MappedETag] {
  override def dbTableName = "ETag" // define the DB table name
  override def dbIndexes: List[BaseIndex[MappedETag]] = UniqueIndex(ETagResource) :: super.dbIndexes
}

trait MappedCacheTrait {
  def eTagResource: String
  def eTagValue: String
  def lastUpdatedMSSinceEpoch: Long
}
