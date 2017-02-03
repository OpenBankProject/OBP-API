package code.transactionrequests

import code.model.AmountOfMoney
import code.util.DefaultStringField
import net.liftweb.mapper.{CreatedUpdated, IdPK, LongKeyedMapper, LongKeyedMetaMapper}

class MappedTransactionRequestType extends TransactionRequestType with LongKeyedMapper[MappedTransactionRequestType] with IdPK with CreatedUpdated{
  def getSingleton = MappedTransactionRequestType

  object mName extends DefaultStringField(this)

  object mBankId extends DefaultStringField(this)

  object mChargeCurrency extends DefaultStringField(this)

  object mChargeAmount extends DefaultStringField(this)

  object mChargeSummary extends DefaultStringField(this)

  override def name: String = mName.get

  override def bankId: String = mBankId.get

  override def chargeCurrency: String = mChargeCurrency.get

  override def chargeAmount: String = mChargeAmount.get

  override def chargeSummary: String = mChargeSummary.get
  
}

object MappedTransactionRequestType extends MappedTransactionRequestType with LongKeyedMetaMapper[MappedTransactionRequestType] {
  
}

trait TransactionRequestType {

  def name: String

  def bankId: String

  def chargeCurrency: String

  def chargeAmount: String

  def chargeSummary: String
}

case class Charge(
  val chargeCurrency: String,
  
  val chargeAmount: String,
  
  val chargeSummary: String
)