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

package code.api.pemusage

import code.util.Helper.MdcLoggable
import net.liftweb.mapper._

import scala.collection.immutable.List

object MappedPemUsageProvider extends PemUsageProviderTrait with MdcLoggable {
  
}

class PemUsage extends PemUsageTrait with LongKeyedMapper[PemUsage] with IdPK with CreatedUpdated {
  override def getSingleton = PemUsage
  object PemHash extends MappedString(this, 50)
  object ConsumerId extends MappedString(this, 50)
  object LastUserId extends MappedString(this, 50)

  def pemHash: String = PemHash.get
  def consumerId: String = ConsumerId.get
  def lastUserId: String = LastUserId.get

}

object PemUsage extends PemUsage with LongKeyedMetaMapper[PemUsage] {
  override def dbIndexes: List[BaseIndex[PemUsage]] = UniqueIndex(PemHash) :: super.dbIndexes
}
