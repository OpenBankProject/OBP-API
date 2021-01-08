package code.apicollectionendpoint

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

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

object MappedApiCollectionEndpointsProvider extends MdcLoggable with ApiCollectionEndpointsProvider{
  
  override def createApiCollectionEndpoint(
    apiCollectionId: String,
    operationId: String
  ): Box[ApiCollectionEndpointTrait] =
    tryo (
      ApiCollectionEndpoint
        .create
        .ApiCollectionId(apiCollectionId)
        .OperationId(operationId)
        .saveMe()
    )

  override def getApiCollectionEndpointByApiCollectionIdAndOperationId(
    apiCollectionId: String,
    operationId: String,
  ) = ApiCollectionEndpoint.find(
    By(ApiCollectionEndpoint.ApiCollectionId, apiCollectionId),
    By(ApiCollectionEndpoint.OperationId,operationId)
  )
  
  override def getApiCollectionEndpoints(
    apiCollectionId: String
  ) = ApiCollectionEndpoint.findAll(By(ApiCollectionEndpoint.ApiCollectionId,apiCollectionId))
  
  override def getApiCollectionEndpointById(
    apiCollectionEndpointId: String
  ) = ApiCollectionEndpoint.find(By(ApiCollectionEndpoint.ApiCollectionEndpointId,apiCollectionEndpointId))

  override def deleteApiCollectionEndpointById(
    apiCollectionEndpointId: String,
  ): Box[Boolean]  =  ApiCollectionEndpoint.find(By(ApiCollectionEndpoint.ApiCollectionEndpointId,apiCollectionEndpointId)).map(_.delete_!)

}