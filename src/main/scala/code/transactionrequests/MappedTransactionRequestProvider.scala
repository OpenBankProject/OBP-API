package code.transactionrequests

import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.bankconnectors.Connector
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.transactionrequests.TransactionRequests._
import code.util.DefaultStringField
import net.liftweb.common.{Box, Failure, Full, Logger}
import net.liftweb.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers._

object MappedTransactionRequestProvider extends TransactionRequestProvider {

  private val logger = Logger(classOf[TransactionRequestProvider])

  override def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest] =
    MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))

  override def getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId): Box[TransactionRequest] =
    MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value)).flatMap(_.toTransactionRequest)

  override def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]] = {
    Full(MappedTransactionRequest.findAll(By(MappedTransactionRequest.mFrom_BankId, bankId.value), By(MappedTransactionRequest.mFrom_AccountId, accountId.value)).flatMap(_.toTransactionRequest))
  }

  override def updateAllPendingTransactionRequests: Box[Option[Unit]] = {
    val transactionRequests = MappedTransactionRequest.find(By(MappedTransactionRequest.mStatus, TransactionRequests.STATUS_PENDING))
    logger.debug("Updating status of all pending transactions: ")
    val statuses = Connector.connector.vend.getTransactionRequestStatuses
    transactionRequests.map{ tr =>
      for {
        transactionRequest <- tr.toTransactionRequest
        if (statuses.exists(_ == transactionRequest.id -> "APVD"))
      } yield {
        tr.updateStatus(TransactionRequests.STATUS_COMPLETED)
        logger.debug(s"updated ${transactionRequest.id} status: ${TransactionRequests.STATUS_COMPLETED}")
      }
    }
  }

  override def bulkDeleteTransactionRequests(): Boolean = {
    MappedTransactionRequest.bulkDelete_!!()
  }

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                            transactionRequestType: TransactionRequestType,
                                            account : BankAccount,
                                            counterparty : BankAccount,
                                            body: TransactionRequestBody,
                                            status: String,
                                            charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    val mappedTransactionRequest = MappedTransactionRequest.create
      .mTransactionRequestId(transactionRequestId.value)
      .mType(transactionRequestType.value)
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mTo_BankId(counterparty.bankId.value)
      .mTo_AccountId(counterparty.accountId.value)
      .mBody_Value_Currency(body.value.currency)
      .mBody_Value_Amount(body.value.amount)
      .mBody_Description(body.description)
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now).saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                               transactionRequestType: TransactionRequestType,
                                               fromAccount: BankAccount,
                                               toAccount: BankAccount,
                                               toCounterparty: CounterpartyTrait,
                                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                               details: String,
                                               status: String,
                                               charge: TransactionRequestCharge,
                                               chargePolicy: String): Box[TransactionRequest] = {

    // Note: We don't save transaction_ids, status and challenge here.
    val mappedTransactionRequest = MappedTransactionRequest.create

      //transaction request fields:
      .mTransactionRequestId(transactionRequestId.value)
      .mType(transactionRequestType.value)
      //transaction fields:
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now)
      .mCharge_Summary(charge.summary)
      .mCharge_Amount(charge.value.amount)
      .mCharge_Currency(charge.value.currency)
      .mcharge_Policy(chargePolicy)

      //fromAccount fields
      .mFrom_BankId(fromAccount.bankId.value)
      .mFrom_AccountId(fromAccount.accountId.value)

      //toAccount fields
      .mTo_BankId(toAccount.bankId.value)
      .mTo_AccountId(toAccount.accountId.value)

      //toCounterparty fields
      .mName(toCounterparty.name)
      .mThisBankId(toCounterparty.thisBankId)
      .mThisAccountId(toCounterparty.thisAccountId)
      .mThisViewId(toCounterparty.thisViewId)
      .mCounterpartyId(toCounterparty.counterpartyId)
      .mOtherAccountRoutingScheme(toCounterparty.otherAccountRoutingScheme)
      .mOtherAccountRoutingAddress(toCounterparty.otherAccountRoutingAddress)
      .mOtherBankRoutingScheme(toCounterparty.otherBankRoutingScheme)
      .mOtherBankRoutingAddress(toCounterparty.otherBankRoutingAddress)
      .mIsBeneficiary(toCounterparty.isBeneficiary)

      //Body from http request: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY should have the same following fields:
      .mBody_Value_Currency(transactionRequestCommonBody.value.currency)
      .mBody_Value_Amount(transactionRequestCommonBody.value.amount)
      .mBody_Description(transactionRequestCommonBody.description)
      .mDetails(details) // This is the details / body of the request (contains all fields in the body)


      .saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    // This saves transaction_ids
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full(tr.mTransactionIDs(transactionId.value).save)
      case _ => Failure("Couldn't find transaction request ${transactionRequestId}")
    }
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    //this saves challenge
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full{
        tr.mChallenge_Id(challenge.id)
        tr.mChallenge_AllowedAttempts(challenge.allowed_attempts)
        tr.mChallenge_ChallengeType(challenge.challenge_type).save
      }
      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set transactionId")
    }
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    //this saves status
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full(tr.mStatus(status).save)
      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set status")
    }
  }

}

class MappedTransactionRequest extends LongKeyedMapper[MappedTransactionRequest] with IdPK with CreatedUpdated {

  private val logger = Logger(classOf[MappedTransactionRequest])

  override def getSingleton = MappedTransactionRequest

  //transaction request fields:
  object mTransactionRequestId extends DefaultStringField(this)
  object mType extends DefaultStringField(this)

  //transaction fields:
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
  object mcharge_Policy  extends DefaultStringField(this)

  //Body from http request: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY should have the same following fields:
  object mBody_Value_Currency extends DefaultStringField(this)
  object mBody_Value_Amount extends DefaultStringField(this)
  object mBody_Description extends DefaultStringField(this)
  // This is the details / body of the request (contains all fields in the body)
  // Note:this need to be a longer string, defaults is 2000, maybe not enough
  object mDetails extends DefaultStringField(this)

  //fromAccount fields
  object mFrom_BankId extends DefaultStringField(this)
  object mFrom_AccountId extends DefaultStringField(this)

  //toAccount fields
  object mTo_BankId extends DefaultStringField(this)
  object mTo_AccountId extends DefaultStringField(this)

  //toCounterparty fields
  object mName extends DefaultStringField(this)
  object mThisBankId extends DefaultStringField(this)
  object mThisAccountId extends DefaultStringField(this)
  object mThisViewId extends DefaultStringField(this)
  object mCounterpartyId extends DefaultStringField(this)
  object mOtherAccountRoutingScheme extends DefaultStringField(this)
  object mOtherAccountRoutingAddress extends DefaultStringField(this)
  object mOtherBankRoutingScheme extends DefaultStringField(this)
  object mOtherBankRoutingAddress extends DefaultStringField(this)
  object mIsBeneficiary extends MappedBoolean(this)

  def updateStatus(newStatus: String) = {
    mStatus.set(newStatus)
  }

  def toTransactionRequest : Option[TransactionRequest] = {
    val t_amount = AmountOfMoney (
      currency = mBody_Value_Currency.get,
      amount = mBody_Value_Amount.get
    )
    val t_to = TransactionRequestAccount (
      bank_id = mTo_BankId.get,
      account_id = mTo_AccountId.get
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


    val details = mDetails.get

    val parsedDetails = json.parse(details)


    Some(
      TransactionRequest(
        id = TransactionRequestId(mTransactionRequestId.get),
        `type`= mType.get,
        from = t_from,
        details = parsedDetails,
        body = t_body,
        status = mStatus.get,
        transaction_ids = mTransactionIDs.get,
        start_date = mStartDate.get,
        end_date = mEndDate.get,
        challenge = t_challenge,
        charge = t_charge,
        charge_policy =mcharge_Policy,
        counterparty_id =  CounterpartyId(mCounterpartyId.get),
        name = mName.get,
        this_bank_id = BankId(mThisBankId.get),
        this_account_id = AccountId(mThisAccountId.get),
        this_view_id = ViewId(mThisViewId.get),
        other_account_routing_scheme = mOtherAccountRoutingScheme.get,
        other_account_routing_address = mOtherAccountRoutingAddress.get,
        other_bank_routing_scheme = mOtherBankRoutingScheme.get,
        other_bank_routing_address = mOtherBankRoutingAddress.get,
        is_beneficiary = mIsBeneficiary.get
      )
    )
  }
}

object MappedTransactionRequest extends MappedTransactionRequest with LongKeyedMetaMapper[MappedTransactionRequest] {
  override def dbIndexes = UniqueIndex(mTransactionRequestId) :: super.dbIndexes
}
