package code.bankconnectors.opencorridor

import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import code.api.util.APIUtil.generateUUID
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, NewStyle}
import code.api.v7_0_0.JSONFactory700.OpenCorridorSettleResultJsonV700
import code.bankconnectors.DoobieTransactionRequestQueries
import code.transactionrequests.{MappedTransactionRequest, TransactionRequests}
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{TransactionRequestAttributeType, TransactionRequestStatus, TransactionRequestTypes}
import net.liftweb.common.Full
import net.liftweb.mapper.By
import org.json4s.NoTypeHints
import org.json4s.native.Serialization

import scala.concurrent.Future

/**
 * The Open Corridor settle-pair step (OPEN_CORRIDOR_SIMPLE_NETTING.md §4/§6.4,
 * publish plan §5.3): bilateral, settle-on-demand netting.
 *
 *   net = SUM(PENDING A→B promises) − SUM(PENDING B→A promises)
 *
 * One internal OPEN_CORRIDOR_SETTLEMENT TransactionRequest ("TR B") is minted
 * between the pair's settlement accounts and executed, posting ONE Transaction
 * for abs(net) (debtor's OUTGOING settlement account → creditor's INCOMING).
 * Every covered promise TR gets:
 *   - `settled_by_transaction_ids` attribute = the net Transaction's id (only
 *     when a Transaction posted — a zero net discharges without one),
 *   - `settled_by_transaction_request_id` attribute = TR B's id (always, so the
 *     settle event is traceable even at net zero),
 * and flips PENDING → COMPLETED.
 *
 * The outbound Interface C messages (credit notification per covered promise to
 * its beneficiary bank, the net settlement instruction to the debtor) are
 * written to the OpenCorridorOutbox in the SAME request DB transaction — the
 * ResourceDocMiddleware transaction wrapper makes the whole settle atomic: a
 * crash rolls back money movement and outbox rows together.
 *
 * Idempotency / concurrency: every covered promise TR row is row-locked
 * (SELECT ... FOR UPDATE via DoobieTransactionRequestQueries) and its status
 * re-read under the lock, so a concurrent double-trigger cannot settle the same
 * promises twice; a re-trigger with nothing PENDING is a no-op. TR B's id is
 * the stable settlement_id and the Bank Node `idempotency_key`.
 */
object OpenCorridorSettlement extends MdcLoggable {

  val AttrSettledByTransactionIds = "settled_by_transaction_ids"
  val AttrSettledByTransactionRequestId = "settled_by_transaction_request_id"

  private implicit val wireFormats = Serialization.formats(NoTypeHints)

  def settlePair(
    user: User,
    bankIdA: String,
    bankIdB: String,
    currency: String,
    callContext: Option[CallContext]
  ): Future[(OpenCorridorSettleResultJsonV700, Option[CallContext])] = {

    def pendingPromises(fromBank: String, toBank: String): List[MappedTransactionRequest] =
      MappedTransactionRequest.findAll(
        By(MappedTransactionRequest.mType, TransactionRequestTypes.OPEN_CORRIDOR_PROMISE.toString),
        By(MappedTransactionRequest.mStatus, TransactionRequestStatus.PENDING.toString),
        By(MappedTransactionRequest.mFrom_BankId, fromBank),
        By(MappedTransactionRequest.mTo_BankId, toBank),
        By(MappedTransactionRequest.mBody_Value_Currency, currency)
      )

    for {
      // Candidate discovery, then row-lock each candidate and re-read its status
      // under the lock — a concurrent settle may have completed it in between.
      candidates <- Future {
        pendingPromises(bankIdA, bankIdB) ++ pendingPromises(bankIdB, bankIdA)
      }
      covered <- Future {
        candidates.flatMap { row =>
          val trId = row.mTransactionRequestId.get
          if (DoobieTransactionRequestQueries.lockTransactionRequest(trId).isEmpty) {
            logger.warn(s"Open Corridor settle: could not lock promise TR $trId — skipping")
            None
          } else {
            MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, trId))
              .filter(_.mStatus.get == TransactionRequestStatus.PENDING.toString)
          }
        }
      }
      result <-
        if (covered.isEmpty) {
          // No-op re-trigger: nothing pending for the pair.
          Future.successful((OpenCorridorSettleResultJsonV700(
            settlement_id = "",
            settlement_transaction_request_id = "",
            transaction_id = "",
            debtor_bank_id = "",
            creditor_bank_id = "",
            currency = currency,
            net_amount = "0",
            covered_transaction_request_ids = Nil,
            credit_notifications_enqueued = 0,
            settlement_instructions_enqueued = 0
          ), callContext))
        } else {
          executeSettle(user, bankIdA, bankIdB, currency, covered, callContext)
        }
    } yield result
  }

  private def executeSettle(
    user: User,
    bankIdA: String,
    bankIdB: String,
    currency: String,
    covered: List[MappedTransactionRequest],
    callContext: Option[CallContext]
  ): Future[(OpenCorridorSettleResultJsonV700, Option[CallContext])] = {

    val aToB = covered.filter(_.mFrom_BankId.get == bankIdA)
    val bToA = covered.filter(_.mFrom_BankId.get == bankIdB)

    val sumAToB = aToB.map(row => BigDecimal(row.mBody_Value_Amount.get)).sum
    val sumBToA = bToA.map(row => BigDecimal(row.mBody_Value_Amount.get)).sum
    val net = sumAToB - sumBToA

    val (debtorBankId, creditorBankId) = if (net >= 0) (bankIdA, bankIdB) else (bankIdB, bankIdA)
    val netAbs = net.abs

    for {
      // Fail fast BEFORE mutating anything: both banks need a broker registration
      // (credit notifications go to each beneficiary's vhost), and a non-zero net
      // needs the creditor's settlement address for the instruction.
      _ <- Helper.booleanToFuture(s"$OpenCorridorBankBrokerNotConfigured BANK_ID: $bankIdA", cc = callContext) {
        OpenCorridorBankBroker.findByBankId(bankIdA).isDefined
      }
      _ <- Helper.booleanToFuture(s"$OpenCorridorBankBrokerNotConfigured BANK_ID: $bankIdB", cc = callContext) {
        OpenCorridorBankBroker.findByBankId(bankIdB).isDefined
      }
      creditorSettlementAddress = OpenCorridorBankBroker.findByBankId(creditorBankId)
        .map(_.settlementAddress).getOrElse("")
      _ <- Helper.booleanToFuture(s"$OpenCorridorSettlementAddressMissing BANK_ID: $creditorBankId", cc = callContext) {
        netAbs == 0 || creditorSettlementAddress.trim.nonEmpty
      }

      // The settlement accounts (created at boot for every bank).
      (debtorOutgoing, callContext) <- NewStyle.function.getBankAccount(
        BankId(debtorBankId), AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), callContext)
      (creditorIncoming, callContext) <- NewStyle.function.getBankAccount(
        BankId(creditorBankId), AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext)

      // Mint TR B — the settle event's audit object and the settlement_id.
      settlementTrId = generateUUID()
      commonBody = TransactionRequestCommonBodyJSONCommons(
        AmountOfMoneyJsonV121(currency, netAbs.toString()),
        s"Open Corridor net settlement $debtorBankId -> $creditorBankId ($currency), covering ${covered.size} promise(s)"
      )
      settlementTr <- Future {
        TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(
          TransactionRequestId(settlementTrId),
          TransactionRequestType(TransactionRequestTypes.OPEN_CORRIDOR_SETTLEMENT.toString),
          debtorOutgoing,
          creditorIncoming,
          commonBody,
          Serialization.write(commonBody),
          TransactionRequestStatus.PENDING.toString,
          TransactionRequestCharge("Open Corridor settlement", AmountOfMoney(currency, "0.00")),
          "SHARED",
          None, None, None, None,
          callContext
        )
      } map {
        _.toOption.getOrElse(
          throw new RuntimeException(s"$UnknownError could not create the OPEN_CORRIDOR_SETTLEMENT Transaction Request"))
      }

      // Execute TR B: post the ONE net Transaction (unless the net is zero —
      // flows offset exactly, promises discharge with nothing moving).
      netTransactionId <-
        if (netAbs > 0) {
          for {
            (transactionId, _) <- NewStyle.function.makePaymentv210(
              debtorOutgoing,
              creditorIncoming,
              TransactionRequestId(settlementTrId),
              commonBody,
              netAbs,
              commonBody.description,
              TransactionRequestType(TransactionRequestTypes.OPEN_CORRIDOR_SETTLEMENT.toString),
              "SHARED",
              callContext
            )
            _ <- Future(TransactionRequests.transactionRequestProvider.vend
              .saveTransactionRequestTransactionImpl(TransactionRequestId(settlementTrId), transactionId))
          } yield transactionId.value
        } else Future.successful("")
      _ <- Future(TransactionRequests.transactionRequestProvider.vend
        .saveTransactionRequestStatusImpl(TransactionRequestId(settlementTrId), TransactionRequestStatus.COMPLETED.toString))

      // Discharge every covered promise: linkage attributes + PENDING → COMPLETED.
      _ <- Future.sequence(covered.map { row =>
        val promiseTrId = TransactionRequestId(row.mTransactionRequestId.get)
        val linkage =
          TransactionRequestAttributeJsonV400(AttrSettledByTransactionRequestId, TransactionRequestAttributeType.STRING.toString, settlementTrId) ::
          (if (netTransactionId.nonEmpty)
            List(TransactionRequestAttributeJsonV400(AttrSettledByTransactionIds, TransactionRequestAttributeType.STRING.toString, netTransactionId))
          else Nil)
        for {
          (_, _) <- NewStyle.function.createTransactionRequestAttributes(
            BankId(row.mFrom_BankId.get), promiseTrId, linkage, isPersonal = false, callContext)
          _ <- Future(TransactionRequests.transactionRequestProvider.vend
            .saveTransactionRequestStatusImpl(promiseTrId, TransactionRequestStatus.COMPLETED.toString))
        } yield ()
      })

      // Enqueue the Interface C messages in this same DB transaction (the outbox).
      creditNotifications <- Future.sequence(covered.map(row => buildCreditNotification(row, callContext)))
      _ <- Future {
        creditNotifications.foreach { case (beneficiaryBankId, wireBody) =>
          OpenCorridorOutbox.enqueue(
            settlementTrId, "obp_credit_notification", beneficiaryBankId, Serialization.write(wireBody))
        }
      }
      settlementInstructionCount <- Future {
        if (netAbs > 0) {
          val instruction = OutBoundOpenCorridorSettlementInstruction(
            snapshot_id = None,
            settlement_id = settlementTrId,
            settlement_system = code.api.util.APIUtil.getPropsValue("open_corridor.settlement_system", "cardano-ada"),
            currency = currency,
            amount = netAbs.toString(),
            creditor_bank_id = creditorBankId,
            creditor_address = creditorSettlementAddress,
            idempotency_key = settlementTrId
          )
          OpenCorridorOutbox.enqueue(
            settlementTrId, "obp_settlement_instruction", debtorBankId, Serialization.write(instruction))
          1
        } else 0
      }
    } yield {
      logger.info(s"Open Corridor settled pair ($bankIdA, $bankIdB) $currency: net $net, " +
        s"debtor $debtorBankId, creditor $creditorBankId, ${covered.size} promise(s), " +
        s"settlement TR $settlementTrId, transaction '${netTransactionId}'")
      (OpenCorridorSettleResultJsonV700(
        settlement_id = settlementTrId,
        settlement_transaction_request_id = settlementTrId,
        transaction_id = netTransactionId,
        debtor_bank_id = debtorBankId,
        creditor_bank_id = creditorBankId,
        currency = currency,
        net_amount = netAbs.toString(),
        covered_transaction_request_ids = covered.map(_.mTransactionRequestId.get),
        credit_notifications_enqueued = creditNotifications.size,
        settlement_instructions_enqueued = settlementInstructionCount
      ), callContext)
    }
  }

  /** Build the credit notification for one covered promise, addressed to its
    * beneficiary (to-side) bank, relaying the §5.1 evidence attributes verbatim. */
  private def buildCreditNotification(
    row: MappedTransactionRequest,
    callContext: Option[CallContext]
  ): Future[(String, OutBoundOpenCorridorCreditNotification)] = {
    val promiseTrId = TransactionRequestId(row.mTransactionRequestId.get)
    for {
      (attributes, _) <- NewStyle.function.getTransactionRequestAttributes(
        BankId(row.mFrom_BankId.get), promiseTrId, callContext)
    } yield {
      def attr(name: String): Option[String] =
        attributes.find(_.name == name).map(_.value).filter(_.nonEmpty)
      val wireBody = OutBoundOpenCorridorCreditNotification(
        transaction_request_id = promiseTrId.value,
        value = OpenCorridorMoneyValue(row.mBody_Value_Currency.get, row.mBody_Value_Amount.get),
        description = Option(row.mBody_Description.get).filter(_.nonEmpty),
        originator = Option(row.mOriginator_Name.get).filter(_.nonEmpty).map(name =>
          OpenCorridorOriginator(name, Option(row.mOriginator_Address.get).filter(_.nonEmpty))),
        netting_snapshot_id = None,
        promise_id = attr(OpenCorridorProcessor.PromiseAttributeTxHash),
        promise_blockchain = attr(OpenCorridorProcessor.PromiseAttributeBlockchain),
        promise_commitment = attr(OpenCorridorProcessor.PromiseAttributeCommitment),
        promise_salt = attr(OpenCorridorProcessor.PromiseAttributeSalt),
        promise_preimage = attr(OpenCorridorProcessor.PromiseAttributePreimage)
      )
      (row.mTo_BankId.get, wireBody)
    }
  }
}
