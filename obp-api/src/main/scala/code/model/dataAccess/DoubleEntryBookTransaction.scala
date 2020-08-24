package code.model.dataAccess

import code.util.UUIDString
import com.openbankproject.commons.model.{TransactionRequestId => ModelTransactionRequestId, _}
import net.liftweb.mapper._

class DoubleEntryBookTransaction extends DoubleEntryBookTransactionTrait with LongKeyedMapper[DoubleEntryBookTransaction] with IdPK with CreatedUpdated {
  def getSingleton: DoubleEntryBookTransaction.type = DoubleEntryBookTransaction

  override def transactionRequestId: Option[ModelTransactionRequestId] = {
    val transactionRequestIdString = TransactionRequestId.get
    if (transactionRequestIdString.isEmpty) None else Some(ModelTransactionRequestId(transactionRequestIdString))
  }

  override def debitTransactionId: TransactionId = TransactionId(DebitTransactionId.get)

  override def creditTransactionId: TransactionId = TransactionId(CreditTransactionId.get)

  object TransactionRequestId extends UUIDString(this)

  object DebitTransactionId extends UUIDString(this)

  object CreditTransactionId extends UUIDString(this)

}

object DoubleEntryBookTransaction extends DoubleEntryBookTransaction with LongKeyedMetaMapper[DoubleEntryBookTransaction] {

  override def dbIndexes: List[BaseIndex[DoubleEntryBookTransaction]] =
    UniqueIndex(DebitTransactionId) :: UniqueIndex(CreditTransactionId) :: UniqueIndex(DebitTransactionId, CreditTransactionId) :: super.dbIndexes

}




