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

package code.customer.internalMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.{BankId, CustomerId}
import net.liftweb.mapper._

class MappedCustomerIdMapping extends CustomerIdMapping with LongKeyedMapper[MappedCustomerIdMapping] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerIdMapping

  object mCustomerId extends MappedUUID(this)
  object mCustomerPlainTextReference extends MappedString(this, 255)

  override def customerId = CustomerId(mCustomerId.get)
  override def customerPlainTextReference = mCustomerPlainTextReference.get


  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  object mBankId extends MappedString(this, 50)
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  object mCustomerNumber extends MappedString(this, 50)
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  override def bankId = BankId(mBankId.get)
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  override def customerNumber: String = mCustomerNumber.get

}

object MappedCustomerIdMapping extends MappedCustomerIdMapping with LongKeyedMetaMapper[MappedCustomerIdMapping] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mCustomerId) :: UniqueIndex(mCustomerId, mCustomerPlainTextReference) :: super.dbIndexes
}