package code.featuredapicollection

import net.liftweb.common.Box

trait FeaturedApiCollectionTrait {
  def featuredApiCollectionId: String
  def apiCollectionId: String
  def sortOrder: Int
}

trait FeaturedApiCollectionsProvider {
  def createFeaturedApiCollection(
    apiCollectionId: String,
    sortOrder: Int
  ): Box[FeaturedApiCollectionTrait]

  def getFeaturedApiCollectionById(
    featuredApiCollectionId: String
  ): Box[FeaturedApiCollectionTrait]

  def getFeaturedApiCollectionByApiCollectionId(
    apiCollectionId: String
  ): Box[FeaturedApiCollectionTrait]

  def updateFeaturedApiCollection(
    featuredApiCollectionId: String,
    sortOrder: Int
  ): Box[FeaturedApiCollectionTrait]

  def getAllFeaturedApiCollections(): List[FeaturedApiCollectionTrait]

  def deleteFeaturedApiCollectionById(
    featuredApiCollectionId: String
  ): Box[Boolean]

  def deleteFeaturedApiCollectionByApiCollectionId(
    apiCollectionId: String
  ): Box[Boolean]
}
