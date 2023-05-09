package code.etag

import net.liftweb.mapper._

class MappedCache extends MappedCacheTrait with LongKeyedMapper[MappedCache] with IdPK {
  
  def getSingleton = MappedCache

  object CacheKey extends MappedString(this, 1000)
  object CacheValue extends MappedString(this, 1000)
  object CacheNamespace extends MappedString(this, 200)
  object LastUpdatedEpochTime extends MappedLong(this)

  override def cacheKey: String = CacheKey.get
  override def cacheValue: String = CacheValue.get
  override def cacheNamespace: String = CacheNamespace.get
  override def lastUpdatedEpochTime: Long = LastUpdatedEpochTime.get
}

object MappedCache extends MappedCache with LongKeyedMetaMapper[MappedCache] {
  override def dbTableName = "Cache" // define the DB table name
  override def dbIndexes: List[BaseIndex[MappedCache]] = UniqueIndex(CacheKey) :: super.dbIndexes
}

trait MappedCacheTrait {
  def cacheKey: String
  def cacheValue: String
  def cacheNamespace: String
  def lastUpdatedEpochTime: Long
}
