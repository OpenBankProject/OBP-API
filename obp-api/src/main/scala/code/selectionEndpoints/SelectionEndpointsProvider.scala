package code.selectionEndpoints

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

trait SelectionEndpointsProvider {
  def createSelectionEndpoint(
    selectionId: String,
    operationId: String
  ): Box[SelectionEndpointTrait]

  def getSelectionEndpointById(
    selectionEndpointId: String
  ): Box[SelectionEndpointTrait]

  def getSelectionEndpoints(
    selectionId: String
  ): List[SelectionEndpointTrait]

  def deleteSelectionEndpointById(
    selectionEndpointId: String,
  ): Box[Boolean]
  
}

object MappedSelectionEndpointsProvider extends MdcLoggable with SelectionEndpointsProvider{
  
  override def createSelectionEndpoint(
    selectionId: String,
    operationId: String
  ): Box[SelectionEndpointTrait] =
    tryo (
      SelectionEndpoint
        .create
        .SelectionId(selectionId)
        .OperationId(operationId)
        .saveMe()
    )

  override def getSelectionEndpoints(
    selectionId: String
  ) = SelectionEndpoint.findAll(By(SelectionEndpoint.SelectionId,selectionId))
  
  override def getSelectionEndpointById(
    selectionEndpointId: String
  ) = SelectionEndpoint.find(By(SelectionEndpoint.SelectionEndpointId,selectionEndpointId))

  override def deleteSelectionEndpointById(
    selectionEndpointId: String,
  ): Box[Boolean]  =  SelectionEndpoint.find(By(SelectionEndpoint.SelectionEndpointId,selectionEndpointId)).map(_.delete_!)

}