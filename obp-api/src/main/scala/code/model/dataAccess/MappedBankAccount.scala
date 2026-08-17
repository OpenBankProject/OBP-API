package code.model.dataAccess

import java.util.Date

import code.api.util.DoobieUtil
import code.bankconnectors.DoobieBankAccountRoutingQueries
import code.util.Helper
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import scala.collection.immutable.List

/**
 * One bank account, as the local connector stores it.
 *
 * `accountBalance` is signed and in the smallest unit of the currency; `balance` converts it. The
 * balance itself is updated through DoobieBankAccountQueries rather than here, because those
 * updates lock the row.
 *
 * The account rules are a fixed pair of scheme/value columns rather than a child table - exactly
 * two can be stored - while account ROUTINGS are a real child table and are read from it.
 *
 * `accountHolder` reads the deprecated holder column; the real holders live in mapperaccountholders.
 */
case class MappedBankAccount(
  accountPrimaryKey: Long,
  bank: String,
  theAccountId: String,
  accountCurrency: String,
  accountNumber: String,
  holder: String,
  accountBalance: Long,
  accountName: String,
  kind: String,
  accountLabel: String,
  accountLastUpdate: Date,
  branchId: String,
  accountRuleScheme1: String,
  accountRuleValue1: Long,
  accountRuleScheme2: String,
  accountRuleValue2: Long
) extends BankAccount {

  override def accountId: AccountId = AccountId(theAccountId)
  override def bankId: BankId = BankId(bank)
  override def currency: String = accountCurrency.toUpperCase
  override def number: String = accountNumber
  override def balance: BigDecimal = Helper.smallestCurrencyUnitToBigDecimal(accountBalance, currency)
  override def name: String = accountName
  override def accountType: String = kind

  override def label: String = accountLabel
  override def accountHolder: String = holder
  override def lastUpdate : Date = accountLastUpdate

  def createAccountRule(scheme: String, value: Long) = {
    scheme match {
      case s: String if s.equalsIgnoreCase("") == false =>
        val v = Helper.smallestCurrencyUnitToBigDecimal(value, accountCurrency.toUpperCase)
        List(AccountRule(scheme, v.toString()))
      case _ =>
        Nil
    }
  }
  override def accountRoutings: List[AccountRouting] = {
    DoobieBankAccountRoutingQueries.findAllByBankAccount(this.bankId, this.accountId)
      .map(_.accountRouting)
  }
  override def accountRules: List[AccountRule] = createAccountRule(accountRuleScheme1, accountRuleValue1) :::
                                                  createAccountRule(accountRuleScheme2, accountRuleValue2)

}

object MappedBankAccount {

  private val selectColumns =
    fr"""SELECT id, bank, theaccountid, accountcurrency, accountnumber, holder, accountbalance,
                accountname, kind, accountlabel, accountlastupdate, mbranchid, accountrulescheme1,
                accountrulevalue1, accountrulescheme2, accountrulevalue2
         FROM mappedbankaccount"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Long], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[String], Option[String], Option[Long], Option[String],
    Option[Long])

  /** A timestamp read back as a plain java.util.Date, which is what MappedDateTime handed out. */
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  private def fromRow(row: Row): MappedBankAccount = row match {
    case (id, bank, theAccountId, accountCurrency, accountNumber, holder, accountBalance,
          accountName, kind, accountLabel, accountLastUpdate, branchId, accountRuleScheme1,
          accountRuleValue1, accountRuleScheme2, accountRuleValue2) =>
      MappedBankAccount(id, bank.orNull, theAccountId.orNull, accountCurrency.orNull,
        accountNumber.orNull, holder.orNull,
        // A NULL number reads back as 0, which is what MappedLong did.
        accountBalance.getOrElse(0L), accountName.orNull, kind.orNull, accountLabel.orNull,
        readDate(accountLastUpdate), branchId.orNull, accountRuleScheme1.orNull,
        accountRuleValue1.getOrElse(0L), accountRuleScheme2.orNull, accountRuleValue2.getOrElse(0L))
  }

  private def query(condition: Fragment): List[MappedBankAccount] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def timestamp(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  private def one(condition: Fragment): Box[MappedBankAccount] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def find(bankId: String, accountId: String): Box[MappedBankAccount] =
    one(fr"WHERE bank = ${opt(bankId)} AND theaccountid = ${opt(accountId)}")

  /** By surrogate key, for the card rows whose foreign key holds it. */
  def findByPrimaryKey(accountPrimaryKey: Long): Box[MappedBankAccount] =
    one(fr"WHERE id = $accountPrimaryKey")

  def findByAccountNumber(bankId: String, accountNumber: String): Box[MappedBankAccount] =
    one(fr"WHERE bank = ${opt(bankId)} AND accountnumber = ${opt(accountNumber)}")

  /** Without a bank, an account number is only as unique as the deployment makes it. */
  def findAllByAccountNumber(bankId: Option[String], accountNumber: String): List[MappedBankAccount] =
    bankId match {
      case Some(value) =>
        query(fr"WHERE bank = ${opt(value)} AND accountnumber = ${opt(accountNumber)}")
      case None => query(fr"WHERE accountnumber = ${opt(accountNumber)}")
    }

  def findAllByAccountId(accountId: String): List[MappedBankAccount] =
    query(fr"WHERE theaccountid = ${opt(accountId)}")

  def setCurrency(bankId: String, accountId: String, currency: String): Box[MappedBankAccount] =
    update(bankId, accountId, List(fr"accountcurrency = ${opt(currency)}"))

  def findAllByBankId(bankId: String): List[MappedBankAccount] =
    query(fr"WHERE bank = ${opt(bankId)}")

  def findAllByBankIdAndKind(bankId: String, kind: String): List[MappedBankAccount] =
    query(fr"WHERE bank = ${opt(bankId)} AND kind = ${opt(kind)}")

  def findAllByAccountIds(bankId: String, accountIds: List[String]): List[MappedBankAccount] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows - not "no filter".
    if (accountIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"theaccountid",
        cats.data.NonEmptyList.fromListUnsafe(accountIds.distinct))
      query(fr"WHERE bank = ${opt(bankId)} AND " ++ in)
    }

  def findAll(): List[MappedBankAccount] = query(Fragment.empty)

  def count(): Long =
    DoobieUtil.runQuery(fr"SELECT COUNT(*) FROM mappedbankaccount".query[Long].unique)

  def insert(bankId: String,
             accountId: String,
             accountCurrency: String = "",
             accountNumber: String = "",
             holder: String = "",
             accountBalance: Long = 0L,
             accountName: String = "",
             kind: String = "",
             accountLabel: String = "",
             accountLastUpdate: Date = null,
             branchId: String = "",
             accountRuleScheme1: String = "",
             accountRuleValue1: Long = 0L,
             accountRuleScheme2: String = "",
             accountRuleValue2: Long = 0L): MappedBankAccount = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedbankaccount
            (bank, theaccountid, accountcurrency, accountnumber, holder, accountbalance,
             accountname, kind, accountlabel, accountlastupdate, mbranchid, accountrulescheme1,
             accountrulevalue1, accountrulescheme2, accountrulevalue2, createdat, updatedat)
            VALUES (${opt(bankId)}, ${opt(accountId)}, ${opt(accountCurrency)},
             ${opt(accountNumber)}, ${opt(holder)}, $accountBalance, ${opt(accountName)},
             ${opt(kind)}, ${opt(accountLabel)}, ${timestamp(accountLastUpdate)}, ${opt(branchId)},
             ${opt(accountRuleScheme1)}, $accountRuleValue1, ${opt(accountRuleScheme2)},
             $accountRuleValue2, $now, $now)"""
        .update.run)
    find(bankId, accountId)
      .openOrThrowException("the bank account just created must be readable")
  }

  /**
   * Applies the supplied column assignments and returns the row as it now stands.
   *
   * The balance is deliberately not settable here: DoobieBankAccountQueries owns balance updates
   * because they have to lock the row.
   */
  def update(bankId: String, accountId: String, sets: List[Fragment]): Box[MappedBankAccount] = {
    val stamp = fr"updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}"
    val assignments = (sets :+ stamp).reduce((a, b) => a ++ fr"," ++ b)
    DoobieUtil.runUpdate(
      (fr"UPDATE mappedbankaccount SET" ++ assignments ++
        fr"WHERE bank = ${opt(bankId)} AND theaccountid = ${opt(accountId)}").update.run)
    find(bankId, accountId)
  }

  /**
   * Sets the balance outright.
   *
   * Production balance changes go through DoobieBankAccountQueries, which locks the row and
   * applies a delta; this is for fixtures that seed a starting balance.
   */
  def setBalance(bankId: String, accountId: String, balance: Long): Box[MappedBankAccount] =
    update(bankId, accountId, List(fr"accountbalance = $balance"))

  def setAccountLabel(bankId: String, accountId: String, label: String): Box[MappedBankAccount] =
    update(bankId, accountId, List(fr"accountlabel = ${opt(label)}"))

  def setLastUpdate(bankId: String, accountId: String, lastUpdate: Date): Box[MappedBankAccount] =
    update(bankId, accountId, List(fr"accountlastupdate = ${timestamp(lastUpdate)}"))

  def delete(bankId: String, accountId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM mappedbankaccount
            WHERE bank = ${opt(bankId)} AND theaccountid = ${opt(accountId)}"""
        .update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbankaccount".update.run)
    ()
  }
}
