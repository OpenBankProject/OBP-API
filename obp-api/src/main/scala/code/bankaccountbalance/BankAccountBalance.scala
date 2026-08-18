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
  balanceId: BalanceId,
  bankId: BankId,
  accountId: AccountId,
  balanceType: String,
  balanceAmount: BigDecimal,
  referenceDate: Option[String],
  lastChangeDateTime: Option[Date],
  // Storage detail rather than part of the trait: the column holds the amount in the smallest
  // currency unit, and writes need it back in that form.
  balanceAmountSmallestUnit: Long,
  currency: String
) extends BankAccountBalanceTrait with MdcLoggable

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

  private type Row = (String, String, String, Option[String], Option[Long], String,
    Option[java.sql.Date], Option[java.sql.Timestamp])

  private def fromRow(row: Row): BankAccountBalance = row match {
    case (balanceId, bankId, accountId, balanceType, amount, currency, referenceDate, updatedAt) =>
      val amountSmallestUnit = amount.getOrElse(0L)
      BankAccountBalance(
        balanceId = BalanceId(balanceId),
        bankId = BankId(bankId),
        accountId = AccountId(accountId),
        balanceType = balanceType.orNull,
        balanceAmount = Helper.smallestCurrencyUnitToBigDecimal(amountSmallestUnit, currency),
        referenceDate = referenceDate.map(_.toString),
        // A java.sql.Timestamp put straight into a field typed java.util.Date serializes as {};
        // convert it.
        lastChangeDateTime = updatedAt.map(t => new Date(t.getTime)),
        balanceAmountSmallestUnit = amountSmallestUnit,
        currency = currency)
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
    val currency = accountCurrency(accountId)
    BankAccountBalance(
      balanceId = BalanceId(balanceId),
      bankId = BankId(bankId),
      accountId = AccountId(accountId),
      balanceType = balanceType,
      balanceAmount = Helper.smallestCurrencyUnitToBigDecimal(amountSmallestUnit, currency),
      referenceDate = None,
      lastChangeDateTime = Some(new Date(now.getTime)),
      balanceAmountSmallestUnit = amountSmallestUnit,
      currency = currency)
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
