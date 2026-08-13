package code.opencorridorfees

import net.liftweb.mapper._

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
class OpenCorridorFeeAccrual extends LongKeyedMapper[OpenCorridorFeeAccrual] with IdPK {
  def getSingleton = OpenCorridorFeeAccrual

  /** The bank that OWES the fee — the promise's originating (from) bank. */
  object DebtorBankId extends MappedString(this, 255) {
    override def dbColumnName = "debtor_bank_id"
  }
  /** The covered promise this fee is for. Unique — accrual is idempotent. */
  object TransactionRequestId extends MappedString(this, 64) {
    override def dbColumnName = "transaction_request_id"
  }
  object Currency extends MappedString(this, 8) {
    override def dbColumnName = "currency"
  }
  /** The TR's charge amount, verbatim (major units, decimal string). */
  object Amount extends MappedString(this, 32) {
    override def dbColumnName = "amount"
  }
  /** The settlement that made the fee due (the netting cycle's id). */
  object CoveredBySettlementId extends MappedString(this, 64) {
    override def dbColumnName = "covered_by_settlement_id"
  }
  /** NULL until swept; then the fee settlement's id. */
  object FeeSettlementId extends MappedString(this, 64) {
    override def dbColumnName = "fee_settlement_id"
  }
  object AccruedAt extends MappedDateTime(this) {
    override def dbColumnName = "accrued_at"
    override def defaultValue = new java.util.Date()
  }

  def debtorBankId: String = DebtorBankId.get
  def transactionRequestId: String = TransactionRequestId.get
  def currency: String = Currency.get
  def amount: String = Amount.get
  def feeSettlementId: String = FeeSettlementId.get
}

object OpenCorridorFeeAccrual
  extends OpenCorridorFeeAccrual
  with LongKeyedMetaMapper[OpenCorridorFeeAccrual] {

  override def dbTableName = "open_corridor_fee_accrual"

  override def dbIndexes: List[BaseIndex[OpenCorridorFeeAccrual]] =
    UniqueIndex(TransactionRequestId) :: Index(DebtorBankId) ::
      Index(FeeSettlementId) :: super.dbIndexes

  /** Accrue the fee for one covered promise. Idempotent on the TR id (a
    * re-settle of the same promise cannot double-charge); zero/empty charges
    * accrue nothing. */
  def accrue(
    debtorBankId: String,
    transactionRequestId: String,
    currency: String,
    amount: String,
    coveredBySettlementId: String
  ): Option[OpenCorridorFeeAccrual] = {
    val zero = scala.util.Try(BigDecimal(amount)).map(_ <= 0).getOrElse(true)
    if (zero) None
    else if (find(By(TransactionRequestId, transactionRequestId)).isDefined) None
    else Some(
      OpenCorridorFeeAccrual.create
        .DebtorBankId(debtorBankId)
        .TransactionRequestId(transactionRequestId)
        .Currency(currency)
        .Amount(amount)
        .CoveredBySettlementId(coveredBySettlementId)
        .saveMe()
    )
  }

  /** A bank's unswept accruals in one currency, oldest first. (MappedString
    * defaults to the empty string, so "unswept" is an empty FeeSettlementId.) */
  def unswept(debtorBankId: String, currency: String): List[OpenCorridorFeeAccrual] =
    findAll(
      By(DebtorBankId, debtorBankId),
      By(Currency, currency),
      By(FeeSettlementId, ""),
      OrderBy(AccruedAt, Ascending)
    )
}
