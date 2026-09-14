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

import net.liftweb.mapper._

class MappedBankAccountData extends LongKeyedMapper[MappedBankAccountData] with IdPK with CreatedUpdated {

  override def getSingleton = MappedBankAccountData

  object bankId extends MappedString(this, 255)
  def getBankId = bankId.get
  def setBankId(value: String) = bankId.set(value)

  object accountId extends MappedString(this, 255)
  def getAccountId = accountId.get
  def setAccountId(value: String) = accountId.set(value)

  object accountLabel extends MappedString(this, 255)
  def getLabel = accountLabel.get
  def setLabel(value: String) = accountLabel.set(value)

}

object MappedBankAccountData extends MappedBankAccountData with LongKeyedMetaMapper[MappedBankAccountData] {
  override def dbIndexes = UniqueIndex(bankId, accountId) :: super.dbIndexes
}
