package code.transaction_types

import code.TransactionTypes.TransactionTypeProvider
import code.model._
import code.TransactionTypes.TransactionType._

import code.util.DefaultStringField
import net.liftweb.common.Logger
import net.liftweb.json
import net.liftweb.mapper._
import java.util.Date

object MappedTransactionTypeProvider extends TransactionTypeProvider {



  override protected def getTransactionTypeFromProvider(TransactionTypeId: TransactionTypeId): Option[TransactionType] =
    MappedTransactionType.find(By(MappedTransactionType.mTransactionTypeId, TransactionTypeId.value)).flatMap(_.toTransactionType)

  override protected def getTransactionTypesForBankFromProvider(bankId: BankId): Some[List[TransactionType]] = {
    Some(MappedTransactionType.findAll(By(MappedTransactionType.mBankId, bankId.value)).flatMap(_.toTransactionType))
  }
}


class MappedTransactionType extends LongKeyedMapper[MappedTransactionType] with IdPK with CreatedUpdated {

  private val logger = Logger(classOf[MappedTransactionType])

  override def getSingleton = MappedTransactionType

  object mTransactionTypeId extends DefaultStringField(this)
  object mBankId extends DefaultStringField(this)
  object mShortCode extends DefaultStringField(this)
  object mSummary extends DefaultStringField(this)
  object mDescription extends DefaultStringField(this)


  object mCustomerFee_Currency extends MappedString(this, 3)
  //amount uses the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  object mCustomerFee_Amount extends MappedLong(this)

  def toTransactionType : Option[TransactionType] = {

    Some(
      TransactionType(
        id = TransactionTypeId(mTransactionTypeId.get),
        bankId = BankId(mBankId.get),
        shortCode= mShortCode.get,
        summary = mSummary.get,
        description = mDescription.get,
        charge = AmountOfMoney (
          currency = mCustomerFee_Currency.get,
          amount = mCustomerFee_Amount.get.toString
        )
      )
    )
  }
}

object MappedTransactionType extends MappedTransactionType with LongKeyedMetaMapper[MappedTransactionType] {
  override def dbIndexes = UniqueIndex(mTransactionTypeId) :: UniqueIndex(mBankId, mShortCode) :: super.dbIndexes
}