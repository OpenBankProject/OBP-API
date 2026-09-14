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

package code.transactionRequestAttribute

import code.util.{MappedUUID, NewAttributeQueryTrait, UUIDString}
import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import com.openbankproject.commons.model.{TransactionRequestAttributeTrait, BankId => ModelBankId, TransactionRequestId => ModelTransactionRequestId}
import net.liftweb.mapper._

import scala.collection.immutable.List


class TransactionRequestAttribute extends TransactionRequestAttributeTrait with LongKeyedMapper[TransactionRequestAttribute] with IdPK {
  override def getSingleton = TransactionRequestAttribute

  override def bankId: ModelBankId = ModelBankId(BankId.get)

  override def transactionRequestId: ModelTransactionRequestId = ModelTransactionRequestId(TransactionRequestId.get)

  override def transactionRequestAttributeId: String = TransactionRequestAttributeId.get

  override def name: String = Name.get

  override def attributeType: TransactionRequestAttributeType.Value = TransactionRequestAttributeType.withName(Type.get)

  override def value: String = `Value`.get
  
  override def isPersonal: Boolean = IsPersonal.get

  object BankId extends UUIDString(this) // combination of this

  object TransactionRequestId extends UUIDString(this) // combination of this

  object TransactionRequestAttributeId extends MappedUUID(this)

  object Name extends MappedString(this, 50)

  object Type extends MappedString(this, 50)

  // TEXT, not varchar(255): Open Corridor promise evidence stores the full
  // A1.1 preimage JSON here, which exceeds any fixed varchar bound.
  object `Value` extends MappedText(this)
  
  object IsPersonal extends MappedBoolean(this)

}

object TransactionRequestAttribute extends TransactionRequestAttribute with LongKeyedMetaMapper[TransactionRequestAttribute]
  with NewAttributeQueryTrait {
  override val ParentId: BaseMappedField = TransactionRequestId

  override def dbIndexes: List[BaseIndex[TransactionRequestAttribute]] = Index(TransactionRequestId) :: Index(TransactionRequestAttributeId) :: super.dbIndexes
}

