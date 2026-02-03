package code.featuredapicollection

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.{By, OrderBy, Ascending}
import net.liftweb.util.Helpers.tryo

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

object MappedFeaturedApiCollectionsProvider extends MdcLoggable with FeaturedApiCollectionsProvider {

  override def createFeaturedApiCollection(
    apiCollectionId: String,
    sortOrder: Int
  ): Box[FeaturedApiCollectionTrait] =
    tryo(
      FeaturedApiCollection
        .create
        .ApiCollectionId(apiCollectionId)
        .SortOrder(sortOrder)
        .saveMe()
    )

  override def getFeaturedApiCollectionById(
    featuredApiCollectionId: String
  ): Box[FeaturedApiCollectionTrait] =
    FeaturedApiCollection.find(By(FeaturedApiCollection.FeaturedApiCollectionId, featuredApiCollectionId))

  override def getFeaturedApiCollectionByApiCollectionId(
    apiCollectionId: String
  ): Box[FeaturedApiCollectionTrait] =
    FeaturedApiCollection.find(By(FeaturedApiCollection.ApiCollectionId, apiCollectionId))

  override def updateFeaturedApiCollection(
    featuredApiCollectionId: String,
    sortOrder: Int
  ): Box[FeaturedApiCollectionTrait] = {
    FeaturedApiCollection.find(By(FeaturedApiCollection.FeaturedApiCollectionId, featuredApiCollectionId)).map { featured =>
      featured
        .SortOrder(sortOrder)
        .saveMe()
    }
  }

  override def getAllFeaturedApiCollections(): List[FeaturedApiCollectionTrait] =
    FeaturedApiCollection.findAll(OrderBy(FeaturedApiCollection.SortOrder, Ascending))

  override def deleteFeaturedApiCollectionById(
    featuredApiCollectionId: String
  ): Box[Boolean] =
    FeaturedApiCollection.find(By(FeaturedApiCollection.FeaturedApiCollectionId, featuredApiCollectionId)).map(_.delete_!)

  override def deleteFeaturedApiCollectionByApiCollectionId(
    apiCollectionId: String
  ): Box[Boolean] =
    FeaturedApiCollection.find(By(FeaturedApiCollection.ApiCollectionId, apiCollectionId)).map(_.delete_!)
}
