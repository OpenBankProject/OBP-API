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
