package code.payments

import code.util.MappedUUID
import com.openbankproject.commons.model.enums.TransactionRequestTypes
import net.liftweb.mapper.{MappedString, _}

class MappedPayment extends LongKeyedMapper[MappedPayment] with IdPK with CreatedUpdated {
  def getSingleton = MappedPayment

  object mPaymentId extends MappedUUID(this)
  object mEndToEndIdentification extends MappedString(this, 50)
  object mDebtorAccountIban extends MappedString(this, 50) { override def defaultValue = null }
  object mInstructedAmountCurrency extends MappedString(this, 10)
  object mInstructedAmountAmount extends MappedString(this, 20)
  object mCreditorAccountMsisdn extends MappedString(this, 50)
  object mCreditorAccountIban extends MappedString(this, 50)
  object mCreditorName extends MappedString(this, 100)
  object mCreditorId extends MappedString(this, 50)
  object mPurposeCode extends MappedString(this, 10)
  object mRemittanceInformationUnstructured extends MappedString(this, 200)
  object mCreditorCtryOfRes extends MappedString(this, 5)
  object mInstructionPriority extends MappedString(this, 10)
  object mPurposeType extends MappedString(this, 50)

  // Статус с дефолтным значением
  object mStatus extends MappedString(this, 10) { override def defaultValue = "RCVD" } // Default status "RCVD"

  // Тип с дефолтным значением
  object mType extends MappedString(this, 50) { override def defaultValue = TransactionRequestTypes.INSTANT_CREDIT_TRANSFERS_MD.toString() } // Default type "INSTANT_CREDIT_TRANSFERS_MD"

  def status: String = mStatus.get
  def transactionType: String = mType.get
}

object MappedPayment extends MappedPayment with LongKeyedMetaMapper[MappedPayment] {
  override def dbIndexes = UniqueIndex(mPaymentId) :: super.dbIndexes
}



