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

package code.transaction.internalMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.{BankId, TransactionId}
import net.liftweb.mapper._

class TransactionIdMapping extends TransactionIdMappingTrait with LongKeyedMapper[TransactionIdMapping] with IdPK with CreatedUpdated {

  def getSingleton = TransactionIdMapping

  object TransactionId extends MappedUUID(this)
  object TransactionPlainTextReference extends MappedString(this, 255)

  override def transactionId: TransactionId = com.openbankproject.commons.model.TransactionId(TransactionId.get)
  override def transactionPlainTextReference = TransactionPlainTextReference.get

}

object TransactionIdMapping extends TransactionIdMapping with LongKeyedMetaMapper[TransactionIdMapping] {
  //one transaction info per bank for each api user
  override def dbIndexes = UniqueIndex(TransactionId) :: UniqueIndex(TransactionId, TransactionPlainTextReference) :: super.dbIndexes
}