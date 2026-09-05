package code.bankconnectors

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{AccountId, AccountRouting, BankAccountRoutingTrait, BankId}
import doobie._
import doobie.implicits._

/** One bank-account-routing row, standing in for the Lift entity in return types. */
case class BankAccountRoutingRow(
  bankId: BankId,
  accountId: AccountId,
  accountRouting: AccountRouting
) extends BankAccountRoutingTrait

/**
 * Doobie implementation of the bank-account-routing store, replacing the Lift
 * BankAccountRouting entity.
 *
 * Both unique indexes are load-bearing and enforced by the database, not by any check here:
 * (bankId, accountId, scheme) is one address per (account, scheme); (bankId, scheme, address) is
 * one account per (bank, scheme, address) - an address like an IBAN can't be claimed twice at
 * the same bank under the same scheme.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieBankAccountRoutingQueries {

  private def rowOf(r: (String, String, String, String)): BankAccountRoutingRow =
    BankAccountRoutingRow(BankId(r._1), AccountId(r._2), AccountRouting(r._3, r._4))

  private val selectCols: Fragment =
    fr"SELECT bankid, accountid, accountroutingscheme, accountroutingaddress FROM bankaccountrouting"

  def findByBankAccountScheme(bankId: BankId, accountId: AccountId, scheme: String): Option[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${bankId.value} AND accountid = ${accountId.value} AND accountroutingscheme = $scheme LIMIT 1")
        .query[(String, String, String, String)].option
    ).map(rowOf)

  def findByBankSchemeAddress(bankId: BankId, scheme: String, address: String): Option[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${bankId.value} AND accountroutingscheme = $scheme AND accountroutingaddress = $address LIMIT 1")
        .query[(String, String, String, String)].option
    ).map(rowOf)

  def findBySchemeAddress(scheme: String, address: String): Option[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE accountroutingscheme = $scheme AND accountroutingaddress = $address LIMIT 1")
        .query[(String, String, String, String)].option
    ).map(rowOf)

  def findAllByBankSchemeAddress(bankId: BankId, scheme: String, address: String): List[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${bankId.value} AND accountroutingscheme = $scheme AND accountroutingaddress = $address")
        .query[(String, String, String, String)].to[List]
    ).map(rowOf)

  def findAllBySchemeAddress(scheme: String, address: String): List[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE accountroutingscheme = $scheme AND accountroutingaddress = $address")
        .query[(String, String, String, String)].to[List]
    ).map(rowOf)

  def findAllByBankScheme(bankId: BankId, scheme: String): List[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${bankId.value} AND accountroutingscheme = $scheme")
        .query[(String, String, String, String)].to[List]
    ).map(rowOf)

  def findAllByScheme(scheme: String): List[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE accountroutingscheme = $scheme").query[(String, String, String, String)].to[List]
    ).map(rowOf)

  def findAllByBankAccount(bankId: BankId, accountId: AccountId): List[BankAccountRoutingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${bankId.value} AND accountid = ${accountId.value}")
        .query[(String, String, String, String)].to[List]
    ).map(rowOf)

  def create(bankId: BankId, accountId: AccountId, scheme: String, address: String): BankAccountRoutingRow = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO bankaccountrouting (bankid, accountid, accountroutingscheme, accountroutingaddress, createdat, updatedat)
            VALUES (${bankId.value}, ${accountId.value}, $scheme, $address, NOW(), NOW())"""
        .update.run)
    BankAccountRoutingRow(bankId, accountId, AccountRouting(scheme, address))
  }

  /** Rewrites the address for an existing (bankId, accountId, scheme) row. */
  def updateAddress(bankId: BankId, accountId: AccountId, scheme: String, address: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE bankaccountrouting SET accountroutingaddress = $address, updatedat = NOW()
            WHERE bankid = ${bankId.value} AND accountid = ${accountId.value} AND accountroutingscheme = $scheme"""
        .update.run)

  /** Rewrites the accountId for an existing (bankId, scheme, address) row. */
  def updateAccountId(bankId: BankId, scheme: String, address: String, accountId: AccountId): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE bankaccountrouting SET accountid = ${accountId.value}, updatedat = NOW()
            WHERE bankid = ${bankId.value} AND accountroutingscheme = $scheme AND accountroutingaddress = $address"""
        .update.run)

  def deleteByBankAccount(bankId: BankId, accountId: AccountId): Int =
    DoobieUtil.runUpdate(
      sql"DELETE FROM bankaccountrouting WHERE bankid = ${bankId.value} AND accountid = ${accountId.value}".update.run)

  def deleteByBankAccountScheme(bankId: BankId, accountId: AccountId, scheme: String): Int =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM bankaccountrouting
            WHERE bankid = ${bankId.value} AND accountid = ${accountId.value} AND accountroutingscheme = $scheme"""
        .update.run)
}
