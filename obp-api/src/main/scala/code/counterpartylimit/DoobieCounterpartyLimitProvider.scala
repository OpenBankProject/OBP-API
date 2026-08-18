package code.counterpartylimit

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.CounterpartyLimitTrait
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.json4s.Formats
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

import scala.concurrent.Future

/** One counterparty-limit row, standing in for the Lift entity in return types. */
case class CounterpartyLimitRow(
  counterpartyLimitId: String,
  bankId: String,
  accountId: String,
  viewId: String,
  counterpartyId: String,
  currency: String,
  maxSingleAmount: BigDecimal,
  maxMonthlyAmount: BigDecimal,
  maxNumberOfMonthlyTransactions: Int,
  maxYearlyAmount: BigDecimal,
  maxNumberOfYearlyTransactions: Int,
  maxTotalAmount: BigDecimal,
  maxNumberOfTransactions: Int
) extends CounterpartyLimitTrait {
  override def toJValue(implicit format: Formats): JValue =
    ("counterparty_limit_id", counterpartyLimitId) ~
      ("bank_id", bankId) ~
      ("account_id", accountId) ~
      ("view_id", viewId) ~
      ("counterparty_id", counterpartyId) ~
      ("currency", currency) ~
      ("max_single_amount", maxSingleAmount) ~
      ("max_monthly_amount", maxMonthlyAmount) ~
      ("max_number_of_monthly_transactions", maxNumberOfMonthlyTransactions) ~
      ("max_yearly_amount", maxYearlyAmount) ~
      ("max_number_of_yearly_transactions", maxNumberOfYearlyTransactions) ~
      ("max_total_amount", maxTotalAmount) ~
      ("max_number_of_transactions", maxNumberOfTransactions)
}

/**
 * Doobie implementation of the counterparty-limit store, replacing the Lift CounterpartyLimit
 * entity.
 *
 * Two unique indexes: one on counterpartylimitid, one on the composite
 * (bankid, accountid, viewid, counterpartyid) - at most one limit per tuple, matching the
 * entity's own dbIndexes.
 *
 * Amount fields default to 0 and transaction-count fields default to -1 at the application layer
 * on create, matching the Mapper fields' own defaultValue overrides, which only fired through the
 * Mapper API (no column-level DEFAULT).
 */
object DoobieCounterpartyLimitProvider extends CounterpartyLimitProviderTrait {

  // MappedInt/MappedDecimal read a NULL column as the field's declared defaultValue, never as a
  // failure: the amount fields declared BigDecimal(0) and the count fields -1. A row written
  // before one of these columns existed holds NULL, so a bare Int here fails the whole query.
  private val noTransactionLimit = -1
  private val noAmountLimit = BigDecimal(0)

  private def rowOf(r: (Option[String], String, String, String, String, Option[String],
    Option[BigDecimal], Option[BigDecimal], Option[Int], Option[BigDecimal], Option[Int],
    Option[BigDecimal], Option[Int])): CounterpartyLimitRow =
    CounterpartyLimitRow(
      counterpartyLimitId = r._1.orNull,
      bankId = r._2,
      accountId = r._3,
      viewId = r._4,
      counterpartyId = r._5,
      currency = r._6.orNull,
      maxSingleAmount = r._7.getOrElse(noAmountLimit),
      maxMonthlyAmount = r._8.getOrElse(noAmountLimit),
      maxNumberOfMonthlyTransactions = r._9.getOrElse(noTransactionLimit),
      maxYearlyAmount = r._10.getOrElse(noAmountLimit),
      maxNumberOfYearlyTransactions = r._11.getOrElse(noTransactionLimit),
      maxTotalAmount = r._12.getOrElse(noAmountLimit),
      maxNumberOfTransactions = r._13.getOrElse(noTransactionLimit)
    )

  private val selectCols: Fragment =
    fr"""SELECT counterpartylimitid, bankid, accountid, viewid, counterpartyid, currency,
                maxsingleamount, maxmonthlyamount, maxnumberofmonthlytransactions,
                maxyearlyamount, maxnumberofyearlytransactions, maxtotalamount, maxnumberoftransactions
         FROM counterpartylimit"""

  private type Row = (Option[String], String, String, String, String, Option[String],
    Option[BigDecimal], Option[BigDecimal], Option[Int], Option[BigDecimal], Option[Int],
    Option[BigDecimal], Option[Int])

  private def find(bankId: String, accountId: String, viewId: String, counterpartyId: String): Option[Row] =
    DoobieUtil.runQuery(
      (selectCols ++
        fr"WHERE bankid = $bankId AND accountid = $accountId AND viewid = $viewId AND counterpartyid = $counterpartyId LIMIT 1")
        .query[Row].option
    )

  override def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[CounterpartyLimitTrait]] = Future {
    find(bankId, accountId, viewId, counterpartyId) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }
  }

  override def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[Boolean]] = Future {
    find(bankId, accountId, viewId, counterpartyId) match {
      case Some(_) =>
        DoobieUtil.runUpdate(
          sql"""DELETE FROM counterpartylimit
                WHERE bankid = $bankId AND accountid = $accountId AND viewid = $viewId AND counterpartyid = $counterpartyId"""
            .update.run)
        Full(true)
      case None => Empty
    }
  }

  override def createOrUpdateCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    currency: String,
    maxSingleAmount: BigDecimal,
    maxMonthlyAmount: BigDecimal,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: BigDecimal,
    maxNumberOfYearlyTransactions: Int,
    maxTotalAmount: BigDecimal,
    maxNumberOfTransactions: Int
  ): Future[Box[CounterpartyLimitTrait]] = Future {
    tryo {
      find(bankId, accountId, viewId, counterpartyId) match {
        case Some((existingId, _, _, _, _, _, _, _, _, _, _, _, _)) =>
          DoobieUtil.runUpdate(
            sql"""UPDATE counterpartylimit
                  SET currency = $currency, maxsingleamount = $maxSingleAmount, maxmonthlyamount = $maxMonthlyAmount,
                      maxnumberofmonthlytransactions = $maxNumberOfMonthlyTransactions, maxyearlyamount = $maxYearlyAmount,
                      maxnumberofyearlytransactions = $maxNumberOfYearlyTransactions, maxtotalamount = $maxTotalAmount,
                      maxnumberoftransactions = $maxNumberOfTransactions, updatedat = CURRENT_TIMESTAMP
                  WHERE bankid = $bankId AND accountid = $accountId AND viewid = $viewId AND counterpartyid = $counterpartyId"""
              .update.run)
          CounterpartyLimitRow(existingId.orNull, bankId, accountId, viewId, counterpartyId,
            currency,
            maxSingleAmount, maxMonthlyAmount, maxNumberOfMonthlyTransactions,
            maxYearlyAmount, maxNumberOfYearlyTransactions, maxTotalAmount, maxNumberOfTransactions)
        case None =>
          val id = APIUtil.generateUUID()
          DoobieUtil.runUpdate(
            sql"""INSERT INTO counterpartylimit
                    (counterpartylimitid, bankid, accountid, viewid, counterpartyid, currency,
                     maxsingleamount, maxmonthlyamount, maxnumberofmonthlytransactions,
                     maxyearlyamount, maxnumberofyearlytransactions, maxtotalamount, maxnumberoftransactions,
                     createdat, updatedat)
                  VALUES ($id, $bankId, $accountId, $viewId, $counterpartyId, $currency,
                          $maxSingleAmount, $maxMonthlyAmount, $maxNumberOfMonthlyTransactions,
                          $maxYearlyAmount, $maxNumberOfYearlyTransactions, $maxTotalAmount, $maxNumberOfTransactions,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
              .update.run)
          CounterpartyLimitRow(id, bankId, accountId, viewId, counterpartyId, currency,
            maxSingleAmount, maxMonthlyAmount, maxNumberOfMonthlyTransactions,
            maxYearlyAmount, maxNumberOfYearlyTransactions, maxTotalAmount, maxNumberOfTransactions)
      }
    }
  }
}
