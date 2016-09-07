package code.transactionrequests

import code.model._
import code.transactionrequests.TransactionRequests._

import code.util.DefaultStringField
import net.liftweb.common.Logger
import net.liftweb.json
import net.liftweb.mapper._
import java.util.Date

object MappedTransactionRequestProvider extends TransactionRequestProvider {

  override protected def getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId): Option[TransactionRequest] =
    MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value)).flatMap(_.toTransactionRequest)

  override protected def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId, viewId: ViewId): Some[List[TransactionRequest]] = {
    Some(MappedTransactionRequest.findAll(By(MappedTransactionRequest.mBody_To_BankId, bankId.value), By(MappedTransactionRequest.mBody_To_AccountId, accountId.value)).flatMap(_.toTransactionRequest))
  }
}

class MappedTransactionRequest extends LongKeyedMapper[MappedTransactionRequest] with IdPK with CreatedUpdated {

  private val logger = Logger(classOf[MappedTransactionRequest])

  override def getSingleton = MappedTransactionRequest

  object mTransactionRequestId extends DefaultStringField(this)
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

  object mCharge_Summary  extends DefaultStringField(this)
  object mCharge_Amount  extends DefaultStringField(this)
  object mCharge_Currency  extends DefaultStringField(this)
 
  def toTransactionRequest : Option[TransactionRequest] = {
    val t_amount = AmountOfMoney (
      currency = mBody_Value_Currency.get,
      amount = mBody_Value_Amount.get
    )
    val t_to = TransactionRequestAccount (
      bank_id = mBody_To_BankId.get,
      account_id = mBody_To_AccountId.get
    )
    val t_body = TransactionRequestBody (
      to = t_to,
      value = t_amount,
      description = mBody_Description.get
    )
    val t_from = TransactionRequestAccount (
      bank_id = mFrom_BankId.get,
      account_id = mFrom_AccountId.get
    )

    val t_challenge = TransactionRequestChallenge (
      id = mChallenge_Id,
      allowed_attempts = mChallenge_AllowedAttempts,
      challenge_type = mChallenge_ChallengeType
    )

    val t_charge = TransactionRequestCharge (
    summary = mCharge_Summary,
    value = AmountOfMoney(currency = mCharge_Currency, amount = mCharge_Amount)
    )

    Some(
      TransactionRequest(
        id = TransactionRequestId(mTransactionRequestId.get),
        `type`= mType.get,
        from = t_from,
        body = t_body,
        status = mStatus.get,
        transaction_ids = mTransactionIDs.get,
        start_date = mStartDate.get,
        end_date = mEndDate.get,
        challenge = t_challenge,
        charge = t_charge
      )
    )
  }
}

object MappedTransactionRequest extends MappedTransactionRequest with LongKeyedMetaMapper[MappedTransactionRequest] {
  override def dbIndexes = UniqueIndex(mTransactionRequestId) :: super.dbIndexes
}

class MappedTransactionRequest210 extends LongKeyedMapper[MappedTransactionRequest210] with IdPK with CreatedUpdated {

  private val logger = Logger(classOf[MappedTransactionRequest210])

  override def getSingleton = MappedTransactionRequest210

  object mTransactionRequestId extends DefaultStringField(this)
  object mType extends DefaultStringField(this)
  object mFrom_BankId extends DefaultStringField(this)
  object mFrom_AccountId extends DefaultStringField(this)

  //details fields
  object mDetails extends DefaultStringField(this)

  object mTransactionIDs extends DefaultStringField(this)
  object mStatus extends DefaultStringField(this)
  object mStartDate extends MappedDate(this)
  object mEndDate extends MappedDate(this)
  object mChallenge_Id extends DefaultStringField(this)
  object mChallenge_AllowedAttempts extends MappedInt(this)
  object mChallenge_ChallengeType extends DefaultStringField(this)

  object mCharge_Summary  extends DefaultStringField(this)
  object mCharge_Amount  extends DefaultStringField(this)
  object mCharge_Currency  extends DefaultStringField(this)

  def toTransactionRequest210 : Option[TransactionRequest210] = {
    val t_from = TransactionRequestAccount (
      bank_id = mFrom_BankId.get,
      account_id = mFrom_AccountId.get
    )

    val t_challenge = TransactionRequestChallenge (
      id = mChallenge_Id,
      allowed_attempts = mChallenge_AllowedAttempts,
      challenge_type = mChallenge_ChallengeType
    )

    val t_charge = TransactionRequestCharge (
      summary = mCharge_Summary,
      value = AmountOfMoney(currency = mCharge_Currency, amount = mCharge_Amount)
    )

    Some(
      TransactionRequest210(
        id = TransactionRequestId(mTransactionRequestId.get),
        `type`= mType.get,
        from = t_from,
        details = mDetails.get,
        status = mStatus.get,
        transaction_ids = mTransactionIDs.get,
        start_date = mStartDate.get,
        end_date = mEndDate.get,
        challenge = t_challenge,
        charge = t_charge
      )
    )
  }
}

object MappedTransactionRequest210 extends MappedTransactionRequest210 with LongKeyedMetaMapper[MappedTransactionRequest210] {
  override def dbIndexes = UniqueIndex(mTransactionRequestId) :: super.dbIndexes
}