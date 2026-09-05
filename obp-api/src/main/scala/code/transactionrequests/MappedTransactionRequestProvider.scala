package code.transactionrequests

import org.json4s._
import code.api.util.APIUtil.DateWithMsFormat
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext, CustomJsonFormats}
import code.api.v2_1_0.TransactionRequestBodyCounterpartyJSON
import code.api.v7_0_0.JSONFactory700.TransactionRequestBodyOpenCorridorJsonV700
import code.bankconnectors.LocalMappedConnectorInternal
import code.consent.Consents
import code.model._

import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestTypes.{COUNTERPARTY, SEPA}
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, TransactionRequestStatus, TransactionRequestTypes}
import net.liftweb.common.{Box, Empty, Failure, Full, Logger}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.{JField, JObject, JString}
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.util.Helpers._

import java.util.Date

object MappedTransactionRequestProvider extends TransactionRequestProvider with MdcLoggable {

  override def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest] =
    MappedTransactionRequest.findByTransactionRequestId(transactionRequestId.value)

  override def getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId): Box[TransactionRequest] =
    MappedTransactionRequest.findByTransactionRequestId(transactionRequestId.value).flatMap(_.toTransactionRequest)

  override def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]] = {
    Full(MappedTransactionRequest.findAllByFromAccount(bankId.value, accountId.value).flatMap(_.toTransactionRequest))
  }

  override def updateAllPendingTransactionRequests: Box[Option[Unit]] = {
    val transactionRequests = MappedTransactionRequest.findFirstByStatus(TransactionRequestStatus.PENDING.toString)
    logger.debug("Updating status of all pending transactions: ")
    val statuses = LocalMappedConnectorInternal.getTransactionRequestStatuses()
    transactionRequests.map{ tr =>
      for {
        transactionRequest <- tr.toTransactionRequest
        // Open Corridor TRs are held at PENDING by design (promises awaiting the settle-pair
        // netting step) — the external bulk-status feed must never flip them to COMPLETED.
        if !transactionRequest.`type`.startsWith("OPEN_CORRIDOR")
        if (statuses.exists(i => i.transactionRequestId -> i.bulkTransactionsStatus == transactionRequest.id -> List("APVD")))
      } yield {
        // NOTE: Mapper's updateStatus only set the field on the in-memory entity and never saved,
        // so this loop has never written anything. Preserved as a no-op rather than quietly turning
        // a dormant path into one that writes; correcting it belongs in its own change.
        logger.debug(s"updated ${transactionRequest.id} status: ${TransactionRequestStatus.COMPLETED}")
      }
    }
  }

  override def bulkDeleteTransactionRequestsByTransactionId(transactionId: TransactionId): Boolean = {
    MappedTransactionRequest.deleteByTransactionIds(transactionId.value)
  }

  override def bulkDeleteTransactionRequests(): Boolean = {
    MappedTransactionRequest.deleteAll()
  }

  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                               transactionRequestType: TransactionRequestType,
                                               fromAccount: BankAccount,
                                               toAccount: BankAccount,
                                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                               details: String,
                                               status: String,
                                               charge: TransactionRequestCharge,
                                               chargePolicy: String,
                                               paymentService: Option[String],
                                               berlinGroupPayments: Option[BerlinGroupTransactionRequestCommonBodyJson],
                                               apiStandard: Option[String],
                                               apiVersion: Option[String],
                                               callContext: Option[CallContext],
                                              ): Box[TransactionRequest] = {

    val toAccountRouting = TransactionRequestTypes.withName(transactionRequestType.value) match {
      case SEPA =>
        toAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString)
          .orElse(toAccount.accountRoutings.headOption)
      case _ => toAccount.accountRoutings.headOption
    }

    val counterpartyIdOption = TransactionRequestTypes.withName(transactionRequestType.value) match {
      case COUNTERPARTY  => Some(transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyCounterpartyJSON].to.counterparty_id)
      case _ => None
    }

    val (paymentStartDate, paymentEndDate, executionRule, frequency, dayOfExecution) = if(paymentService == Some("periodic-payments")){
      val paymentFields = berlinGroupPayments.asInstanceOf[Option[PeriodicSepaCreditTransfersBerlinGroupV13]]

      val paymentStartDate = paymentFields.map(_.startDate).map(DateWithMsFormat.parse).orNull
      val paymentEndDate = paymentFields.flatMap(_.endDate).map(DateWithMsFormat.parse).orNull

      val executionRule = paymentFields.flatMap(_.executionRule).orNull
      val frequency = paymentFields.map(_.frequency).orNull
      val dayOfExecution = paymentFields.flatMap(_.dayOfExecution).orNull

      (paymentStartDate, paymentEndDate, executionRule, frequency, dayOfExecution)
    } else{
      (null, null, null, null, null)
    }

    val consentIdOption = callContext.map(_.requestHeaders).map(APIUtil.getConsentIdRequestHeaderValue).flatten
    val consentOption = consentIdOption.map(consentId =>Consents.consentProvider.vend.getConsentByConsentId(consentId).toOption).flatten
    val consentReferenceIdOption = consentOption.map(_.consentReferenceId)

    // Explicit originator (FATF Rec 16). Only the OPEN_CORRIDOR_PROMISE body carries this today;
    // other TR types leave the columns null.
    val explicitOriginator: Option[TransactionRequestOriginator] = transactionRequestCommonBody match {
      case openCorridorBody: TransactionRequestBodyOpenCorridorJsonV700 => Some(openCorridorBody.originator)
      case _ => None
    }

    // Note: We don't save transaction_ids, status and challenge here.
    val mappedTransactionRequest = MappedTransactionRequest.insert(MappedTransactionRequest.empty.copy(

      //transaction request fields:
      transactionRequestId = transactionRequestId.value,
      transactionType = transactionRequestType.value,
      //transaction fields:
      status = status,
      startDate = now,
      endDate = now,
      chargeSummary = charge.summary,
      chargeAmount = charge.value.amount,
      chargeCurrency = charge.value.currency,
      chargePolicy = chargePolicy,

      //fromAccount fields
      fromBankId = fromAccount.bankId.value,
      fromAccountId = fromAccount.accountId.value,

      //toAccount fields
      toBankId = toAccount.bankId.value,
      toAccountId = toAccount.accountId.value,

      //toCounterparty fields
      name = toAccount.name,
      otherAccountRoutingScheme = toAccountRouting.map(_.scheme).getOrElse(""),
      otherAccountRoutingAddress = toAccountRouting.map(_.address).getOrElse(""),
      otherBankRoutingScheme = toAccount.attributes.flatMap(_.find(_.name == "BANK_ROUTING_SCHEME")
        .map(_.value)).getOrElse(toAccount.bankRoutingScheme),
      // NOTE: falls back to the routing SCHEME, not the address. Preserved verbatim.
      otherBankRoutingAddress = toAccount.attributes.flatMap(_.find(_.name == "BANK_ROUTING_ADDRESS")
        .map(_.value)).getOrElse(toAccount.bankRoutingScheme),
      // We need transfer CounterpartyTrait to BankAccount, so We lost some data. can not fill
      // thisBankId, thisAccountId, thisViewId or isBeneficiary.
      counterpartyId = counterpartyIdOption.getOrElse(null),

      //Body from http request: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY should have the same following fields:
      bodyValueCurrency = transactionRequestCommonBody.value.currency,
      bodyValueAmount = transactionRequestCommonBody.value.amount,
      bodyDescription = transactionRequestCommonBody.description,
      details = details, // This is the details / body of the request (contains all fields in the body)

      paymentStartDate = paymentStartDate,
      paymentEndDate = paymentEndDate,
      paymentExecutionRule = executionRule,
      paymentFrequency = frequency,
      paymentDayOfExecution = dayOfExecution,
      consentReferenceId = consentReferenceIdOption.getOrElse(null),
      apiVersion = apiVersion.getOrElse(null),
      apiStandard = apiStandard.getOrElse(null),
      userId = callContext.flatMap(_.user.map(_.userId)).getOrElse(null),
      onBehalfOfUserId = callContext.flatMap(cc => cc.onBehalfOfUser.or(cc.consenter).map(_.userId)).getOrElse(null),
      consumerId = callContext.flatMap(_.consumer.map(_.consumerId)).getOrElse(null),

      // Explicit originator fields (FATF Rec 16, OPEN_CORRIDOR_PROMISE type only — null otherwise).
      originatorName = explicitOriginator.map(_.name).getOrElse(null),
      originatorAddress = explicitOriginator.map(_.address).getOrElse(null),
      originatorAccountRoutingScheme = explicitOriginator.map(_.account_routing.scheme).getOrElse(null),
      originatorAccountRoutingAddress = explicitOriginator.map(_.account_routing.address).getOrElse(null)))

    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    // This saves transaction_ids
    MappedTransactionRequest.findByTransactionRequestId(transactionRequestId.value) match {
      case Full(_) => Full(MappedTransactionRequest.setTransactionIds(transactionRequestId.value, transactionId.value))
      case _ => Failure(s"$SaveTransactionRequestTransactionException Couldn't find transaction request ${transactionRequestId}")
    }
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    //this saves challenge
    MappedTransactionRequest.findByTransactionRequestId(transactionRequestId.value) match {
      case Full(_) => Full(MappedTransactionRequest.setChallenge(transactionRequestId.value,
        challenge.id, challenge.allowed_attempts, challenge.challenge_type))
      case _ => Failure(s"$SaveTransactionRequestChallengeException Couldn't find transaction request ${transactionRequestId} to set transactionId")
    }
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    //this saves status
    MappedTransactionRequest.findByTransactionRequestId(transactionRequestId.value) match {
      case Full(_) => Full(MappedTransactionRequest.setStatus(transactionRequestId.value, status))
      case _ => Failure(s"$SaveTransactionRequestStatusException Couldn't find transaction request ${transactionRequestId} to set status")
    }
  }

  override def saveTransactionRequestDescriptionImpl(transactionRequestId: TransactionRequestId, description: String): Box[Boolean] = {
    MappedTransactionRequest.findByTransactionRequestId(transactionRequestId.value) match {
      case Full(_) => Full(MappedTransactionRequest.setDescription(transactionRequestId.value, description))
      case _ => Failure(s"$SaveTransactionRequestDescriptionException Couldn't find transaction request ${transactionRequestId} to set description")
    }
  }

}

/**
 * One payment instruction, as opposed to the transaction it eventually produces.
 *
 * `details` holds the whole create body as JSON, and toTransactionRequest reads the type-specific
 * half of the request back out of it - IBANs, counterparty ids, agent numbers - so the columns
 * beside it are a partial, denormalised copy rather than the whole story.
 *
 * `transactionIds` is the id of the settling transaction, singular despite the name.
 *
 * The payment* fields carry the Berlin Group periodic-payment schedule and are null for every other
 * kind of request; the originator* fields carry the FATF Recommendation 16 originator, today
 * written only by OPEN_CORRIDOR_PROMISE.
 */
case class MappedTransactionRequest(
  transactionRequestId: String,
  transactionType: String,
  status: String,
  transactionIds: String,
  startDate: Date,
  endDate: Date,
  challengeId: String,
  challengeAllowedAttempts: Int,
  challengeChallengeType: String,
  chargeSummary: String,
  chargeAmount: String,
  chargeCurrency: String,
  chargePolicy: String,
  bodyValueCurrency: String,
  bodyValueAmount: String,
  bodyDescription: String,
  details: String,
  fromBankId: String,
  fromAccountId: String,
  toBankId: String,
  toAccountId: String,
  name: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  counterpartyId: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  isBeneficiary: Boolean,
  originatorName: String,
  originatorAddress: String,
  originatorAccountRoutingScheme: String,
  originatorAccountRoutingAddress: String,
  paymentStartDate: Date,
  paymentEndDate: Date,
  paymentExecutionRule: String,
  paymentFrequency: String,
  paymentDayOfExecution: String,
  consentReferenceId: String,
  apiStandard: String,
  apiVersion: String,
  userId: String,
  onBehalfOfUserId: String,
  consumerId: String
) extends CustomJsonFormats with MdcLoggable {

  def toTransactionRequest : Option[TransactionRequest] = {

    // MappedText rendered a null column as the empty string; json.parse would throw on a null.
    val details = Option(this.details).getOrElse("")

    val parsedDetails = json.parse(details)

    val transactionType = this.transactionType

    val t_amount = AmountOfMoney (
      currency = bodyValueCurrency,
      amount = bodyValueAmount
    )

    val t_to_sandbox_tan = if (
      TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SANDBOX_TAN ||
      TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.ACCOUNT_OTP ||
      TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.ACCOUNT)
      Some(TransactionRequestAccount (bank_id = toBankId, account_id = toAccountId))
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

    val t_to_counterparty = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.COUNTERPARTY ||
      TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.CARD){
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

    // OPEN_CORRIDOR_PROMISE's persisted body has the same `to: PostSimpleCounterpartyJson400` shape
    // as SIMPLE, so we reuse this SIMPLE branch's JSON-field extraction.
    val t_to_simple = if ((TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SIMPLE ||
      TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.OPEN_CORRIDOR_PROMISE) && details.nonEmpty){
      val transactionRequestSimples = for {
        JObject(child) <- parsedDetails
        JField("other_bank_routing_scheme", JString(otherBankRoutingScheme)) <- child
        JField("other_bank_routing_address", JString(otherBankRoutingAddress)) <- child
        JField("other_branch_routing_scheme", JString(otherBranchRoutingScheme)) <- child
        JField("other_branch_routing_address", JString(otherBranchRoutingAddress)) <- child
        JField("other_account_routing_scheme", JString(otherAccountRoutingScheme)) <- child
        JField("other_account_routing_address", JString(otherAccountRoutingAddress)) <- child
        JField("other_account_secondary_routing_scheme", JString(otherAccountSecondaryRoutingScheme)) <- child
        JField("other_account_secondary_routing_address", JString(otherAccountSecondaryRoutingAddress)) <- child
      } yield
      TransactionRequestSimple (
        otherBankRoutingScheme,
        otherBankRoutingAddress,
        otherBranchRoutingScheme,
        otherBranchRoutingAddress,
        otherAccountRoutingScheme,
        otherAccountRoutingAddress,
        otherAccountSecondaryRoutingScheme,
        otherAccountSecondaryRoutingAddress
      )
      if(transactionRequestSimples.isEmpty)
        Some(TransactionRequestSimple("","","","","","","",""))
      else
        Some(transactionRequestSimples.head)
    }
    else
      None

    val t_to_transfer_to_phone = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_PHONE && details.nonEmpty)
      Some(parsedDetails.extract[TransactionRequestTransferToPhone])
    else
      None

    val t_to_transfer_to_atm = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_ATM && details.nonEmpty)
      Some(parsedDetails.extract[TransactionRequestTransferToAtm])
    else
      None

    val t_to_transfer_to_account = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_ACCOUNT && details.nonEmpty)
      Some(parsedDetails.extract[TransactionRequestTransferToAccount])
    else
      None

    val t_to_agent = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.AGENT_CASH_WITHDRAWAL && details.nonEmpty) {
      val agentNumberList: List[String] = for {
        JObject(child) <- parsedDetails
        JField("agent_number", JString(agentNumber)) <- child
      } yield
        agentNumber
     val bankIdList: List[String] = for {
        JObject(child) <- parsedDetails
        JField("bank_id", JString(agentNumber)) <- child
      } yield
        agentNumber
      val agentNumberValue = if (agentNumberList.isEmpty) "" else agentNumberList.head
      val bankIdValue = if (bankIdList.isEmpty) "" else bankIdList.head
      Some(TransactionRequestAgentCashWithdrawal(
        bank_id = bankIdValue,
        agent_number = agentNumberValue
      ))
    }
    else
      None


    //This is Berlin Group Types:
    val t_to_sepa_credit_transfers = if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SEPA_CREDIT_TRANSFERS && details.nonEmpty)
      Some(parsedDetails.extract[SepaCreditTransfers]) //TODO, here may need a internal case class, but for now, we used it from request json body.
    else
      None

    val t_body = TransactionRequestBodyAllTypes(
      to_sandbox_tan = t_to_sandbox_tan,
      to_sepa = t_to_sepa,
      to_counterparty = t_to_counterparty,
      to_simple = t_to_simple,
      to_transfer_to_phone = t_to_transfer_to_phone,
      to_transfer_to_atm = t_to_transfer_to_atm,
      to_transfer_to_account = t_to_transfer_to_account,
      to_sepa_credit_transfers = t_to_sepa_credit_transfers,
      to_agent = t_to_agent,
      value = t_amount,
      description = bodyDescription
    )
    val t_from = TransactionRequestAccount (
      bank_id = fromBankId,
      account_id = fromAccountId
    )

    val t_challenge = TransactionRequestChallenge (
      id = challengeId,
      allowed_attempts = challengeAllowedAttempts,
      challenge_type = challengeChallengeType
    )

    val t_charge = TransactionRequestCharge (
    summary = chargeSummary,
    value = AmountOfMoney(currency = chargeCurrency, amount = chargeAmount)
    )

    // Explicit originator (FATF Rec 16) — populated only when stored explicitly on the TR.
    // Virtually filling from customer_account_link happens in the v7 JSON factory layer,
    // which has async access (this sync method does not).
    val t_originator: Option[TransactionRequestOriginator] =
      if (originatorName != null && originatorName.nonEmpty)
        Some(TransactionRequestOriginator(
          name = originatorName,
          address = originatorAddress,
          account_routing = TransactionRequestOriginatorAccountRouting(
            scheme  = originatorAccountRoutingScheme,
            address = originatorAccountRoutingAddress
          )
        ))
      else
        None

    Some(
      TransactionRequest(
        id = TransactionRequestId(transactionRequestId),
        `type`= transactionType,
        from = t_from,
        body = t_body,
        status = status,
        transaction_ids = transactionIds,
        start_date = startDate,
        end_date = endDate,
        challenge = t_challenge,
        charge = t_charge,
        charge_policy = chargePolicy,
        counterparty_id =  CounterpartyId(counterpartyId),
        name = name,
        this_bank_id = BankId(thisBankId),
        this_account_id = AccountId(thisAccountId),
        this_view_id = ViewId(thisViewId),
        other_account_routing_scheme = otherAccountRoutingScheme,
        other_account_routing_address = otherAccountRoutingAddress,
        other_bank_routing_scheme = otherBankRoutingScheme,
        other_bank_routing_address = otherBankRoutingAddress,
        is_beneficiary = isBeneficiary,
        user_id = Option(userId).filter(_.nonEmpty),
        on_behalf_of_user_id = Option(onBehalfOfUserId).filter(_.nonEmpty),
        originator = t_originator
      )
    )
  }
}

object MappedTransactionRequest {

  private val selectColumns =
    fr"""SELECT mtransactionrequestid, mtype, mstatus, mtransactionids, mstartdate, menddate,
                mchallenge_id, mchallenge_allowedattempts, mchallenge_challengetype,
                mcharge_summary, mcharge_amount, mcharge_currency, mcharge_policy,
                mbody_value_currency, mbody_value_amount, mbody_description, mdetails,
                mfrom_bankid, mfrom_accountid, mto_bankid, mto_accountid, mname,
                mthisbankid, mthisaccountid, mthisviewid, mcounterpartyid,
                motheraccountroutingscheme, motheraccountroutingaddress, motherbankroutingscheme,
                motherbankroutingaddress, misbeneficiary, moriginator_name, moriginator_address,
                moriginator_accountroutingscheme, moriginator_accountroutingaddress,
                mpaymentstartdate, mpaymentenddate, mpaymentexecutionrule, mpaymentfrequency,
                mpaymentdayofexecution, mconsentreferenceid, mapistandard, mapiversion,
                muserid, monbehalfofuserid, mconsumerid
         FROM mappedtransactionrequest"""

  // 46 columns, past the 22-element tuple limit, so the row is read as six nested tuples.
  private type RowA = (Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Date], Option[java.sql.Date], Option[String], Option[Int])
  private type RowB = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String])
  private type RowC = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String])
  private type RowD = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[Boolean], Option[String])
  private type RowE = (Option[String], Option[String], Option[String], Option[java.sql.Date],
    Option[java.sql.Date], Option[String], Option[String], Option[String])
  private type RowF = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String])
  private type Row = (RowA, RowB, RowC, RowD, RowE, RowF)

  /**
   * A date read back as a plain java.util.Date, which is what MappedDate handed out.
   *
   * The java.sql.Date the driver returns is a subclass, so it type-checks either way - but it
   * serializes to an empty JSON object rather than a date string, and the transaction-request
   * endpoints put start_date and end_date straight into their response.
   */
  private def readDate(value: Option[java.sql.Date]): Date =
    value.map(d => new Date(d.getTime)).orNull

  private def fromRow(row: Row): MappedTransactionRequest = row match {
    case ((transactionRequestId, transactionType, status, transactionIds, startDate, endDate,
           challengeId, challengeAllowedAttempts),
          (challengeChallengeType, chargeSummary, chargeAmount, chargeCurrency, chargePolicy,
           bodyValueCurrency, bodyValueAmount, bodyDescription),
          (details, fromBankId, fromAccountId, toBankId, toAccountId, name, thisBankId,
           thisAccountId),
          (thisViewId, counterpartyId, otherAccountRoutingScheme, otherAccountRoutingAddress,
           otherBankRoutingScheme, otherBankRoutingAddress, isBeneficiary, originatorName),
          (originatorAddress, originatorAccountRoutingScheme, originatorAccountRoutingAddress,
           paymentStartDate, paymentEndDate, paymentExecutionRule, paymentFrequency,
           paymentDayOfExecution),
          (consentReferenceId, apiStandard, apiVersion, userId, onBehalfOfUserId, consumerId)) =>
      MappedTransactionRequest(
        transactionRequestId.orNull, transactionType.orNull, status.orNull, transactionIds.orNull,
        readDate(startDate), readDate(endDate),
        challengeId.orNull,
        // A NULL count reads back as 0, which is what MappedInt did.
        challengeAllowedAttempts.getOrElse(0),
        challengeChallengeType.orNull, chargeSummary.orNull, chargeAmount.orNull,
        chargeCurrency.orNull, chargePolicy.orNull, bodyValueCurrency.orNull,
        bodyValueAmount.orNull, bodyDescription.orNull, details.orNull, fromBankId.orNull,
        fromAccountId.orNull, toBankId.orNull, toAccountId.orNull, name.orNull, thisBankId.orNull,
        thisAccountId.orNull, thisViewId.orNull, counterpartyId.orNull,
        otherAccountRoutingScheme.orNull, otherAccountRoutingAddress.orNull,
        otherBankRoutingScheme.orNull, otherBankRoutingAddress.orNull,
        isBeneficiary.getOrElse(false), originatorName.orNull, originatorAddress.orNull,
        originatorAccountRoutingScheme.orNull, originatorAccountRoutingAddress.orNull,
        readDate(paymentStartDate), readDate(paymentEndDate),
        paymentExecutionRule.orNull, paymentFrequency.orNull, paymentDayOfExecution.orNull,
        consentReferenceId.orNull, apiStandard.orNull, apiVersion.orNull, userId.orNull,
        onBehalfOfUserId.orNull, consumerId.orNull)
  }

  private def query(condition: Fragment): List[MappedTransactionRequest] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def date(value: Date): Option[java.sql.Date] =
    Option(value).map(d => new java.sql.Date(d.getTime))

  private def one(condition: Fragment): Box[MappedTransactionRequest] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByTransactionRequestId(transactionRequestId: String): Box[MappedTransactionRequest] =
    one(fr"WHERE mtransactionrequestid = ${opt(transactionRequestId)}")

  def findAllByFromAccount(bankId: String, accountId: String): List[MappedTransactionRequest] =
    query(fr"WHERE mfrom_bankid = ${opt(bankId)} AND mfrom_accountid = ${opt(accountId)}")

  /** The first request in the given status, as Mapper's find with a single By did. */
  def findFirstByStatus(status: String): Box[MappedTransactionRequest] =
    one(fr"WHERE mstatus = ${opt(status)}")

  def findAllByStatusUpdatedBefore(status: String, updatedBefore: Date): List[MappedTransactionRequest] =
    query(fr"""WHERE mstatus = ${opt(status)}
                 AND updatedat < ${Option(updatedBefore).map(d => new java.sql.Timestamp(d.getTime))}""")

  def findAllByTypeStatusBanksAndCurrency(transactionType: String, status: String,
                                          fromBankId: String, toBankId: String,
                                          currency: String): List[MappedTransactionRequest] =
    query(fr"""WHERE mtype = ${opt(transactionType)} AND mstatus = ${opt(status)}
                 AND mfrom_bankid = ${opt(fromBankId)} AND mto_bankid = ${opt(toBankId)}
                 AND mbody_value_currency = ${opt(currency)}""")

  /**
   * Completed requests from one account to one counterparty, optionally narrowed by when they were
   * last updated and ordered by the same column. The intended sort field of an OBPOrdering is
   * ignored, as it was under Mapper.
   */
  def findAllCompletedToCounterparty(fromBankId: String, fromAccountId: String,
                                     counterpartyId: String, status: String,
                                     fromDate: Option[Date], toDate: Option[Date],
                                     ascending: Option[Boolean]): List[MappedTransactionRequest] = {
    val filters = List(
      Some(fr"mfrom_bankid = ${opt(fromBankId)}"),
      Some(fr"mfrom_accountid = ${opt(fromAccountId)}"),
      Some(fr"mcounterpartyid = ${opt(counterpartyId)}"),
      Some(fr"mstatus = ${opt(status)}"),
      fromDate.map(d => fr"updatedat >= ${new java.sql.Timestamp(d.getTime)}"),
      toDate.map(d => fr"updatedat <= ${new java.sql.Timestamp(d.getTime)}")
    ).flatten
    val ordering = ascending match {
      case Some(true) => fr"ORDER BY updatedat ASC"
      case Some(false) => fr"ORDER BY updatedat DESC"
      case None => Fragment.empty
    }
    query(fr"WHERE " ++ filters.reduce((a, b) => a ++ fr"AND" ++ b) ++ ordering)
  }

  def insert(row: MappedTransactionRequest): MappedTransactionRequest = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedtransactionrequest
            (mtransactionrequestid, mtype, mstatus, mtransactionids, mstartdate, menddate,
             mchallenge_id, mchallenge_allowedattempts, mchallenge_challengetype, mcharge_summary,
             mcharge_amount, mcharge_currency, mcharge_policy, mbody_value_currency,
             mbody_value_amount, mbody_description, mdetails, mfrom_bankid, mfrom_accountid,
             mto_bankid, mto_accountid, mname, mthisbankid, mthisaccountid, mthisviewid,
             mcounterpartyid, motheraccountroutingscheme, motheraccountroutingaddress,
             motherbankroutingscheme, motherbankroutingaddress, misbeneficiary, moriginator_name,
             moriginator_address, moriginator_accountroutingscheme,
             moriginator_accountroutingaddress, mpaymentstartdate, mpaymentenddate,
             mpaymentexecutionrule, mpaymentfrequency, mpaymentdayofexecution,
             mconsentreferenceid, mapistandard, mapiversion, muserid, monbehalfofuserid,
             mconsumerid, createdat, updatedat)
            VALUES (${opt(row.transactionRequestId)}, ${opt(row.transactionType)},
             ${opt(row.status)}, ${opt(row.transactionIds)}, ${date(row.startDate)},
             ${date(row.endDate)}, ${opt(row.challengeId)}, ${row.challengeAllowedAttempts},
             ${opt(row.challengeChallengeType)}, ${opt(row.chargeSummary)},
             ${opt(row.chargeAmount)}, ${opt(row.chargeCurrency)}, ${opt(row.chargePolicy)},
             ${opt(row.bodyValueCurrency)}, ${opt(row.bodyValueAmount)},
             ${opt(row.bodyDescription)}, ${opt(row.details)}, ${opt(row.fromBankId)},
             ${opt(row.fromAccountId)}, ${opt(row.toBankId)}, ${opt(row.toAccountId)},
             ${opt(row.name)}, ${opt(row.thisBankId)}, ${opt(row.thisAccountId)},
             ${opt(row.thisViewId)}, ${opt(row.counterpartyId)},
             ${opt(row.otherAccountRoutingScheme)}, ${opt(row.otherAccountRoutingAddress)},
             ${opt(row.otherBankRoutingScheme)}, ${opt(row.otherBankRoutingAddress)},
             ${row.isBeneficiary}, ${opt(row.originatorName)}, ${opt(row.originatorAddress)},
             ${opt(row.originatorAccountRoutingScheme)},
             ${opt(row.originatorAccountRoutingAddress)}, ${date(row.paymentStartDate)},
             ${date(row.paymentEndDate)}, ${opt(row.paymentExecutionRule)},
             ${opt(row.paymentFrequency)}, ${opt(row.paymentDayOfExecution)},
             ${opt(row.consentReferenceId)}, ${opt(row.apiStandard)}, ${opt(row.apiVersion)},
             ${opt(row.userId)}, ${opt(row.onBehalfOfUserId)}, ${opt(row.consumerId)},
             $now, $now)"""
        .update.run)
    row
  }

  /** An empty row to build an insert from: every string empty, as Mapper's defaults were. */
  def empty: MappedTransactionRequest = MappedTransactionRequest(
    transactionRequestId = "", transactionType = "", status = "", transactionIds = "",
    startDate = null, endDate = null, challengeId = "", challengeAllowedAttempts = 0,
    challengeChallengeType = "", chargeSummary = "", chargeAmount = "", chargeCurrency = "",
    chargePolicy = "", bodyValueCurrency = "", bodyValueAmount = "", bodyDescription = "",
    details = "", fromBankId = "", fromAccountId = "", toBankId = "", toAccountId = "", name = "",
    thisBankId = "", thisAccountId = "", thisViewId = "", counterpartyId = "",
    otherAccountRoutingScheme = "", otherAccountRoutingAddress = "", otherBankRoutingScheme = "",
    otherBankRoutingAddress = "", isBeneficiary = false, originatorName = "", originatorAddress = "",
    originatorAccountRoutingScheme = "", originatorAccountRoutingAddress = "",
    paymentStartDate = null, paymentEndDate = null, paymentExecutionRule = "",
    paymentFrequency = "", paymentDayOfExecution = "", consentReferenceId = "", apiStandard = "",
    apiVersion = "", userId = "", onBehalfOfUserId = "", consumerId = "")

  private def update(transactionRequestId: String, set: Fragment): Boolean =
    DoobieUtil.runUpdate(
      (fr"UPDATE mappedtransactionrequest SET" ++ set ++
        fr", updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}" ++
        fr"WHERE mtransactionrequestid = ${opt(transactionRequestId)}").update.run) > 0

  def setTransactionIds(transactionRequestId: String, transactionIds: String): Boolean =
    update(transactionRequestId, fr"mtransactionids = ${opt(transactionIds)}")

  def setChallenge(transactionRequestId: String, challengeId: String, allowedAttempts: Int,
                   challengeType: String): Boolean =
    update(transactionRequestId,
      fr"""mchallenge_id = ${opt(challengeId)}, mchallenge_allowedattempts = $allowedAttempts,
           mchallenge_challengetype = ${opt(challengeType)}""")

  def setStatus(transactionRequestId: String, status: String): Boolean =
    update(transactionRequestId, fr"mstatus = ${opt(status)}")

  def setDescription(transactionRequestId: String, description: String): Boolean =
    update(transactionRequestId, fr"mbody_description = ${opt(description)}")

  def setConsumerId(transactionRequestId: String, consumerId: String): Boolean =
    update(transactionRequestId, fr"mconsumerid = ${opt(consumerId)}")

  def deleteByTransactionIds(transactionIds: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedtransactionrequest WHERE mtransactionids = ${opt(transactionIds)}"
        .update.run) > 0

  def deleteAll(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionrequest".update.run)
    true
  }
}
