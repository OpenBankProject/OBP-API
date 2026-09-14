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

package code.apicollection

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

trait ApiCollectionsProvider {
  def createApiCollection(
    userId: String,
    apiCollectionName: String,
    isSharable: Boolean,
    description: String
  ): Box[ApiCollectionTrait]

  def getApiCollectionById(
    apiCollectionId: String
  ): Box[ApiCollectionTrait]
  
  def updateApiCollectionById(apiCollectionId: String, 
                              name: String, 
                              description: String, 
                              isSharable: Boolean): Box[ApiCollectionTrait]

  def getApiCollectionByUserIdAndCollectionName(
    userId: String,
    apiCollectionName: String
  ): Box[ApiCollectionTrait] 
  
  def getAllApiCollections(): List[ApiCollectionTrait]
  
  def deleteApiCollectionById(
    apiCollectionId: String,
  ): Box[Boolean]
  
  def getApiCollectionsByUserId(
    userId: String
  ): List[ApiCollectionTrait]

}

object MappedApiCollectionsProvider extends MdcLoggable with ApiCollectionsProvider{
  
  override def createApiCollection(
    userId: String,
    apiCollectionName: String,
    isSharable: Boolean,
    description: String
  ): Box[ApiCollectionTrait] =
    tryo (
      ApiCollection
        .create
        .UserId(userId)
        .ApiCollectionName(apiCollectionName)
        .IsSharable(isSharable) 
        .Description(description) 
        .saveMe()
    )

  override def updateApiCollectionById(apiCollectionId: String, name: String, description: String, isSharable: Boolean): Box[ApiCollection] = {
    ApiCollection.find(By(ApiCollection.ApiCollectionId,apiCollectionId)).map { collection =>
      collection
        .ApiCollectionName(name)
        .Description(description)
        .IsSharable(isSharable)
        .saveMe()
    }
  }
  override def getApiCollectionById(
    apiCollectionId: String
  ) = ApiCollection.find(By(ApiCollection.ApiCollectionId,apiCollectionId))

  override def getAllApiCollections(): List[ApiCollectionTrait] = ApiCollection.findAll()

  override def getApiCollectionByUserIdAndCollectionName(
    userId: String,
    apiCollectionName: String
  ) = ApiCollection.find(By(ApiCollection.UserId, userId), By(ApiCollection.ApiCollectionName, apiCollectionName))
  
  override def deleteApiCollectionById(
    apiCollectionId: String,
  ): Box[Boolean]  =  ApiCollection.find(By(ApiCollection.ApiCollectionId,apiCollectionId)).map(_.delete_!)

  override def getApiCollectionsByUserId(
    userId: String
  ): List[ApiCollectionTrait] = ApiCollection.findAll(By(ApiCollection.UserId,userId))

}