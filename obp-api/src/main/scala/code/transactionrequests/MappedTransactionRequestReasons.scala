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

package code.transactionrequests

import code.api.util.APIUtil
import code.util.UUIDString
import com.openbankproject.commons.model.TransactionRequestReasonsTrait
import net.liftweb.mapper._

class TransactionRequestReasons extends TransactionRequestReasonsTrait with LongKeyedMapper[TransactionRequestReasons] with IdPK with CreatedUpdated{
  def getSingleton = TransactionRequestReasons

  object TransactionRequestReasonId extends UUIDString(this) {
    override def defaultValue = APIUtil.generateUUID()
  }
  object TransactionRequestId extends UUIDString(this)
  object Code extends MappedString(this, 8)
  object DocumentNumber extends MappedString(this, 100)
  object Currency extends MappedString(this, 3)
  object Amount extends MappedString(this, 32)
  object Description extends MappedString(this, 2048)

  override def transactionRequestReasonId: String = TransactionRequestReasonId.get
  override def transactionRequestId: String = TransactionRequestId.get
  override def code: String = Code.get
  override def documentNumber: String = DocumentNumber.get
  override def amount: String = Amount.get
  override def currency: String = Currency.get
  override def description: String = Description.get
  
}

object TransactionRequestReasons extends TransactionRequestReasons with LongKeyedMetaMapper[TransactionRequestReasons] {}



