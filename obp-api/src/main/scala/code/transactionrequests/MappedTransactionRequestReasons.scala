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



