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

package code.model.dataAccess.internalMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.{BankId, AccountId}
import net.liftweb.mapper._

class AccountIdMapping extends AccountIdMappingT with LongKeyedMapper[AccountIdMapping] with IdPK with CreatedUpdated {

  def getSingleton = AccountIdMapping

  object mAccountId extends MappedUUID(this)
  object mAccountPlainTextReference extends MappedString(this, 255)

  override def accountId = AccountId(mAccountId.get)
  override def accountPlainTextReference = mAccountPlainTextReference.get
  
}

object AccountIdMapping extends AccountIdMapping with LongKeyedMetaMapper[AccountIdMapping] {
  //one account info per bank for each api user
  override def dbIndexes = UniqueIndex(mAccountId) :: UniqueIndex(mAccountId, mAccountPlainTextReference) :: super.dbIndexes
}