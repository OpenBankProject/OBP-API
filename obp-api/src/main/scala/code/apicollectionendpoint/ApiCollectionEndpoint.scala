package code.apicollectionendpoint

import code.util.MappedUUID
import net.liftweb.mapper._

class ApiCollectionEndpoint extends ApiCollectionEndpointTrait with LongKeyedMapper[ApiCollectionEndpoint] with IdPK with CreatedUpdated {
  def getSingleton = ApiCollectionEndpoint

  object ApiCollectionEndpointId extends MappedUUID(this)
  object ApiCollectionId extends MappedString(this, 100)
  object OperationId extends MappedString(this, 100)

  override def apiCollectionEndpointId: String = ApiCollectionEndpointId.get    
  override def apiCollectionId: String = ApiCollectionId.get    
  override def operationId: String = OperationId.get    
}

object ApiCollectionEndpoint extends ApiCollectionEndpoint with LongKeyedMetaMapper[ApiCollectionEndpoint] {
  override def dbIndexes = UniqueIndex(ApiCollectionEndpointId) :: UniqueIndex(ApiCollectionId, OperationId) ::  super.dbIndexes
}

trait ApiCollectionEndpointTrait {
  def apiCollectionEndpointId: String
  def apiCollectionId: String
  def operationId: String
}
