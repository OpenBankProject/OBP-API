package code.transactionrequests

import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages._
import code.bankconnectors.Connector
import code.model._
import code.transactionrequests.TransactionRequests.{TransactionRequestTypes, _}
import code.util.{AccountIdString, UUIDString}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Failure, Full, Logger}
import net.liftweb.json
import net.liftweb.json.JsonAST.{JField, JObject, JString}
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
    val transactionRequests = MappedTransactionRequest.find(By(MappedTransactionRequest.mStatus, TransactionRequestStatus.PENDING.toString))
    logger.debug("Updating status of all pending transactions: ")
    val statuses = Connector.connector.vend.getTransactionRequestStatuses
    transactionRequests.map{ tr =>
      for {
        transactionRequest <- tr.toTransactionRequest
        if (statuses.exists(i => i.transactionRequestId -> i.bulkTransactionsStatus == transactionRequest.id -> List("APVD")))
      } yield {
        tr.updateStatus(TransactionRequestStatus.COMPLETED.toString)
        logger.debug(s"updated ${transactionRequest.id} status: ${TransactionRequestStatus.COMPLETED}")
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
      .mName(toAccount.name)
      .mOtherAccountRoutingScheme(toAccount.accountRoutingScheme)
      .mOtherAccountRoutingAddress(toAccount.accountRoutingAddress)
      .mOtherBankRoutingScheme(toAccount.bankRoutingScheme)
      .mOtherBankRoutingAddress(toAccount.bankRoutingAddress)
      // We need transfer CounterpartyTrait to BankAccount, so We lost some data. can not fill the following fields .
      //.mThisBankId(toAccount.bankId.value) 
      //.mThisAccountId(toAccount.accountId.value)
      //.mThisViewId(toAccount.v) 
      //.mCounterpartyId(toAccount.branchId)
      //.mIsBeneficiary(toAccount.isBeneficiary)

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
      case _ => Failure(s"$SaveTransactionRequestTransactionException Couldn't find transaction request ${transactionRequestId}")
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
      case _ => Failure(s"$SaveTransactionRequestChallengeException Couldn't find transaction request ${transactionRequestId} to set transactionId")
    }
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    //this saves status
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full(tr.mStatus(status).save)
      case _ => Failure(s"$SaveTransactionRequestStatusException Couldn't find transaction request ${transactionRequestId} to set status")
    }
  }

}

class MappedTransactionRequest extends LongKeyedMapper[MappedTransactionRequest] with IdPK with CreatedUpdated with CustomJsonFormats {

  private val logger = Logger(classOf[MappedTransactionRequest])

  override def getSingleton = MappedTransactionRequest

  //transaction request fields:
  object mTransactionRequestId extends UUIDString(this)
  object mType extends MappedString(this, 32)

  //transaction fields:
  object mTransactionIDs extends MappedString(this, 2000)
  object mStatus extends MappedString(this, 32)
  object mStartDate extends MappedDate(this)
  object mEndDate extends MappedDate(this)
  object mChallenge_Id extends MappedString(this, 64)
  object mChallenge_AllowedAttempts extends MappedInt(this)
  object mChallenge_ChallengeType extends MappedString(this, 32)
  object mCharge_Summary  extends MappedString(this, 64)
  object mCharge_Amount  extends MappedString(this, 32)
  object mCharge_Currency  extends MappedString(this, 3)
  object mcharge_Policy  extends MappedString(this, 32)

  //Body from http request: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY should have the same following fields:
  object mBody_Value_Currency extends MappedString(this, 3)
  object mBody_Value_Amount extends MappedString(this, 32)
  object mBody_Description extends MappedString(this, 2000)
  // This is the details / body of the request (contains all fields in the body)
  // Note:this need to be a longer string, defaults is 2000, maybe not enough
  object mDetails extends MappedString(this, 2000)

  //fromAccount fields
  object mFrom_BankId extends UUIDString(this)
  object mFrom_AccountId extends AccountIdString(this)

  //toAccount fields
  @deprecated("use mOtherBankRoutingAddress instead","2017-12-25")
  object mTo_BankId extends UUIDString(this)
  @deprecated("use mOtherAccountRoutingAddress instead","2017-12-25")
  object mTo_AccountId extends AccountIdString(this)

  //toCounterparty fields
  object mName extends MappedString(this, 64)
  object mThisBankId extends UUIDString(this)
  object mThisAccountId extends AccountIdString(this)
  object mThisViewId extends UUIDString(this)
  object mCounterpartyId extends UUIDString(this)
  object mOtherAccountRoutingScheme extends MappedString(this, 32) // TODO Add class for Scheme and Address
  object mOtherAccountRoutingAddress extends MappedString(this, 64)
  object mOtherBankRoutingScheme extends MappedString(this, 32)
  object mOtherBankRoutingAddress extends MappedString(this, 64)
  object mIsBeneficiary extends MappedBoolean(this)

  def updateStatus(newStatus: String) = {
    mStatus.set(newStatus)
  }

  def toTransactionRequest : Option[TransactionRequest] = {
  
    val details = mDetails.get
  
    val parsedDetails = json.parse(details)
  
    val transactionType = mType.get
    
    val t_amount = AmountOfMoney (
      currency = mBody_Value_Currency.get,
      amount = mBody_Value_Amount.get
    )
    
    val t_to_sandbox_tan = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SANDBOX_TAN)
      Some(TransactionRequestAccount (bank_id = mTo_BankId.get, account_id = mTo_AccountId.get))
    else
      None
  
    val t_to_sepa = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SEPA){
      val ibanList: List[String] = for {
        JObject(child) <- parsedDetails
        JField("iban", JString(iban)) <- child
      } yield
        iban
      val ibanValue = if (ibanList.isEmpty) "" else ibanList.head      
      Some(TransactionRequestIban(iban = ibanValue))
    }
    else
      None
    
    val t_to_counterparty = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.COUNTERPARTY){
      val counterpartyIdList: List[String] = for {
        JObject(child) <- parsedDetails
        JField("counterparty_id", JString(counterpartyId)) <- child
      } yield
        counterpartyId
      val counterpartyIdValue = if (counterpartyIdList.isEmpty) "" else counterpartyIdList.head
      Some(TransactionRequestCounterpartyId (counterparty_id = counterpartyIdValue.toString))
    }
    else
      None

    val t_to_transfer_to_phone = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_PHONE) 
      Some(parsedDetails.extract[TransactionRequestTransferToPhone])
    else
      None

    val t_to_transfer_to_atm = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_ATM) 
      Some(parsedDetails.extract[TransactionRequestTransferToAtm])
    else
      None
    
    val t_to_transfer_to_account = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_ACCOUNT)
      Some(parsedDetails.extract[TransactionRequestTransferToAccount])
    else
      None
    
    val t_body = TransactionRequestBodyAllTypes(
      to_sandbox_tan = t_to_sandbox_tan,
      to_sepa = t_to_sepa,
      to_counterparty = t_to_counterparty,
      to_transfer_to_phone = t_to_transfer_to_phone, 
      to_transfer_to_atm = t_to_transfer_to_atm,
      to_transfer_to_account = t_to_transfer_to_account,
      value = t_amount,
      description = mBody_Description.get
    )
    val t_from = TransactionRequestAccount (
      bank_id = mFrom_BankId.get,
      account_id = mFrom_AccountId.get
    )

    val t_challenge = TransactionRequestChallenge (
      id = mChallenge_Id.get,
      allowed_attempts = mChallenge_AllowedAttempts.get,
      challenge_type = mChallenge_ChallengeType.get
    )

    val t_charge = TransactionRequestCharge (
    summary = mCharge_Summary.get,
    value = AmountOfMoney(currency = mCharge_Currency.get, amount = mCharge_Amount.get)
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
        charge = t_charge,
        charge_policy =mcharge_Policy.get,
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
