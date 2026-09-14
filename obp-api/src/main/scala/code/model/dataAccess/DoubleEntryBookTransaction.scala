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
import com.openbankproject.commons.model.{TransactionRequestId => ModelTransactionRequestId, _}
import net.liftweb.mapper._

class DoubleEntryBookTransaction extends DoubleEntryBookTransactionTrait with LongKeyedMapper[DoubleEntryBookTransaction] with IdPK with CreatedUpdated {
  def getSingleton: DoubleEntryBookTransaction.type = DoubleEntryBookTransaction

  override def transactionRequestBankId: Option[BankId] = {
    val transactionRequestBankIdString = TransactionRequestBankId.get
    if (transactionRequestBankIdString.isEmpty) None else Some(BankId(transactionRequestBankIdString))
  }
  override def transactionRequestAccountId: Option[AccountId] = {
    val transactionRequestAccountIdString = TransactionRequestAccountId.get
    if (transactionRequestAccountIdString.isEmpty) None else Some(AccountId(transactionRequestAccountIdString))
  }
  override def transactionRequestId: Option[ModelTransactionRequestId] = {
    val transactionRequestIdString = TransactionRequestId.get
    if (transactionRequestIdString.isEmpty) None else Some(ModelTransactionRequestId(transactionRequestIdString))
  }

  override def debitTransactionBankId: BankId = BankId(DebitTransactionBankId.get)
  override def debitTransactionAccountId: AccountId = AccountId(DebitTransactionAccountId.get)
  override def debitTransactionId: TransactionId = TransactionId(DebitTransactionId.get)

  override def creditTransactionBankId: BankId = BankId(CreditTransactionBankId.get)
  override def creditTransactionAccountId: AccountId = AccountId(CreditTransactionAccountId.get)
  override def creditTransactionId: TransactionId = TransactionId(CreditTransactionId.get)


  object TransactionRequestBankId extends MappedString(this, 255)
  object TransactionRequestAccountId extends AccountIdString(this)
  object TransactionRequestId extends UUIDString(this)

  object DebitTransactionBankId extends MappedString(this, 255)
  object DebitTransactionAccountId extends AccountIdString(this)
  object DebitTransactionId extends UUIDString(this)

  object CreditTransactionBankId extends MappedString(this, 255)
  object CreditTransactionAccountId extends AccountIdString(this)
  object CreditTransactionId extends UUIDString(this)

}

object DoubleEntryBookTransaction extends DoubleEntryBookTransaction with LongKeyedMetaMapper[DoubleEntryBookTransaction] {

  override def dbIndexes: List[BaseIndex[DoubleEntryBookTransaction]] =
    UniqueIndex(DebitTransactionBankId, DebitTransactionAccountId, DebitTransactionId) ::
    UniqueIndex(CreditTransactionBankId, CreditTransactionAccountId, CreditTransactionId) ::
    super.dbIndexes

}




