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

import code.util.MappedUUID
import net.liftweb.mapper._

class FeaturedApiCollection extends FeaturedApiCollectionTrait with LongKeyedMapper[FeaturedApiCollection] with IdPK with CreatedUpdated {
  def getSingleton = FeaturedApiCollection

  object FeaturedApiCollectionId extends MappedUUID(this)
  object ApiCollectionId extends MappedString(this, 100)
  object SortOrder extends MappedInt(this)

  override def featuredApiCollectionId: String = FeaturedApiCollectionId.get
  override def apiCollectionId: String = ApiCollectionId.get
  override def sortOrder: Int = SortOrder.get
}

object FeaturedApiCollection extends FeaturedApiCollection with LongKeyedMetaMapper[FeaturedApiCollection] {
  override def dbIndexes = UniqueIndex(FeaturedApiCollectionId) :: UniqueIndex(ApiCollectionId) :: super.dbIndexes
}

trait FeaturedApiCollectionTrait {
  def featuredApiCollectionId: String
  def apiCollectionId: String
  def sortOrder: Int
}
