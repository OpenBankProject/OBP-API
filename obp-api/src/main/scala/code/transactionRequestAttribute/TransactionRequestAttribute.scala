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

