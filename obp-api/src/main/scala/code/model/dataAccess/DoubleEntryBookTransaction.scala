package code.model.dataAccess

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{TransactionRequestId => ModelTransactionRequestId, _}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * One row linking the debit and credit legs of a single movement.
 *
 * Both unique indexes are load-bearing: each leg may appear in the book at most once, so a
 * transaction cannot be booked into two different double-entry pairs. The connector's save wraps
 * the insert in tryo, so a duplicate booking surfaces as a Failure.
 *
 * The transactionRequest* columns hold "" rather than NULL when the movement did not originate
 * from a transaction request, which is why the accessors below map empty to None.
 */
case class DoubleEntryBookTransaction(
  private val transactionRequestBankIdRaw: String,
  private val transactionRequestAccountIdRaw: String,
  private val transactionRequestIdRaw: String,
  private val debitTransactionBankIdRaw: String,
  private val debitTransactionAccountIdRaw: String,
  private val debitTransactionIdRaw: String,
  private val creditTransactionBankIdRaw: String,
  private val creditTransactionAccountIdRaw: String,
  private val creditTransactionIdRaw: String
) extends DoubleEntryBookTransactionTrait {

  override def transactionRequestBankId: Option[BankId] =
    if (transactionRequestBankIdRaw.isEmpty) None else Some(BankId(transactionRequestBankIdRaw))
  override def transactionRequestAccountId: Option[AccountId] =
    if (transactionRequestAccountIdRaw.isEmpty) None else Some(AccountId(transactionRequestAccountIdRaw))
  override def transactionRequestId: Option[ModelTransactionRequestId] =
    if (transactionRequestIdRaw.isEmpty) None else Some(ModelTransactionRequestId(transactionRequestIdRaw))

  override def debitTransactionBankId: BankId = BankId(debitTransactionBankIdRaw)
  override def debitTransactionAccountId: AccountId = AccountId(debitTransactionAccountIdRaw)
  override def debitTransactionId: TransactionId = TransactionId(debitTransactionIdRaw)

  override def creditTransactionBankId: BankId = BankId(creditTransactionBankIdRaw)
  override def creditTransactionAccountId: AccountId = AccountId(creditTransactionAccountIdRaw)
  override def creditTransactionId: TransactionId = TransactionId(creditTransactionIdRaw)
}

object DoubleEntryBookTransaction {

  private val selectColumns =
    fr"""SELECT transactionrequestbankid, transactionrequestaccountid, transactionrequestid,
                debittransactionbankid, debittransactionaccountid, debittransactionid,
                credittransactionbankid, credittransactionaccountid, credittransactionid
         FROM doubleentrybooktransaction"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): DoubleEntryBookTransaction = row match {
    case (transactionRequestBankId, transactionRequestAccountId, transactionRequestId,
          debitTransactionBankId, debitTransactionAccountId, debitTransactionId,
          creditTransactionBankId, creditTransactionAccountId, creditTransactionId) =>
      DoubleEntryBookTransaction(transactionRequestBankId.orNull,
        transactionRequestAccountId.orNull, transactionRequestId.orNull,
        debitTransactionBankId.orNull, debitTransactionAccountId.orNull, debitTransactionId.orNull,
        creditTransactionBankId.orNull, creditTransactionAccountId.orNull,
        creditTransactionId.orNull)
  }

  private def query(condition: Fragment): List[DoubleEntryBookTransaction] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DoubleEntryBookTransaction] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(transactionRequestBankId: String, transactionRequestAccountId: String,
             transactionRequestId: String, debitTransactionBankId: String,
             debitTransactionAccountId: String, debitTransactionId: String,
             creditTransactionBankId: String, creditTransactionAccountId: String,
             creditTransactionId: String): DoubleEntryBookTransaction = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO doubleentrybooktransaction
            (transactionrequestbankid, transactionrequestaccountid, transactionrequestid,
             debittransactionbankid, debittransactionaccountid, debittransactionid,
             credittransactionbankid, credittransactionaccountid, credittransactionid,
             createdat, updatedat)
            VALUES ($transactionRequestBankId, $transactionRequestAccountId, $transactionRequestId,
             $debitTransactionBankId, $debitTransactionAccountId, $debitTransactionId,
             $creditTransactionBankId, $creditTransactionAccountId, $creditTransactionId,
             $now, $now)"""
        .update.run)
    DoubleEntryBookTransaction(transactionRequestBankId, transactionRequestAccountId,
      transactionRequestId, debitTransactionBankId, debitTransactionAccountId, debitTransactionId,
      creditTransactionBankId, creditTransactionAccountId, creditTransactionId)
  }

  /** The booking whose debit leg is this transaction, else the one whose credit leg is. */
  def findByLeg(bankId: String, accountId: String, transactionId: String): Box[DoubleEntryBookTransaction] =
    one(fr"""WHERE debittransactionbankid = $bankId AND debittransactionaccountid = $accountId
             AND debittransactionid = $transactionId""")
      .or(one(fr"""WHERE credittransactionbankid = $bankId AND credittransactionaccountid = $accountId
                   AND credittransactionid = $transactionId"""))

  /** The same, ignoring bank and account — used to find a transaction's balancing counterpart. */
  def findByTransactionId(transactionId: String): Box[DoubleEntryBookTransaction] =
    one(fr"WHERE debittransactionid = $transactionId")
      .or(one(fr"WHERE credittransactionid = $transactionId"))

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM doubleentrybooktransaction".update.run)
    ()
  }
}
