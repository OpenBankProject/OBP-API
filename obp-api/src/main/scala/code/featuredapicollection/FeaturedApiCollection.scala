package code.featuredapicollection

import code.util.MappedUUID
import net.liftweb.mapper._

class FeaturedApiCollection extends FeaturedApiCollectionTrait with LongKeyedMapper[FeaturedApiCollection] with IdPK with CreatedUpdated {
  def getSingleton = FeaturedApiCollection

  object FeaturedApiCollectionId extends MappedUUID(this)
  object ApiCollectionId extends MappedString(this, 100)
  object SortOrder extends MappedInt(this)

  override def featuredApiCollectionId: String = FeaturedApiCollectionId.get
  override def apiCollectionId: String = ApiCollectionId.get
  override def sortOrder: Int = SortOrder.get
}

object FeaturedApiCollection extends FeaturedApiCollection with LongKeyedMetaMapper[FeaturedApiCollection] {
  override def dbIndexes = UniqueIndex(FeaturedApiCollectionId) :: UniqueIndex(ApiCollectionId) :: super.dbIndexes
}

trait FeaturedApiCollectionTrait {
  def featuredApiCollectionId: String
  def apiCollectionId: String
  def sortOrder: Int
}
