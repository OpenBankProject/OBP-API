package code.transactionRequestAttribute

import code.util.{AttributeQueryTrait, MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import com.openbankproject.commons.model.{BankId, TransactionRequestAttribute, TransactionRequestId}
import net.liftweb.mapper._

import scala.collection.immutable.List


class MappedTransactionRequestAttribute extends TransactionRequestAttribute with LongKeyedMapper[MappedTransactionRequestAttribute] with IdPK {
  override def getSingleton = MappedTransactionRequestAttribute

  override def bankId: BankId = BankId(mBankId.get)

  override def transactionRequestId: TransactionRequestId = TransactionRequestId(mTransactionRequestId.get)

  override def transactionRequestAttributeId: String = mTransactionRequestAttributeId.get

  override def name: String = mName.get

  override def attributeType: TransactionRequestAttributeType.Value = TransactionRequestAttributeType.withName(mType.get)

  override def value: String = mValue.get

  object mBankId extends UUIDString(this) // combination of this

  object mTransactionRequestId extends UUIDString(this) // combination of this

  object mTransactionRequestAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)

}

object MappedTransactionRequestAttribute extends MappedTransactionRequestAttribute with LongKeyedMetaMapper[MappedTransactionRequestAttribute]
  with AttributeQueryTrait {
  override val mParentId: BaseMappedField = mTransactionRequestId

  override def dbIndexes: List[BaseIndex[MappedTransactionRequestAttribute]] = Index(mTransactionRequestId) :: Index(mTransactionRequestAttributeId) :: super.dbIndexes
}

