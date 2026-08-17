package code.opencorridorfees

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * Platform fee accrual ledger for Open Corridor (design: WIP/NEXT_TODO.md
 * 2026-08-12, Simon).
 *
 * Fee policy: ORIGINATOR PAYS. When a netting cycle covers a promise, the bank
 * that originated it owes the platform the charge already stamped on the TR
 * (props `transactionRequests_charge_level_OPEN_CORRIDOR_PROMISE` at create
 * time). One row per covered promise, written in the settle transaction —
 * this table IS the billing feed. Promises with `return_of` set accrue
 * nothing: returns are involuntary corridor housekeeping originated by the
 * beneficiary bank.
 *
 * Collection is decoupled from the corridor's settlement rail: the fee sweep
 * (see `OpenCorridorFees.sweep`) sums a bank's unswept rows per currency and
 * enqueues one `obp_settlement_instruction` with `purpose = PLATFORM_FEE`,
 * creditor = the platform's incoming settlement account. `FeeSettlementId`
 * marks a row swept; NULL rows are the bank's open fee balance.
 */
trait OpenCorridorFeeAccrualTrait {
  def debtorBankId: String
  def transactionRequestId: String
  def currency: String
  def amount: String
  def feeSettlementId: String
}

case class OpenCorridorFeeAccrualRow(
  debtorBankId: String,
  transactionRequestId: String,
  currency: String,
  amount: String,
  feeSettlementId: String
) extends OpenCorridorFeeAccrualTrait

object OpenCorridorFeeAccrual {

  private val selectColumns =
    fr"SELECT debtor_bank_id, transaction_request_id, currency, amount, fee_settlement_id FROM open_corridor_fee_accrual"

  private def fromRow(row: (String, String, String, String, String)): OpenCorridorFeeAccrualTrait =
    row match {
      case (debtorBankId, transactionRequestId, currency, amount, feeSettlementId) =>
        OpenCorridorFeeAccrualRow(debtorBankId, transactionRequestId, currency, amount, feeSettlementId)
    }

  /** Accrue the fee for one covered promise. Idempotent on the TR id (a
    * re-settle of the same promise cannot double-charge); zero/empty charges
    * accrue nothing. */
  def accrue(
    debtorBankId: String,
    transactionRequestId: String,
    currency: String,
    amount: String,
    coveredBySettlementId: String
  ): Option[OpenCorridorFeeAccrualTrait] = {
    val zero = scala.util.Try(BigDecimal(amount)).map(_ <= 0).getOrElse(true)
    val alreadyAccrued = find(transactionRequestId).isDefined
    if (zero || alreadyAccrued) None
    else {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO open_corridor_fee_accrual
              (debtor_bank_id, transaction_request_id, currency, amount, covered_by_settlement_id, fee_settlement_id, accrued_at)
              VALUES
              ($debtorBankId, $transactionRequestId, $currency, $amount, $coveredBySettlementId, '', CURRENT_TIMESTAMP)"""
          .update.run)
      Some(OpenCorridorFeeAccrualRow(debtorBankId, transactionRequestId, currency, amount, ""))
    }
  }

  /** A bank's unswept accruals in one currency, oldest first. (MappedString
    * defaults to the empty string, so "unswept" is an empty FeeSettlementId.) */
  def unswept(debtorBankId: String, currency: String): List[OpenCorridorFeeAccrualTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE debtor_bank_id = $debtorBankId AND currency = $currency AND fee_settlement_id = '' ORDER BY accrued_at ASC")
        .query[(String, String, String, String, String)].to[List]
    ).map(fromRow)

  def find(transactionRequestId: String): Box[OpenCorridorFeeAccrualTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE transaction_request_id = $transactionRequestId")
        .query[(String, String, String, String, String)].option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

  /** Stamp one accrued fee as swept. */
  def markSwept(transactionRequestId: String, feeSettlementId: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE open_corridor_fee_accrual SET fee_settlement_id = $feeSettlementId
            WHERE transaction_request_id = $transactionRequestId"""
        .update.run)
    ()
  }
}
