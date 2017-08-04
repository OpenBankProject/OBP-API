package code.transaction_types

import code.TransactionTypes.TransactionTypeProvider
import code.model._
import code.TransactionTypes.TransactionType._

import code.util.{MediumString, UUIDString}
import net.liftweb.common._
import net.liftweb.mapper._
import code.api.util.ErrorMessages
import code.api.v2_0_0.TransactionTypeJsonV200
import net.liftweb.util.Helpers._
import java.util.Date

object MappedTransactionTypeProvider extends TransactionTypeProvider {


  override protected def getTransactionTypeFromProvider(TransactionTypeId: TransactionTypeId): Option[TransactionType] =
    MappedTransactionType.find(By(MappedTransactionType.mTransactionTypeId, TransactionTypeId.value)).flatMap(_.toTransactionType)

  override protected def getTransactionTypesForBankFromProvider(bankId: BankId): Some[List[TransactionType]] = {
    Some(MappedTransactionType.findAll(By(MappedTransactionType.mBankId, bankId.value)).flatMap(_.toTransactionType))
  }

  /**
    * This method will create or update the data. It need to check the bank_id & short_code and TransactionTypeId to make the data is
    * uniqueness in the database
    *
    */
  override def createOrUpdateTransactionTypeAtProvider(transactionType: TransactionTypeJsonV200): Box[TransactionType] = {

    // get the Input data from GUI and prepare to store and return
    val mappedTransactionType = MappedTransactionType.create
      .mTransactionTypeId(transactionType.id.toString)
      .mBankId(transactionType.bank_id)
      .mShortCode(transactionType.short_code)
      .mSummary(transactionType.summary)
      .mDescription(transactionType.description)
      .mCustomerFee_Currency(transactionType.charge.currency.toString)
      .mCustomerFee_Amount(transactionType.charge.amount.toString.toLong)

    //check the transactionTypeId existence and update or insert data
    TransactionTypeProvider.vend.getTransactionType(transactionType.id) match {
      case Full(f) =>
        tryo {
          for {
            mappedTransactionTypeUpdate <- MappedTransactionType.find(By(MappedTransactionType.mTransactionTypeId, transactionType.id.toString))
          } yield {
            mappedTransactionTypeUpdate.updateAllFields(mappedTransactionType)
            mappedTransactionTypeUpdate.save
          }
          mappedTransactionType.toTransactionType.get
        } ?~! ErrorMessages.CreateTransactionTypeUpdateError
      case _ =>
        tryo {
          mappedTransactionType.save
          mappedTransactionType.toTransactionType.get
        } ?~! ErrorMessages.CreateTransactionTypeInsertError
    }
  }

}
class MappedTransactionType extends LongKeyedMapper[MappedTransactionType] with IdPK with CreatedUpdated {

  private val logger = Logger(classOf[MappedTransactionType])

  override def getSingleton = MappedTransactionType

  object mTransactionTypeId extends UUIDString(this)
  object mBankId extends UUIDString(this)
  object mShortCode extends MappedString(this,20)
  object mSummary extends MappedString(this, 64)
  object mDescription extends MappedString(this, 2000)


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

  def updateAllFields(mappedTransactionType: MappedTransactionType): Box[MappedTransactionType] = {
    mTransactionTypeId.set(mappedTransactionType.mTransactionTypeId.get)
    mBankId.set(mappedTransactionType.mBankId.get)
    mShortCode.set(mappedTransactionType.mShortCode.get)
    mSummary.set(mappedTransactionType.mSummary.get)
    mDescription.set(mappedTransactionType.mDescription.get)
    mCustomerFee_Currency.set(mappedTransactionType.mCustomerFee_Currency.get)
    mCustomerFee_Amount.set(mappedTransactionType.mCustomerFee_Amount.get)
    Some(this)
  }
}

object MappedTransactionType extends MappedTransactionType with LongKeyedMetaMapper[MappedTransactionType] {
  override def dbIndexes = UniqueIndex(mTransactionTypeId) :: UniqueIndex(mBankId, mShortCode) :: super.dbIndexes
}