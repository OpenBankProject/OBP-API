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

import code.util.MappedUUID
import net.liftweb.mapper._

class ApiCollection extends ApiCollectionTrait with LongKeyedMapper[ApiCollection] with IdPK with CreatedUpdated {
  def getSingleton = ApiCollection

  object ApiCollectionId extends MappedUUID(this)
  object UserId extends MappedString(this, 100)
  object ApiCollectionName extends MappedString(this, 100)
  object IsSharable extends MappedBoolean(this)
  object Description extends MappedString(this, 2000)

  override def apiCollectionId: String = ApiCollectionId.get    
  override def userId: String = UserId.get              
  override def apiCollectionName: String = ApiCollectionName.get
  override def isSharable: Boolean = IsSharable.get    
  override def description: String = Description.get    
}

object ApiCollection extends ApiCollection with LongKeyedMetaMapper[ApiCollection] {
  override def dbIndexes = UniqueIndex(ApiCollectionId) :: UniqueIndex(UserId, ApiCollectionName) :: super.dbIndexes
}

trait ApiCollectionTrait {
  def apiCollectionId: String
  def userId: String
  def apiCollectionName: String
  def isSharable: Boolean
  def description: String
}