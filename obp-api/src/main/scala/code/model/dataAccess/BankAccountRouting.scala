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

package code.model.dataAccess

import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model.{AccountId => ModelAccountId, BankId => ModelBankId, _}
import net.liftweb.mapper._

class BankAccountRouting extends BankAccountRoutingTrait with LongKeyedMapper[BankAccountRouting] with IdPK with CreatedUpdated {
  def getSingleton: BankAccountRouting.type = BankAccountRouting

  override def bankId: ModelBankId = ModelBankId(BankId.get)

  override def accountId: ModelAccountId = ModelAccountId(AccountId.get)

  override def accountRouting: AccountRouting = AccountRouting(AccountRoutingScheme.get, AccountRoutingAddress.get)

  object BankId extends UUIDString(this)

  object AccountId extends AccountIdString(this)

  object AccountRoutingScheme extends MappedString(this, 32)

  object AccountRoutingAddress extends MappedString(this, 128)

}

object BankAccountRouting extends BankAccountRouting with LongKeyedMetaMapper[BankAccountRouting] {

  override def dbIndexes: List[BaseIndex[BankAccountRouting]] =
    UniqueIndex(BankId, AccountId, AccountRoutingScheme) :: UniqueIndex(BankId, AccountRoutingScheme, AccountRoutingAddress) :: super.dbIndexes

}

