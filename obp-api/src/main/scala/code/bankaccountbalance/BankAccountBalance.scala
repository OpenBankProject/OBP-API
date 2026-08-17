package code.bankaccountbalance

import code.api.util.DoobieUtil
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BalanceId, BankAccountBalanceTrait, BankId}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import java.util.Date

/**
 * One stored balance for an account.
 *
 * The name is kept from the Lift entity rather than becoming DoobieBankAccountBalance: the
 * concrete type is what BankAccountBalanceProviderTrait's signatures are written in, so renaming
 * would ripple through the provider and its LocalMappedConnector call sites for no gain. (The
 * Connector trait itself is already stated in terms of the obp-commons BankAccountBalanceTrait,
 * so nothing leaks that far.)
 *
 * `currency` is carried on the row so balanceAmount can be converted out of the smallest
 * currency unit it is stored in. Under Mapper this was a per-row `val` that ran its own
 * MappedBankAccount lookup on construction - an N+1 - defaulting to "EUR" when the account was
 * missing. Here the same first-match-or-EUR resolution is done as a correlated subquery in the
 * one SELECT, which is the same answer without the extra round trips.
 */
case class BankAccountBalance(
  balanceIdValue: String,
  bankIdValue: String,
  accountIdValue: String,
  balanceType: String,
  balanceAmountSmallestUnit: Long,
  currency: String,
  referenceDateValue: Option[java.sql.Date],
  updatedAtValue: Date
) extends BankAccountBalanceTrait with MdcLoggable {

  override def bankId: BankId = BankId(bankIdValue)
  override def accountId: AccountId = AccountId(accountIdValue)
  override def balanceId: BalanceId = BalanceId(balanceIdValue)
  override def balanceAmount: BigDecimal =
    Helper.smallestCurrencyUnitToBigDecimal(balanceAmountSmallestUnit, currency)
  override def lastChangeDateTime: Option[Date] = Some(updatedAtValue)
  override def referenceDate: Option[String] = referenceDateValue match {
    case Some(d) => Some(d.toString)
    case None =>
      logger.warn(s"ReferenceDate is missing for BalanceId=$balanceIdValue, AccountId=$accountIdValue, BankId=$bankIdValue")
      None
  }
}

object BankAccountBalance {

  /**
   * Resolves the account currency inline, matching Mapper's
   * `MappedBankAccount.find(By(theAccountId, ...)).map(_.currency).getOrElse("EUR")`:
   * first matching account row, or "EUR" when there is none.
   */
  private val selectColumns =
    fr"""SELECT b.balanceid_, b.bankid_, b.accountid_, b.balancetype, b.balanceamount,
                COALESCE((SELECT a.accountcurrency FROM mappedbankaccount a
                          WHERE a.theaccountid = b.accountid_ LIMIT 1), 'EUR'),
                b.referencedate, b.updatedat
         FROM bankaccountbalance b"""

  private type Row = (String, String, String, String, Long, String, Option[java.sql.Date], java.sql.Timestamp)

  private def fromRow(row: Row): BankAccountBalance = row match {
    case (balanceId, bankId, accountId, balanceType, amount, currency, referenceDate, updatedAt) =>
      BankAccountBalance(balanceId, bankId, accountId, balanceType, amount, currency, referenceDate, updatedAt)
  }

  private def query(condition: Fragment): List[BankAccountBalance] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAllByAccountId(accountId: String): List[BankAccountBalance] =
    query(fr"WHERE b.accountid_ = $accountId")

  def findAllByAccountIds(accountIds: List[String]): List[BankAccountBalance] =
    if (accountIds.isEmpty) Nil
    else {
      val inFrag = Fragments.in(fr"b.accountid_", cats.data.NonEmptyList.fromListUnsafe(accountIds.distinct))
      query(fr"WHERE " ++ inFrag)
    }

  def findByBalanceId(balanceId: String): Box[BankAccountBalance] =
    query(fr"WHERE b.balanceid_ = $balanceId LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** The currency of the account this balance belongs to, or "EUR" when the account is unknown. */
  def accountCurrency(accountId: String): String =
    DoobieUtil.runQuery(
      sql"SELECT accountcurrency FROM mappedbankaccount WHERE theaccountid = $accountId LIMIT 1"
        .query[String].option
    ).getOrElse("EUR")

  def insert(balanceId: String, bankId: String, accountId: String, balanceType: String,
             amountSmallestUnit: Long): BankAccountBalance = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO bankaccountbalance
            (balanceid_, bankid_, accountid_, balancetype, balanceamount, createdat, updatedat)
            VALUES ($balanceId, $bankId, $accountId, $balanceType, $amountSmallestUnit, $now, $now)"""
        .update.run)
    BankAccountBalance(balanceId, bankId, accountId, balanceType, amountSmallestUnit,
      accountCurrency(accountId), None, now)
  }

  def update(balanceId: String, bankId: String, accountId: String, balanceType: String,
             amountSmallestUnit: Long): Unit = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE bankaccountbalance
            SET bankid_ = $bankId, accountid_ = $accountId, balancetype = $balanceType,
                balanceamount = $amountSmallestUnit, updatedat = $now
            WHERE balanceid_ = $balanceId"""
        .update.run)
    ()
  }

  def deleteByBalanceId(balanceId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountbalance WHERE balanceid_ = $balanceId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountbalance".update.run)
    ()
  }
}
