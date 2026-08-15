package code.transaction.internalMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.{BankId, TransactionId}
import net.liftweb.mapper._

class TransactionIdMapping extends TransactionIdMappingTrait with LongKeyedMapper[TransactionIdMapping] with IdPK with CreatedUpdated {

  def getSingleton: code.transaction.internalMapping.TransactionIdMapping.type = TransactionIdMapping

  object TransactionId extends MappedUUID(this)
  object TransactionPlainTextReference extends MappedString(this, 255)

  override def transactionId: TransactionId = com.openbankproject.commons.model.TransactionId(TransactionId.get)
  override def transactionPlainTextReference = TransactionPlainTextReference.get

}

object TransactionIdMapping extends TransactionIdMapping with LongKeyedMetaMapper[TransactionIdMapping] {
  //one transaction info per bank for each api user
  override def dbIndexes = UniqueIndex(TransactionId) :: UniqueIndex(TransactionId, TransactionPlainTextReference) :: super.dbIndexes
}