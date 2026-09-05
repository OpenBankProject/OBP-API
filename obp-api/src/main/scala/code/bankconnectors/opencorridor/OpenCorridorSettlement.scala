package code.bankconnectors.opencorridor

import code.amqpbroker.AmqpBankBroker
import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import code.api.util.APIUtil.{generateUUID, unboxFullOrFail}
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, NewStyle}
import code.api.v7_0_0.JSONFactory700.{OpenCorridorSettleResultJsonV700, OpenCorridorSettlementMessageJsonV700, OpenCorridorSettlementStatusJsonV700}
import code.bankconnectors.DoobieTransactionRequestQueries
import code.messageoutbox.MessageOutbox
import code.transactionRequestAttribute.DoobieTransactionRequestAttributeProvider
import code.transactionrequests.{MappedTransactionRequest, TransactionRequests}
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{TransactionRequestAttributeType, TransactionRequestStatus, TransactionRequestTypes}
import net.liftweb.common.Full
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.json4s.jvalue2monadic

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
 * written to the message_outbox in the SAME request DB transaction — the
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

  private implicit val wireFormats: org.json4s.Formats = Serialization.formats(NoTypeHints)

  def settlePair(
    user: User,
    bankIdA: String,
    bankIdB: String,
    currency: String,
    callContext: Option[CallContext]
  ): Future[(OpenCorridorSettleResultJsonV700, Option[CallContext])] = {

    def pendingPromises(fromBank: String, toBank: String): List[MappedTransactionRequest] =
      MappedTransactionRequest.findAllByTypeStatusBanksAndCurrency(
        TransactionRequestTypes.OPEN_CORRIDOR_PROMISE.toString,
        TransactionRequestStatus.PENDING.toString, fromBank, toBank, currency)

    for {
      // Settlement is pairwise between two DIFFERENT banks; a same-bank pair
      // would fetch the same promise rows in both directions.
      _ <- Helper.booleanToFuture(s"$OpenCorridorSameBankNotAllowed", cc = callContext) {
        bankIdA != bankIdB
      }
      // Candidate discovery, then row-lock each candidate and re-read its status
      // under the lock — a concurrent settle may have completed it in between.
      candidates <- Future {
        pendingPromises(bankIdA, bankIdB) ++ pendingPromises(bankIdB, bankIdA)
      }
      covered <- Future {
        candidates.flatMap { row =>
          val trId = row.transactionRequestId
          if (DoobieTransactionRequestQueries.lockTransactionRequest(trId).isEmpty) {
            logger.warn(s"Open Corridor settle: could not lock promise TR $trId — skipping")
            None
          } else if (!hasPromiseEvidence(trId)) {
            // No on-chain evidence yet means the beneficiary bank was never
            // notified and never paid out — netting it would move value between
            // banks for a payment nobody delivered. It stays PENDING for a
            // later cycle, once the originating node reports back.
            logger.info(s"Open Corridor settle: promise TR $trId has no on-chain evidence yet — skipping")
            None
          } else {
            MappedTransactionRequest.findByTransactionRequestId(trId)
              .filter(_.status == TransactionRequestStatus.PENDING.toString)
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
            settlement_advices_enqueued = 0,
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

    val aToB = covered.filter(_.fromBankId == bankIdA)
    val bToA = covered.filter(_.fromBankId == bankIdB)

    val sumAToB = aToB.map(row => BigDecimal(row.bodyValueAmount)).sum
    val sumBToA = bToA.map(row => BigDecimal(row.bodyValueAmount)).sum
    val net = sumAToB - sumBToA

    val (debtorBankId, creditorBankId) = if (net >= 0) (bankIdA, bankIdB) else (bankIdB, bankIdA)
    val netAbs = net.abs

    for {
      // Fail fast BEFORE mutating anything: both banks need a broker registration
      // (credit notifications go to each beneficiary's vhost), and a non-zero net
      // needs the creditor's settlement address for the instruction.
      _ <- Helper.booleanToFuture(s"$AmqpBankBrokerNotConfigured BANK_ID: $bankIdA", cc = callContext) {
        AmqpBankBroker.findByBankId(bankIdA).isDefined
      }
      _ <- Helper.booleanToFuture(s"$AmqpBankBrokerNotConfigured BANK_ID: $bankIdB", cc = callContext) {
        AmqpBankBroker.findByBankId(bankIdB).isDefined
      }

      // The settlement accounts (created at boot for every bank).
      (debtorOutgoing, callContext) <- NewStyle.function.getBankAccount(
        BankId(debtorBankId), AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), callContext)
      (creditorIncoming, callContext) <- NewStyle.function.getBankAccount(
        BankId(creditorBankId), AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext)

      // The creditor's on-chain receiving address is the CARDANO routing on its
      // incoming settlement account (the broker row is transport only). Checked
      // before anything is mutated: a non-zero net needs somewhere to send funds.
      creditorSettlementAddress = creditorIncoming.accountRoutings
        .find(_.scheme.equalsIgnoreCase("CARDANO")).map(_.address).getOrElse("")
      _ <- Helper.booleanToFuture(s"$OpenCorridorSettlementAddressMissing BANK_ID: $creditorBankId", cc = callContext) {
        netAbs == 0 || creditorSettlementAddress.trim.nonEmpty
      }

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
        val promiseTrId = TransactionRequestId(row.transactionRequestId)
        val linkage =
          TransactionRequestAttributeJsonV400(AttrSettledByTransactionRequestId, TransactionRequestAttributeType.STRING.toString, settlementTrId) ::
          (if (netTransactionId.nonEmpty)
            List(TransactionRequestAttributeJsonV400(AttrSettledByTransactionIds, TransactionRequestAttributeType.STRING.toString, netTransactionId))
          else Nil)
        for {
          (_, _) <- NewStyle.function.createTransactionRequestAttributes(
            BankId(row.fromBankId), promiseTrId, linkage, isPersonal = false, callContext)
          _ <- Future(TransactionRequests.transactionRequestProvider.vend
            .saveTransactionRequestStatusImpl(promiseTrId, TransactionRequestStatus.COMPLETED.toString))
        } yield ()
      })

      // Accrue the platform fee for each covered promise (originator pays;
      // the charge was stamped on the TR at create time). Returns are
      // fee-exempt: involuntary housekeeping originated by the beneficiary
      // bank. Idempotent per TR id — a re-settle cannot double-charge.
      _ <- Future {
        covered.foreach { row =>
          val isReturn = scala.util.Try(org.json4s.native.JsonMethods.parse(row.details))
            .toOption.exists(_ \ "return_of" match {
              case org.json4s.JString(s) => s.trim.nonEmpty
              case _ => false
            })
          if (!isReturn) {
            code.opencorridorfees.OpenCorridorFeeAccrual.accrue(
              debtorBankId = row.fromBankId,
              transactionRequestId = row.transactionRequestId,
              currency = row.chargeCurrency,
              amount = row.chargeAmount,
              coveredBySettlementId = settlementTrId
            )
          }
        }
      }

      // Enqueue the Interface C messages in this same DB transaction (the outbox).
      // Credit notifications went to each beneficiary at promise-report-back time
      // (OpenCorridorProcessor); settlement sends BOTH party banks an advice with
      // the FULL covered list, so each node stamps its already-paid-out credits
      // AND its own outbound promises settled — including the party that did not
      // trigger the settle, which otherwise never learns of the coverage (the
      // instruction only moves money, and at net zero it is not sent at all).
      settlementAdviceCount <- Future {
        val advice = OutBoundOpenCorridorSettlementAdvice(
          settlement_id = settlementTrId,
          currency = currency,
          net_amount = netAbs.toString(),
          debtor_bank_id = debtorBankId,
          creditor_bank_id = creditorBankId,
          covered_transaction_request_ids = covered.map(_.transactionRequestId),
          idempotency_key = settlementTrId
        )
        Set(bankIdA, bankIdB).map { partyBankId =>
          MessageOutbox.enqueue(
            MessageOutbox.TYPE_OPEN_CORRIDOR, settlementTrId, MessageOutbox.SUBJECT_TYPE_SETTLEMENT_ID,
            "obp_settlement_advice", partyBankId, Serialization.write(advice))
        }.size
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
          MessageOutbox.enqueue(
            MessageOutbox.TYPE_OPEN_CORRIDOR, settlementTrId, MessageOutbox.SUBJECT_TYPE_SETTLEMENT_ID,
            "obp_settlement_instruction", debtorBankId, Serialization.write(instruction))
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
        covered_transaction_request_ids = covered.map(_.transactionRequestId),
        settlement_advices_enqueued = settlementAdviceCount,
        settlement_instructions_enqueued = settlementInstructionCount
      ), callContext)
    }
  }

  /** True once the promise's on-chain evidence was attached (report-back done) —
    * the precondition for the beneficiary having been notified and paid out. */
  private def hasPromiseEvidence(trId: String): Boolean =
    DoobieTransactionRequestAttributeProvider.existsByNameAndTransactionRequestIdSync(
      OpenCorridorProcessor.PromiseAttributeCommitment, trId)

  /**
   * The GET view of one settlement (the resource minted by settlePair).
   *
   * Visibility: the URL bank must be a party — debtor or creditor — of the
   * settlement; anything else is a 404 (existence is not disclosed to third
   * banks). The ledger side is final at settle time; the rail side is read off
   * the settlement-instruction outbox row, whose LastReplyJson holds the debtor
   * node's most recent §4.2 reply (redelivery-as-polling, publish plan §4.4):
   *   no instruction row                    → NET_ZERO (nothing to move)
   *   row PENDING, no reply yet             → INSTRUCTED
   *   row PENDING, node replied             → the node's reported status
   *                                           (SETTLING / SUBMITTED) + depth
   *   row DELIVERED                         → FINAL
   *   row STICKY                            → ERROR (operator reconciliation)
   */
  def getSettlementStatus(
    bankId: String,
    settlementId: String,
    callContext: Option[CallContext]
  ): Future[(OpenCorridorSettlementStatusJsonV700, Option[CallContext])] = Future {
    val settlementTr = unboxFullOrFail(
      MappedTransactionRequest.findByTransactionRequestId(settlementId)
        .filter(_.transactionType == TransactionRequestTypes.OPEN_CORRIDOR_SETTLEMENT.toString)
        .filter(row => row.fromBankId == bankId || row.toBankId == bankId),
      callContext, OpenCorridorSettlementNotFound, 404)

    val outboxRows = MessageOutbox.bySubjectId(settlementId)
    val coveredTrIds = DoobieTransactionRequestAttributeProvider.transactionRequestIdsByNameAndValueSync(
      AttrSettledByTransactionRequestId, settlementId)

    val instructionRow = outboxRows.find(_.operationName == "obp_settlement_instruction")
    val (settlementStatus, settlementDepth) = instructionRow match {
      case None => ("NET_ZERO", None)
      case Some(row) => row.status match {
        case MessageOutbox.STATUS_DELIVERED => ("FINAL", nodeReportedDepth(row))
        case MessageOutbox.STATUS_STICKY    => ("ERROR", nodeReportedDepth(row))
        case _ => nodeReportedField(row, "status").filter(_.nonEmpty)
          .map(status => (status, nodeReportedDepth(row)))
          .getOrElse(("INSTRUCTED", None))
      }
    }

    (OpenCorridorSettlementStatusJsonV700(
      settlement_id = settlementId,
      debtor_bank_id = settlementTr.fromBankId,
      creditor_bank_id = settlementTr.toBankId,
      currency = settlementTr.bodyValueCurrency,
      net_amount = settlementTr.bodyValueAmount,
      transaction_id = settlementTr.transactionIds,
      ledger_status = settlementTr.status,
      settlement_status = settlementStatus,
      settlement_depth = settlementDepth,
      covered_transaction_request_ids = coveredTrIds,
      messages = outboxRows.map(row => OpenCorridorSettlementMessageJsonV700(
        operation_name = row.operationName,
        target_bank_id = row.targetId,
        delivery_status = row.status,
        attempts = row.attempts,
        last_error = row.lastError
      ))
    ), callContext)
  }

  /** Extract one field of the node's last reply (`data.<field>` of the §4.2
    * envelope recorded on the outbox row); None when no reply is recorded. */
  private def nodeReportedField(row: MessageOutbox, field: String): Option[String] = {
    Option(row.lastReplyJson).filter(_.nonEmpty).flatMap { replyJson =>
      scala.util.Try(org.json4s.native.JsonMethods.parse(replyJson) \ "data" \ field).toOption
    }.flatMap {
      case org.json4s.JString(s) => Some(s)
      case org.json4s.JInt(i)    => Some(i.toString)
      case _                     => None
    }
  }

  private def nodeReportedDepth(row: MessageOutbox): Option[Int] =
    nodeReportedField(row, "depth").flatMap(s => scala.util.Try(s.toInt).toOption)

  /** Build the credit notification for one covered promise, addressed to its
    * beneficiary (to-side) bank, relaying the §5.1 evidence attributes verbatim. */
}
