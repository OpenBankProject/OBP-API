package code.transactionrequests

import code.util.DefaultStringField
import net.liftweb.mapper._

class MappedTransactionRequestTypeCharge extends TransactionRequestTypeCharge with LongKeyedMapper[MappedTransactionRequestTypeCharge] with IdPK with CreatedUpdated{
  def getSingleton = MappedTransactionRequestTypeCharge

  object mTransactionRequestTypeId extends DefaultStringField(this)

  object mBankId extends DefaultStringField(this)

  object mChargeCurrency extends DefaultStringField(this)

  object mChargeAmount extends DefaultStringField(this)

  object mChargeSummary extends DefaultStringField(this)

  override def transactionRequestTypeId: String = mTransactionRequestTypeId.get

  override def bankId: String = mBankId.get

  override def chargeCurrency: String = mChargeCurrency.get
  
  override def chargeAmount: String = mChargeAmount.get

  override def chargeSummary: String = mChargeSummary.get
  
}

object MappedTransactionRequestTypeCharge extends MappedTransactionRequestTypeCharge with LongKeyedMetaMapper[MappedTransactionRequestTypeCharge] {
  
}

/**
  * This case class is used when there is no data in database and mocked empty data to show it to user.
  */
case class TransactionRequestTypeChargeMock(
                                            mTransactionRequestTypeId: String,
                                            mBankId: String,
                                            mChargeCurrency: String,
                                            mChargeAmount: String,
                                            mChargeSummary: String
                                            ) extends TransactionRequestTypeCharge {

  override def transactionRequestTypeId: String = mTransactionRequestTypeId

  override def bankId: String = mBankId

  override def chargeCurrency: String = mChargeCurrency

  override def chargeAmount: String = mChargeAmount

  override def chargeSummary: String = mChargeSummary
}


trait TransactionRequestTypeCharge {

  def transactionRequestTypeId: String

  def bankId: String

  def chargeCurrency: String

  def chargeAmount: String

  def chargeSummary: String
}

