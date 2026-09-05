package code.transaction


import code.accountholders.AccountHolders
import code.api.util.{APIUtil, ApiTrigger, DoobieUtil, OBPAscending, OBPDescending, OBPFromDate, OBPLimit, OBPOffset, OBPOrdering, OBPQueryParam, OBPToDate, OBPTransactionDirection}
import code.bankconnectors.LocalMappedConnector
import code.bankconnectors.LocalMappedConnector.getBankAccountCommon
import code.model._
import code.usercustomerlinks.UserCustomerLink
import code.util.Helper.MdcLoggable
import code.util._
import code.webhook.WebhookAction
import code.webhook.WebhookActor.{AccountNotificationWebhookRequest, RelatedEntity, WebhookRequest}
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.Box.tryo
import net.liftweb.common._

import java.util.Date

/**
 * One transaction as the local connector stores it.
 *
 * A transfer is written twice, once from each side, and both rows carry the same transaction id -
 * which is why the unique index spans (transactionId, bank, account) rather than the id alone.
 *
 * `amount` and `newAccountBalance` are signed and in the smallest unit of the currency (cents, yen,
 * øre); the sign of `amount` is the credit/debit indicator.
 *
 * The counterparty fields are a snapshot taken when the transaction was written, not a reference:
 * a transaction has to keep reading correctly after the counterparty it names is edited or deleted.
 */
case class MappedTransaction(
  bank: String,
  account: String,
  transactionId: String,
  transactionUUID: String,
  transactionType: String,
  amount: Long,
  newAccountBalance: Long,
  currency: String,
  tStartDate: Date,
  tFinishDate: Date,
  description: String,
  chargePolicy: String,
  counterpartyAccountHolder: String,
  counterpartyAccountKind: String,
  counterpartyBankName: String,
  counterpartyNationalId: String,
  counterpartyAccountNumber: String,
  counterpartyIban: String,
  CPCounterPartyId: String,
  CPOtherAccountProvider: String,
  CPOtherAccountRoutingScheme: String,
  CPOtherAccountRoutingAddress: String,
  CPOtherAccountSecondaryRoutingScheme: String,
  CPOtherAccountSecondaryRoutingAddress: String,
  CPOtherBankRoutingScheme: String,
  CPOtherBankRoutingAddress: String,
  status: String
) extends TransactionUUID with MdcLoggable {

  override def theTransactionId = TransactionId(transactionId)
  override def theAccountId = AccountId(account)
  override def theBankId = BankId(bank)

  def getCounterpartyIban() = {
    val i = counterpartyIban
    if(i.isEmpty) None else Some(i)
  }

  //This method have the side affect, it will createOrget the counterparty-metaData and ger transaction- metadata in database
  //It is a expensive method, cause the perfermance issue somehow.
  def toTransaction(account: BankAccount): Option[Transaction] = {
    val tBankId = theBankId
    val tAccId = theAccountId

    if (tBankId != account.bankId || tAccId != account.accountId) {
      logger.warn("Attempted to convert MappedTransaction to Transaction using unrelated existing BankAccount object")
      None
    } else {
      val transactionDescription = {
        val d = description
        if (d.isEmpty) None else Some(d)
      }

      val transactionCurrency = currency
      val transactionAmount = Helper.smallestCurrencyUnitToBigDecimal(amount, transactionCurrency)
      val newBalance = Helper.smallestCurrencyUnitToBigDecimal(newAccountBalance, transactionCurrency)

      val counterpartyName = counterpartyAccountHolder
      val otherAccountRoutingScheme = CPOtherAccountRoutingScheme
      val otherAccountRoutingAddress = CPOtherAccountRoutingAddress

      //TODO This method should be as general as possible, need move to general object, not here.
      //This method is expensive, it has the side affact, will getOrCreateMetadata
      def createCounterparty(counterpartyId : String) = {
        new Counterparty(
          counterpartyId = counterpartyId,
          kind = counterpartyAccountKind,
          nationalIdentifier = counterpartyNationalId,
          counterpartyName = counterpartyAccountHolder,
          thisBankId = theBankId,
          thisAccountId = theAccountId,
          otherAccountProvider = counterpartyAccountHolder,
          otherBankRoutingAddress = Some(CPOtherBankRoutingAddress),
          otherBankRoutingScheme = CPOtherBankRoutingScheme,
          otherAccountRoutingScheme = otherAccountRoutingScheme,
          otherAccountRoutingAddress = Some(otherAccountRoutingAddress),
          isBeneficiary = true
        )
      }

      //It is clear, we create the counterpartyId first, and assign it to metadata.counterpartyId and counterparty.counterpartyId manually
      val counterpartyId = APIUtil.createImplicitCounterpartyId(
        theBankId.value,
        theAccountId.value,
        counterpartyName,
        otherAccountRoutingScheme,
        otherAccountRoutingAddress
      )
      val otherAccount = createCounterparty(counterpartyId)

      Some(new Transaction(
                            transactionUUID,
                            theTransactionId,
                            account,
                            otherAccount,
                            transactionType,
                            transactionAmount,
                            transactionCurrency,
                            transactionDescription,
                            tStartDate,
                            Some(tFinishDate),
                            newBalance,
                            Option(status).map(_.toString)))
    }
  }

  def toTransactionCore(account: BankAccount): Option[TransactionCore] = {
    val tBankId = theBankId
    val tAccId = theAccountId

    if (tBankId != account.bankId || tAccId != account.accountId) {
      logger.warn("Attempted to convert MappedTransaction to Transaction using unrelated existing BankAccount object")
      None
    } else {
      val transactionDescription = {
        val d = description
        if (d.isEmpty) None else Some(d)
      }

      val transactionCurrency = currency
      val transactionAmount = Helper.smallestCurrencyUnitToBigDecimal(amount, transactionCurrency)
      val newBalance = Helper.smallestCurrencyUnitToBigDecimal(newAccountBalance, transactionCurrency)

      val counterpartyName = counterpartyAccountHolder
      val otherAccountRoutingScheme = CPOtherAccountRoutingScheme
      val otherAccountRoutingAddress = CPOtherAccountRoutingAddress

      //TODO This method should be as general as possible, need move to general object, not here.
      //This method is expensive, it has the side affact, will getOrCreateMetadata
      def createCounterpartyCore(counterpartyId : String) = {
        new CounterpartyCore(
          counterpartyId = counterpartyId,
          kind = counterpartyAccountKind,
          counterpartyName = counterpartyName,
          thisBankId = theBankId,
          thisAccountId = theAccountId,
          otherAccountProvider = counterpartyAccountHolder,
          otherBankRoutingAddress = Some(CPOtherBankRoutingAddress),
          otherBankRoutingScheme = CPOtherBankRoutingScheme,
          otherAccountRoutingScheme = otherAccountRoutingScheme,
          otherAccountRoutingAddress = Some(otherAccountRoutingAddress),
          isBeneficiary = true
        )
      }

      //It is clear, we create the counterpartyId first, and assign it to metadata.counterpartyId and counterparty.counterpartyId manually
      val counterpartyId = APIUtil.createImplicitCounterpartyId(theBankId.value, theAccountId.value, counterpartyName, otherAccountRoutingScheme, otherAccountRoutingAddress)
      val otherAccount = createCounterpartyCore(counterpartyId)

      Some(TransactionCore(
        theTransactionId,
        account,
        otherAccount,
        transactionType,
        transactionAmount,
        transactionCurrency,
        transactionDescription,
        tStartDate,
        tFinishDate,
        newBalance))
    }
  }

  def toTransaction : Option[Transaction] = {
    code.api.Constant.CONNECTOR match {
      case Full("akka_vDec2018") =>
        for {
          acc <- getBankAccountCommon(theBankId, theAccountId, None).map(_._1)
          transaction <- toTransaction(acc)
        } yield transaction
      case _ =>
        for {
          acc <- LocalMappedConnector.getBankAccountLegacy(theBankId, theAccountId, None).map(_._1)
          transaction <- toTransaction(acc)
        } yield transaction
    }

  }

}

/**
 * The filters and paging one transaction read carries.
 *
 * Kept as a value rather than as SQL because it is also part of the cache key for the read: two
 * requests that ask for different pages, date ranges or directions must not share a cached answer.
 */
case class TransactionQuery(
  limit: Option[Int],
  offset: Option[Int],
  fromDate: Option[Date],
  toDate: Option[Date],
  creditOnly: Option[Boolean],
  ascending: Option[Boolean]
)

object TransactionQuery {

  /**
   * The date filters and the ordering both work on tFinishDate; the intended sort field of an
   * OBPOrdering is ignored, as it was under Mapper.
   *
   * The direction restriction belongs in the query rather than being applied to the rows
   * afterwards, so the database narrows and paginates in the same pass: filtering an
   * already-limited page hands the caller a short page it cannot distinguish from the end of the
   * data. Zero counts as a credit, matching UKAmounts.creditDebitIndicator.
   */
  def fromQueryParams(queryParams: List[OBPQueryParam]): TransactionQuery =
    TransactionQuery(
      limit = queryParams.collect { case OBPLimit(value) => value }.headOption,
      offset = queryParams.collect { case OBPOffset(value) => value }.headOption,
      fromDate = queryParams.collect { case OBPFromDate(date) => date }.headOption,
      toDate = queryParams.collect { case OBPToDate(date) => date }.headOption,
      creditOnly = queryParams.collect { case OBPTransactionDirection(isCredit) => isCredit }.headOption,
      ascending = queryParams.collect {
        case OBPOrdering(_, OBPAscending) => true
        case OBPOrdering(_, OBPDescending) => false
      }.headOption)
}

object MappedTransaction extends MdcLoggable {

  private val selectColumns =
    fr"""SELECT bank, account, transactionid, transactionuuid, transactiontype, amount,
                newaccountbalance, currency, tstartdate, tfinishdate, description, chargepolicy,
                counterpartyaccountholder, counterpartyaccountkind, counterpartybankname,
                counterpartynationalid, counterpartyaccountnumber, counterpartyiban,
                cpcounterpartyid, cpotheraccountprovider, cpotheraccountroutingscheme,
                cpotheraccountroutingaddress, cpotheraccountsecondaryroutingscheme,
                cpotheraccountsecondaryroutingaddress, cpotherbankroutingscheme,
                cpotherbankroutingaddress, status
         FROM mappedtransaction"""

  // 27 columns, past the 22-element tuple limit, so the row is read as three nested tuples.
  private type RowHead = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Long], Option[Long], Option[String], Option[java.sql.Timestamp])
  private type RowMiddle = (Option[java.sql.Timestamp], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])
  private type RowTail = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String])
  private type Row = (RowHead, RowMiddle, RowTail)

  /**
   * A timestamp read back as a plain java.util.Date, which is what MappedDateTime handed out.
   *
   * The java.sql.Timestamp the driver returns is a subclass, so it type-checks either way - but it
   * does not serialize as a date string, and these dates go straight into transaction responses.
   */
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  private def fromRow(row: Row): MappedTransaction = row match {
    case ((bank, account, transactionId, transactionUUID, transactionType, amount,
           newAccountBalance, currency, tStartDate),
          (tFinishDate, description, chargePolicy, counterpartyAccountHolder,
           counterpartyAccountKind, counterpartyBankName, counterpartyNationalId,
           counterpartyAccountNumber, counterpartyIban),
          (cpCounterPartyId, cpOtherAccountProvider, cpOtherAccountRoutingScheme,
           cpOtherAccountRoutingAddress, cpOtherAccountSecondaryRoutingScheme,
           cpOtherAccountSecondaryRoutingAddress, cpOtherBankRoutingScheme,
           cpOtherBankRoutingAddress, status)) =>
      MappedTransaction(
        bank.orNull, account.orNull, transactionId.orNull, transactionUUID.orNull,
        transactionType.orNull,
        // A NULL amount or balance reads back as 0, which is what MappedLong did.
        amount.getOrElse(0L), newAccountBalance.getOrElse(0L), currency.orNull,
        readDate(tStartDate), readDate(tFinishDate),
        description.orNull, chargePolicy.orNull, counterpartyAccountHolder.orNull,
        counterpartyAccountKind.orNull, counterpartyBankName.orNull, counterpartyNationalId.orNull,
        counterpartyAccountNumber.orNull, counterpartyIban.orNull, cpCounterPartyId.orNull,
        cpOtherAccountProvider.orNull, cpOtherAccountRoutingScheme.orNull,
        cpOtherAccountRoutingAddress.orNull, cpOtherAccountSecondaryRoutingScheme.orNull,
        cpOtherAccountSecondaryRoutingAddress.orNull, cpOtherBankRoutingScheme.orNull,
        cpOtherBankRoutingAddress.orNull, status.orNull)
  }

  private def query(condition: Fragment): List[MappedTransaction] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def ts(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  def find(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[MappedTransaction] =
    query(fr"""WHERE bank = ${opt(bankId.value)} AND account = ${opt(accountId.value)}
                 AND transactionid = ${opt(transactionId.value)}
               ORDER BY id ASC LIMIT 1""").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** A transfer is stored once per side, so an id alone can match two rows; the first one wins. */
  def findByTransactionId(transactionId: TransactionId): Box[MappedTransaction] =
    query(fr"WHERE transactionid = ${opt(transactionId.value)} ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAll(bankId: BankId, accountId: AccountId, params: TransactionQuery): List[MappedTransaction] = {
    val filters = List(
      Some(fr"bank = ${opt(bankId.value)}"),
      Some(fr"account = ${opt(accountId.value)}"),
      params.fromDate.map(date => fr"tfinishdate >= ${ts(date)}"),
      params.toDate.map(date => fr"tfinishdate <= ${ts(date)}"),
      params.creditOnly.map {
        case true => fr"amount >= ${OBPTransactionDirection.creditFloorInSmallestUnit}"
        case false => fr"amount < ${OBPTransactionDirection.creditFloorInSmallestUnit}"
      }
    ).flatten
    val where = fr"WHERE " ++ filters.reduce((a, b) => a ++ fr"AND" ++ b)
    val ordering = params.ascending match {
      case Some(true) => fr"ORDER BY tfinishdate ASC"
      case Some(false) => fr"ORDER BY tfinishdate DESC"
      // No OBPOrdering means no ORDER BY at all, as under Mapper: the row order is whatever the
      // database gives back.
      case None => Fragment.empty
    }
    // OFFSET without LIMIT is valid and is what StartAt on its own produced.
    val paging =
      params.limit.map(value => fr"LIMIT $value").getOrElse(Fragment.empty) ++
        params.offset.map(value => fr"OFFSET $value").getOrElse(Fragment.empty)
    query(where ++ ordering ++ paging)
  }

  def countByBankAccount(bankId: BankId, accountId: AccountId): Long =
    DoobieUtil.runQuery(
      (fr"""SELECT COUNT(*) FROM mappedtransaction
            WHERE bank = ${opt(bankId.value)} AND account = ${opt(accountId.value)}""")
        .query[Long].unique)

  /**
   * Writes one transaction and fires the webhooks that Mapper's afterSave hook fired.
   *
   * transactionId and transactionUUID default to fresh UUIDs, which is what the entity's field
   * defaults did; the sandbox import is the one caller that supplies its own transaction id.
   */
  def insert(bank: String,
             account: String,
             transactionType: String,
             amount: Long,
             newAccountBalance: Long,
             currency: String,
             tStartDate: Date,
             tFinishDate: Date,
             description: String,
             transactionId: String = APIUtil.generateUUID(),
             chargePolicy: String = "",
             counterpartyAccountHolder: String = "",
             counterpartyAccountKind: String = "",
             counterpartyBankName: String = "",
             counterpartyNationalId: String = "",
             counterpartyAccountNumber: String = "",
             counterpartyIban: String = "",
             cpCounterPartyId: String = "",
             cpOtherAccountProvider: String = "",
             cpOtherAccountRoutingScheme: String = "",
             cpOtherAccountRoutingAddress: String = "",
             cpOtherAccountSecondaryRoutingScheme: String = "",
             cpOtherAccountSecondaryRoutingAddress: String = "",
             cpOtherBankRoutingScheme: String = "",
             cpOtherBankRoutingAddress: String = "",
             status: String = ""): MappedTransaction = {
    val transactionUUID = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedtransaction
            (bank, account, transactionid, transactionuuid, transactiontype, amount,
             newaccountbalance, currency, tstartdate, tfinishdate, description, chargepolicy,
             counterpartyaccountholder, counterpartyaccountkind, counterpartybankname,
             counterpartynationalid, counterpartyaccountnumber, counterpartyiban, cpcounterpartyid,
             cpotheraccountprovider, cpotheraccountroutingscheme, cpotheraccountroutingaddress,
             cpotheraccountsecondaryroutingscheme, cpotheraccountsecondaryroutingaddress,
             cpotherbankroutingscheme, cpotherbankroutingaddress, status, extrainfo,
             createdat, updatedat)
            VALUES (${opt(bank)}, ${opt(account)}, ${opt(transactionId)}, $transactionUUID,
             ${opt(transactionType)}, $amount, $newAccountBalance, ${opt(currency)},
             ${ts(tStartDate)}, ${ts(tFinishDate)}, ${opt(description)}, ${opt(chargePolicy)},
             ${opt(counterpartyAccountHolder)}, ${opt(counterpartyAccountKind)},
             ${opt(counterpartyBankName)}, ${opt(counterpartyNationalId)},
             ${opt(counterpartyAccountNumber)}, ${opt(counterpartyIban)}, ${opt(cpCounterPartyId)},
             ${opt(cpOtherAccountProvider)}, ${opt(cpOtherAccountRoutingScheme)},
             ${opt(cpOtherAccountRoutingAddress)}, ${opt(cpOtherAccountSecondaryRoutingScheme)},
             ${opt(cpOtherAccountSecondaryRoutingAddress)}, ${opt(cpOtherBankRoutingScheme)},
             ${opt(cpOtherBankRoutingAddress)}, ${opt(status)}, '', $now, $now)"""
        .update.run)
    val transaction = MappedTransaction(bank, account, transactionId, transactionUUID,
      transactionType, amount, newAccountBalance, currency, tStartDate, tFinishDate, description,
      chargePolicy, counterpartyAccountHolder, counterpartyAccountKind, counterpartyBankName,
      counterpartyNationalId, counterpartyAccountNumber, counterpartyIban, cpCounterPartyId,
      cpOtherAccountProvider, cpOtherAccountRoutingScheme, cpOtherAccountRoutingAddress,
      cpOtherAccountSecondaryRoutingScheme, cpOtherAccountSecondaryRoutingAddress,
      cpOtherBankRoutingScheme, cpOtherBankRoutingAddress, status)
    notifyWebhooks(transaction)
    transaction
  }

  /**
   * The webhook fan-out Mapper ran in afterSave.
   *
   * Kept inside the store rather than at the call sites so that every write still triggers it, and
   * still swallowed by tryo: a webhook subscriber must not be able to fail the transaction that
   * was just written.
   */
  private def notifyWebhooks(t: MappedTransaction): Unit = {
    tryo {
      def getAmount(value: Long): String = {
        Helper.smallestCurrencyUnitToBigDecimal(value, t.currency).toString() + " " + t.currency
      }
      def sendMessage(apiTrigger: ApiTrigger): Unit = {
        if(apiTrigger.equals(ApiTrigger.onCreateTransaction)){

          val userIdCustomerIdPairs: List[(String, String)] = for{
            holder <- AccountHolders.accountHolders.vend.getAccountHolders(t.theBankId, t.theAccountId).toList
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(holder.userId)
          } yield{
            (holder.userId, userCustomerLink.customerId)
          }

          val userIdCustomerIdsPairs: Map[String, List[String]] = userIdCustomerIdPairs.groupBy(_._1).map( a => (a._1,a._2.map(_._2)))
          val eventId = APIUtil.generateUUID()
          logger.debug("Before firing WebhookActor.AccountNotificationWebhookRequest.eventId: " + eventId)
          WebhookAction.accountNotificationWebhookRequest(
            AccountNotificationWebhookRequest(
              apiTrigger,
              eventId,
              t.theBankId.value,
              t.theAccountId.value,
              t.theTransactionId.value,
              userIdCustomerIdsPairs.map(pair => RelatedEntity(pair._1, pair._2)).toList
            )
          )
        } else{
          val eventId = APIUtil.generateUUID()
          logger.debug("Before firing WebhookActor.WebhookRequest.eventId: " + eventId)
          WebhookAction.webhookRequest(
            WebhookRequest(
              apiTrigger,
              eventId,
              t.theBankId.value,
              t.theAccountId.value,
              getAmount(t.amount),
              getAmount(t.newAccountBalance)
            )
          )
        }
      }

      t.amount match {
        case amount if amount > 0 =>
          sendMessage(ApiTrigger.onBalanceChange)
          sendMessage(ApiTrigger.onCreditTransaction)
          sendMessage(ApiTrigger.onCreateTransaction)
        case amount if amount < 0 =>
          sendMessage(ApiTrigger.onBalanceChange)
          sendMessage(ApiTrigger.onDebitTransaction)
          sendMessage(ApiTrigger.onCreateTransaction)
        case _  =>
          // Do not send anything
      }
    }
    ()
  }

  def deleteByTransactionId(transactionId: TransactionId): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedtransaction WHERE transactionid = ${opt(transactionId.value)}"
        .update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransaction".update.run)
    ()
  }
}
