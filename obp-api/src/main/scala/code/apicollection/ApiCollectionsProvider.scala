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