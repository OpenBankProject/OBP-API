package code.transfers

import code.model._
import code.transfers.Transfers._

import code.util.DefaultStringField
import net.liftweb.json
import net.liftweb.mapper._
import java.util.Date

object MappedTransfersProvider extends TransfersProvider {

  override protected def getTransferFromProvider(transferId: code.transfers.Transfers.TransferId): Option[Transfer] =
    MappedTransfer.find(By(MappedTransfer.mTransferId, transferId.value))

  override protected def getTransfersFromProvider(bankId: BankId, accountId: AccountId, viewId: ViewId): Some[List[MappedTransfer]] = {
    Some(MappedTransfer.findAll(By(MappedTransfer.mBody_To_BankId, bankId.value), By(MappedTransfer.mBody_To_AccountId, accountId.value)))
  }
}

class MappedTransfer extends Transfer with LongKeyedMapper[MappedTransfer] with IdPK {

  override def getSingleton = MappedTransfer

  object mTransferId extends DefaultStringField(this)
  object mType extends DefaultStringField(this)
  object mFrom_BankId extends DefaultStringField(this)
  object mFrom_AccountId extends DefaultStringField(this)

  //sandbox body fields
  object mBody_To_BankId extends DefaultStringField(this)
  object mBody_To_AccountId extends DefaultStringField(this)
  object mBody_Value_Currency extends DefaultStringField(this)
  object mBody_Value_Amount extends DefaultStringField(this)
  object mBody_Description extends DefaultStringField(this)

  //other types (sepa, bitcoin, ?)
  //object mBody_To_IBAN extends DefaultStringField(this)
  //...

  object mTransactionIDs extends DefaultStringField(this)
  object mStatus extends DefaultStringField(this)
  object mStartDate extends MappedDate(this)
  object mEndDate extends MappedDate(this)
  object mChallenge_Id extends DefaultStringField(this)
  object mChallenge_AllowedAttempts extends MappedInt(this)
  object mChallenge_ChallengeType extends DefaultStringField(this)

  override def transferId: TransferId = new TransferId(mTransferId.get)
  override def `type`: String = mType.get

  override def from: TransferAccount = new TransferAccount {
    override def bank_id: String = mFrom_AccountId.get
    override def account_id: String = mFrom_BankId.get
  }

  override def body: TransferBody = new TransferBody {
    override def to: TransferAccount = new TransferAccount {
      override def bank_id: String = mBody_To_BankId.get
      override def account_id: String = mBody_To_AccountId.get
    }
    override def value: AmountOfMoney = new AmountOfMoney {
      override def currency: String = mBody_Value_Currency.get
      override def amount: String = mBody_Value_Amount.get
    }
    override def description: String = mBody_Description.get
  }

  override def transaction_ids: String = mTransactionIDs.get
  override def status: String = mStatus.get
  override def start_date: Date = mStartDate.get
  override def end_date: Date = mEndDate.get
  override def challenge = new TransferChallenge {
    override def id: String = mChallenge_Id.get
    override def allowed_attempts: Int = mChallenge_AllowedAttempts.get
    override def challenge_type: String = mChallenge_ChallengeType.get
  }

  def toTransfer : Option[Transfer] = {

    val t_amount = new AmountOfMoney {
      override def currency: String = mBody_Value_Currency.get
      override def amount: String = mBody_Value_Amount.get
    }
    val t_to = new TransferAccount {
      override def bank_id: String = mBody_To_BankId.get
      override def account_id: String = mBody_To_AccountId.get
    }
    val t_body = new TransferBody {
      override def to: TransferAccount = t_to
      override def description: String = mBody_Description.get
      override def value: AmountOfMoney = t_amount
    }
    val t_from = new TransferAccount {
      override def bank_id: String = mFrom_BankId.get
      override def account_id: String = mFrom_AccountId.get
    }

    val t_challenge = new TransferChallenge {
      override def challenge_type: String = mChallenge_ChallengeType
      override def id: String = mChallenge_Id
      override def allowed_attempts: Int = mChallenge_AllowedAttempts
    }

    Some(
      new Transfer {
        override def transferId: TransferId = TransferId(mTransferId.get)
        override def `type`: String = mType.get
        override def from: TransferAccount = t_from
        override def body: TransferBody = t_body
        override def status: String = mStatus.get
        override def end_date: Date = mEndDate.get
        override def transaction_ids: String = mTransactionIDs.get
        override def start_date: Date = mStartDate.get
        override def challenge: TransferChallenge = t_challenge
     }
    )
  }
}

object MappedTransfer extends MappedTransfer with LongKeyedMetaMapper[MappedTransfer] {
  override def dbIndexes = UniqueIndex(mTransferId) :: super.dbIndexes
}