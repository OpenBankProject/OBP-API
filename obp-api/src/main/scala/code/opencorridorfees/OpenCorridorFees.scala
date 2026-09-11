package code.opencorridorfees

import code.amqpbroker.AmqpBankBroker
import code.api.Constant.INCOMING_SETTLEMENT_ACCOUNT_ID
import code.api.util.APIUtil.generateUUID
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext, NewStyle}
import code.messageoutbox.MessageOutbox
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.OutBoundOpenCorridorSettlementInstruction
import com.openbankproject.commons.model.{AccountId, BankId}
import org.json4s.NoTypeHints
import org.json4s.native.Serialization

import scala.concurrent.Future

/** Result of a fee sweep, JSON-shaped for the v7 endpoint. */
case class OpenCorridorFeeSweepResultJsonV700(
  fee_settlement_id: String,
  debtor_bank_id: String,
  platform_bank_id: String,
  currency: String,
  amount: String,
  accruals_swept: Int,
  settlement_instructions_enqueued: Int
)

/**
 * The platform fee sweep (design: OBP-Bank-Node WIP/NEXT_TODO.md 2026-08-12):
 * collect a bank's accrued platform fees in ADA, decoupled from the corridor's
 * settlement rail.
 *
 * Sums the bank's unswept accruals in one currency and enqueues ONE
 * `obp_settlement_instruction` with `purpose = PLATFORM_FEE` to the bank's
 * vhost: creditor = the PLATFORM's incoming settlement account (its CARDANO
 * routing), `settlement_system = cardano-ada` regardless of the corridor's
 * rail — every node runs an ADA wallet for promise commitments anyway. The
 * node executes it exactly like a corridor settlement (idempotency, settle-
 * time FX, finality); the outbox row's redelivery doubles as the status poll.
 *
 * The platform is modelled AS a bank (props `open_corridor.platform_bank_id`)
 * so address resolution, rotation, and audit reuse the settlement-account
 * machinery.
 */
object OpenCorridorFees extends MdcLoggable {

  private implicit val formats: org.json4s.Formats = Serialization.formats(NoTypeHints)

  def sweep(
    debtorBankId: String,
    currency: String,
    callContext: Option[CallContext]
  ): Future[(OpenCorridorFeeSweepResultJsonV700, Option[CallContext])] = {
    val platformBankId = APIUtil.getPropsValue("open_corridor.platform_bank_id", "")
    for {
      _ <- Helper.booleanToFuture(
        s"$InvalidJsonValue open_corridor.platform_bank_id is not configured on this instance",
        cc = callContext) {
        platformBankId.trim.nonEmpty
      }
      // The fee is paid BY the debtor bank's node — it must be reachable.
      _ <- Helper.booleanToFuture(s"$AmqpBankBrokerNotConfigured BANK_ID: $debtorBankId", cc = callContext) {
        AmqpBankBroker.findByBankId(debtorBankId).isDefined
      }
      // The platform's receiving address: the CARDANO routing on its incoming
      // settlement account — same resolution as any corridor creditor.
      (platformIncoming, callContext) <- NewStyle.function.getBankAccount(
        BankId(platformBankId), AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext)
      platformAddress = platformIncoming.accountRoutings
        .find(_.scheme.equalsIgnoreCase("CARDANO")).map(_.address).getOrElse("")
      _ <- Helper.booleanToFuture(
        s"$OpenCorridorSettlementAddressMissing BANK_ID: $platformBankId", cc = callContext) {
        platformAddress.trim.nonEmpty
      }
      result <- Future {
        val accruals = OpenCorridorFeeAccrual.unswept(debtorBankId, currency)
        if (accruals.isEmpty) {
          // No-op sweep: nothing owed in this currency.
          OpenCorridorFeeSweepResultJsonV700("", debtorBankId, platformBankId, currency, "0", 0, 0)
        } else {
          val total = accruals.map(a => BigDecimal(a.amount)).sum
          val feeSettlementId = generateUUID()
          val instruction = OutBoundOpenCorridorSettlementInstruction(
            snapshot_id = None,
            settlement_id = feeSettlementId,
            settlement_system = "cardano-ada",
            currency = currency,
            amount = total.toString(),
            creditor_bank_id = platformBankId,
            creditor_address = platformAddress,
            idempotency_key = feeSettlementId,
            purpose = Some("PLATFORM_FEE")
          )
          MessageOutbox.enqueue(
            MessageOutbox.TYPE_OPEN_CORRIDOR, feeSettlementId,
            MessageOutbox.SUBJECT_TYPE_SETTLEMENT_ID,
            "obp_settlement_instruction", debtorBankId, Serialization.write(instruction))
          accruals.foreach(a => OpenCorridorFeeAccrual.markSwept(a.transactionRequestId, feeSettlementId))
          logger.info(s"Open Corridor fee sweep: $debtorBankId owes $total $currency " +
            s"(${accruals.size} accruals) -> platform $platformBankId, fee settlement $feeSettlementId")
          OpenCorridorFeeSweepResultJsonV700(
            feeSettlementId, debtorBankId, platformBankId, currency,
            total.toString(), accruals.size, 1)
        }
      }
    } yield (result, callContext)
  }
}
