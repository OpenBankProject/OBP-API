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

package code.etag

import net.liftweb.mapper._

class MappedETag extends MappedCacheTrait with LongKeyedMapper[MappedETag] with IdPK {
  
  def getSingleton = MappedETag

  object ETagResource extends MappedString(this, 1000)
  object ETagValue extends MappedString(this, 256)
  object LastUpdatedMSSinceEpoch extends MappedLong(this)

  override def eTagResource: String = ETagResource.get
  override def eTagValue: String = ETagValue.get
  override def lastUpdatedMSSinceEpoch: Long = LastUpdatedMSSinceEpoch.get
}

object MappedETag extends MappedETag with LongKeyedMetaMapper[MappedETag] {
  override def dbTableName = "ETag" // define the DB table name
  override def dbIndexes: List[BaseIndex[MappedETag]] = UniqueIndex(ETagResource) :: super.dbIndexes
}

trait MappedCacheTrait {
  def eTagResource: String
  def eTagValue: String
  def lastUpdatedMSSinceEpoch: Long
}
