package code.apicollection

import net.liftweb.common.Box

trait ApiCollectionTrait {
  def apiCollectionId: String
  def userId: String
  def apiCollectionName: String
  def isSharable: Boolean
  def description: String
}

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
