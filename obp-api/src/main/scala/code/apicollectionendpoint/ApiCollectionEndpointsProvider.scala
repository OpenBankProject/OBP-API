package code.apicollectionendpoint

import net.liftweb.common.Box

trait ApiCollectionEndpointTrait {
  def apiCollectionEndpointId: String
  def apiCollectionId: String
  def operationId: String
}

trait ApiCollectionEndpointsProvider {
  def createApiCollectionEndpoint(
    apiCollectionId: String,
    operationId: String
  ): Box[ApiCollectionEndpointTrait]

  def getApiCollectionEndpointById(
    apiCollectionEndpointId: String
  ): Box[ApiCollectionEndpointTrait]

  def getApiCollectionEndpointByApiCollectionIdAndOperationId(
    apiCollectionId: String,
    operationId: String,
  ): Box[ApiCollectionEndpointTrait]

  def getApiCollectionEndpoints(
    apiCollectionId: String
  ): List[ApiCollectionEndpointTrait]

  def deleteApiCollectionEndpointById(
    apiCollectionEndpointId: String,
  ): Box[Boolean]

}