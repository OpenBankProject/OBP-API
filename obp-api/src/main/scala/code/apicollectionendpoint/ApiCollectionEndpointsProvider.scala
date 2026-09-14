/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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