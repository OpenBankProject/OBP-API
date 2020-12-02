package code.selectionEndpoints

import code.util.MappedUUID
import net.liftweb.mapper._

class SelectionEndpoint extends SelectionEndpointTrait with LongKeyedMapper[SelectionEndpoint] with IdPK with CreatedUpdated {
  def getSingleton = SelectionEndpoint

  object SelectionEndpointId extends MappedUUID(this)
  object SelectionId extends MappedString(this, 100)
  object OperationId extends MappedString(this, 100)

  override def selectionEndpointId: String = SelectionEndpointId.get    
  override def selectionId: String = SelectionId.get    
  override def operationId: String = OperationId.get    
}

object SelectionEndpoint extends SelectionEndpoint with LongKeyedMetaMapper[SelectionEndpoint] {
  override def dbIndexes = UniqueIndex(SelectionEndpointId) :: super.dbIndexes
}

trait SelectionEndpointTrait {
  def selectionEndpointId: String
  def selectionId: String
  def operationId: String
}
