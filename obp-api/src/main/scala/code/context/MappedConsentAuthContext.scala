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

package code.context

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.ConsentAuthContext
import net.liftweb.mapper._

class MappedConsentAuthContext extends ConsentAuthContext with LongKeyedMapper[MappedConsentAuthContext] with IdPK with CreatedUpdated {

  def getSingleton = MappedConsentAuthContext

  object ConsentAuthContextId extends MappedUUID(this)
  object ConsentId extends UUIDString(this)
  object Key extends MappedString(this, 255)
  object `Value` extends MappedString(this, 255)

  override def consentId = ConsentId.get   
  override def key = Key.get  
  override def value = `Value`.get  
  override def consentAuthContextId = ConsentAuthContextId.get
  override def timeStamp = createdAt.get
}

object MappedConsentAuthContext extends MappedConsentAuthContext with LongKeyedMetaMapper[MappedConsentAuthContext] {
  override def dbTableName = "ConsentAuthContext" // define a custom DB table name
  override def dbIndexes = UniqueIndex(ConsentId, Key, createdAt) :: super.dbIndexes
}
