package code.investigation

import java.sql.Timestamp

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * Doobie queries for the Customer Investigation Report endpoint.
 *
 * Each query maps to a logical data retrieval step that could potentially
 * be replaced by a connector call in the future. Keeping them separate
 * makes the endpoint easier to refactor for non-mapped connectors.
 *
 * Tables used:
 * - mappedcustomer
 * - customeraccountlink
 * - mappedbankaccount
 * - mappedtransaction
 * - mappedcustomerlink
 */
object DoobieInvestigationQueries extends MdcLoggable {

  // Result case classes — these are internal to the query layer,
  // not the JSON response classes.

  case class CustomerRow(
    customerId: String,
    legalName: String,
    email: String,
    mobileNumber: String,
    kycStatus: Boolean
  )

  case class AccountRow(
    accountId: String,
    bankId: String,
    currency: String,
    balance: Long,
    accountName: String,
    accountType: String
  )

  case class TransactionRow(
    transactionId: String,
    bankId: String,
    accountId: String,
    amount: Long,
    currency: String,
    transactionType: String,
    description: String,
    startDate: Timestamp,
    finishDate: Timestamp,
    counterpartyName: String,
    counterpartyAccount: String,
    counterpartyBankName: String
  )

  case class CustomerLinkRow(
    customerLinkId: String,
    otherCustomerId: String,
    otherBankId: String,
    relationship: String,
    otherLegalName: String
  )

  case class AccountLinkRow(
    customerId: String,
    accountId: String,
    bankId: String,
    relationshipType: String
  )

  /**
   * Get customer details by customer ID at a specific bank.
   */
  def getCustomerAtBank(customerId: String, bankId: String): Option[CustomerRow] = {
    logger.info(s"getCustomerAtBank says: customerId=$customerId bankId=$bankId")
    val query: ConnectionIO[Option[CustomerRow]] =
      sql"""SELECT mcustomerid, mlegalname, memail, mmobilenumber, mkycstatus
            FROM mappedcustomer
            WHERE mcustomerid = $customerId
              AND mbank = $bankId"""
        .query[CustomerRow]
        .option

    DoobieUtil.runQuery(query)
  }

  /**
   * Get all accounts linked to a customer via customeraccountlink.
   */
  def getAccountsForCustomer(customerId: String): List[AccountRow] = {
    logger.info(s"getAccountsForCustomer says: customerId=$customerId")
    val query: ConnectionIO[List[AccountRow]] =
      sql"""SELECT a.theaccountid, a.bank, a.accountcurrency, a.accountbalance, a.accountname, a.kind
            FROM mappedbankaccount a
            JOIN customeraccountlink cal ON cal.accountid = a.theaccountid AND cal.bankid = a.bank
            WHERE cal.customerid = $customerId"""
        .query[AccountRow]
        .to[List]

    DoobieUtil.runQuery(query)
  }

  /**
   * Get transactions for a list of accounts at a bank within a date range.
   */
  def getTransactionsForAccounts(
    accountIds: List[String],
    bankId: String,
    fromDate: Timestamp,
    toDate: Timestamp,
    limit: Int
  ): List[TransactionRow] = {
    logger.info(s"getTransactionsForAccounts says: accountIds=${accountIds.size} bankId=$bankId limit=$limit")
    if (accountIds.isEmpty) return Nil

    val accountIdFragments = accountIds.map(id => fr"$id")
    val inClause = accountIdFragments.reduceLeft((a, b) => a ++ fr"," ++ b)

    val query: ConnectionIO[List[TransactionRow]] =
      (fr"""SELECT transactionid, bank, account, amount, currency, transactiontype,
                   description, tstartdate, tfinishdate,
                   counterpartyaccountholder,
                   cpotheraccountroutingaddress,
                   counterpartybankname
            FROM mappedtransaction
            WHERE bank = $bankId
              AND account IN (""" ++ inClause ++ fr""")
              AND tstartdate >= $fromDate
              AND tstartdate <= $toDate
            ORDER BY tstartdate DESC
            LIMIT $limit""")
        .query[TransactionRow]
        .to[List]

    DoobieUtil.runQuery(query)
  }

  /**
   * Get customer links (related customers) for a customer,
   * joined with mappedcustomer to get the related customer's legal name.
   */
  def getCustomerLinks(customerId: String): List[CustomerLinkRow] = {
    logger.info(s"getCustomerLinks says: customerId=$customerId")
    val query: ConnectionIO[List[CustomerLinkRow]] =
      sql"""SELECT cl.mcustomerlinkid, cl.mothercustomerid, cl.motherbankid, cl.mrelationshipto,
                   COALESCE(c.mlegalname, '')
            FROM mappedcustomerlink cl
            LEFT JOIN mappedcustomer c ON c.mcustomerid = cl.mothercustomerid
            WHERE cl.mcustomerid = $customerId"""
        .query[CustomerLinkRow]
        .to[List]

    DoobieUtil.runQuery(query)
  }

  /**
   * Get account links for a customer — which accounts they own/have access to.
   */
  def getAccountLinksForCustomer(customerId: String): List[AccountLinkRow] = {
    logger.info(s"getAccountLinksForCustomer says: customerId=$customerId")
    val query: ConnectionIO[List[AccountLinkRow]] =
      sql"""SELECT customerid, accountid, bankid, relationshiptype
            FROM customeraccountlink
            WHERE customerid = $customerId"""
        .query[AccountLinkRow]
        .to[List]

    DoobieUtil.runQuery(query)
  }
}
