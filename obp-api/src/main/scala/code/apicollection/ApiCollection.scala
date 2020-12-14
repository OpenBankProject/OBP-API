package code.apicollection

import code.util.MappedUUID
import net.liftweb.mapper._

class ApiCollection extends ApiCollectionTrait with LongKeyedMapper[ApiCollection] with IdPK with CreatedUpdated {
  def getSingleton = ApiCollection

  object ApiCollectionId extends MappedUUID(this)
  object UserId extends MappedString(this, 100)
  object ApiCollectionName extends MappedString(this, 100)
  object IsSharable extends MappedBoolean(this)

  override def apiCollectionId: String = ApiCollectionId.get    
  override def userId: String = UserId.get              
  override def apiCollectionName: String = ApiCollectionName.get
  override def isSharable: Boolean = IsSharable.get    
}

object ApiCollection extends ApiCollection with LongKeyedMetaMapper[ApiCollection] {
  override def dbIndexes = UniqueIndex(ApiCollectionId) :: UniqueIndex(UserId, ApiCollectionName) :: super.dbIndexes
}

trait ApiCollectionTrait {
  def apiCollectionId: String
  def userId: String
  def apiCollectionName: String
  def isSharable: Boolean
}