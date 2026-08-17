package code.messageoutbox

import code.actorsystem.ObpActorSystem
import code.bankconnectors.opencorridor.OpenCorridorPublisher
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Failure, Full}
import org.json4s._
import org.json4s.native.Serialization

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Publishes message_outbox rows and records the replies. Runs on the
 * actor-system scheduler (started from Boot), one pass per tick, rows
 * processed serially — throughput is not the concern here, at-least-once
 * delivery with a recorded audit trail is.
 *
 * The loop, backoff and PENDING/DELIVERED/STICKY supervision are generic;
 * each `outbox_type` contributes its publish + reply interpretation. A row
 * whose type has no registered publisher goes STICKY (an operator problem,
 * not a retry problem).
 *
 * OPEN_CORRIDOR reply handling (locked wire contract §4.2/§4.4):
 *  - transport failure / timeout / broker unregistered → row stays PENDING,
 *    attempts+1 (retried next tick; exponential backoff by attempts).
 *  - errorCode == "" → DELIVERED, except a settlement instruction, which is
 *    DELIVERED only when the bank reports status FINAL; SUBMITTED / SETTLING
 *    keep the row PENDING — redelivery IS the status poll, and the Bank Node
 *    never pays twice for the same idempotency_key.
 *  - OBP-BANK-NODE-SETTLEMENT-FAILED and CBS-DELIVERY-FAILED → stay PENDING:
 *    both are transient on the node side and redelivery is safe (idempotent
 *    verification; the CBS dedupes on transaction_request_id).
 *  - COMMITMENT-MISMATCH / BAD-MESSAGE / NOT-IMPLEMENTED /
 *    SETTLEMENT-NOT-CONFIGURED / CBS-REJECTED → STICKY: retry cannot fix it;
 *    it needs an operator (GET /management/message-outbox + /retry). The
 *    error and full reply are recorded — never swallowed. CBS-REJECTED is
 *    the bank's CBS refusing the credit itself (unknown account, name
 *    mismatch) — the asynchronous beneficiary refusal, distinct from the
 *    transient CBS-DELIVERY-FAILED.
 */
object MessageOutboxRelay extends MdcLoggable {

  private implicit val formats: org.json4s.Formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  /** Base backoff between attempts for a row; doubles per attempt, capped. */
  private val baseBackoff = 10.seconds
  private val maxBackoff = 10.minutes
  /** Cap on how long one row's publish may block the (serial) relay pass. */
  private val perRowTimeout = 60.seconds

  // OPEN_CORRIDOR errors retrying cannot fix. CBS-DELIVERY-FAILED is
  // deliberately NOT here: a CBS being down is transient, and with credit
  // notifications sent at promise time a sticky classification would park
  // every credit that hits a CBS blip. CBS-REJECTED IS here: the CBS
  // answered and refused the beneficiary — redelivering the same credit
  // cannot change its mind.
  private val openCorridorStickyErrorCodes = Set(
    "OBP-BANK-NODE-COMMITMENT-MISMATCH",
    "OBP-BANK-NODE-BAD-MESSAGE",
    "OBP-BANK-NODE-NOT-IMPLEMENTED",
    "OBP-BANK-NODE-SETTLEMENT-NOT-CONFIGURED",
    "OBP-BANK-NODE-CBS-REJECTED"
  )

  def start(intervalSeconds: Long): Unit = {
    implicit val executor = ObpActorSystem.localActorSystem.dispatcher
    ObpActorSystem.localActorSystem.scheduler.schedule(
      initialDelay = scala.concurrent.duration.Duration(intervalSeconds, TimeUnit.SECONDS),
      interval = scala.concurrent.duration.Duration(intervalSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit =
          try relayOnePass()
          catch { case e: Throwable => logger.error("message outbox relay pass failed", e) }
      }
    )
    logger.info(s"message outbox relay started (interval ${intervalSeconds}s)")
  }

  /** One pass over the PENDING rows that are due (backoff by attempts). */
  def relayOnePass(): Unit = {
    val now = System.currentTimeMillis()
    val due = MessageOutbox.pending().filter { row =>
      val backoff = (baseBackoff * math.pow(2, math.min(row.attempts, 6)).toLong).min(maxBackoff)
      row.updatedAt.getTime + backoff.toMillis <= now || row.attempts == 0
    }
    if (due.nonEmpty) logger.debug(s"message outbox relay: ${due.size} row(s) due")
    due.foreach(relayRow)
  }

  def relayRow(row: MessageOutbox): Unit = row.outboxType match {
    case MessageOutbox.TYPE_OPEN_CORRIDOR => relayOpenCorridorRow(row)
    case other =>
      MessageOutbox.markSticky(row.id, row.attempts + 1,
        s"no publisher registered for outbox_type '$other'")
      logger.error(s"message outbox row ${row.id}: unknown outbox_type '$other' — STICKY")
  }

  private def relayOpenCorridorRow(row: MessageOutbox): Unit = {
    val replyBox: Box[com.openbankproject.commons.dto.InBoundOpenCorridorReply] =
      try {
        Await.result(
          OpenCorridorPublisher.publishRawAndAwaitReply(row.targetId, row.operationName, row.payloadJson),
          perRowTimeout
        )
      } catch {
        case e: Throwable => Failure(s"publish await failed: ${e.getMessage}")
      }

    replyBox match {
      case Full(reply) =>
        val replyJson = Serialization.write(reply)
        val errorCode = reply.status.errorCode
        if (errorCode.isEmpty) {
          val settlementStatus =
            if (row.operationName == "obp_settlement_instruction")
              (reply.data \ "status").extractOpt[String].getOrElse("")
            else ""
          if (row.operationName == "obp_settlement_instruction" && settlementStatus != "FINAL") {
            // Broadcast but not final — keep polling by redelivery (§4.4).
            MessageOutbox.recordAttempt(row.id, row.attempts + 1, "", replyJson)
            logger.info(s"message outbox row ${row.id}: settlement ${row.subjectId} status '$settlementStatus' — will re-poll")
          } else {
            MessageOutbox.markDelivered(row.id, replyJson)
            logger.info(s"message outbox row ${row.id}: ${row.operationName} to ${row.targetId} DELIVERED")
          }
        } else if (openCorridorStickyErrorCodes.exists(errorCode.startsWith)) {
          MessageOutbox.markSticky(row.id, row.attempts + 1, errorCode, replyJson)
          logger.error(s"message outbox row ${row.id}: ${row.operationName} to ${row.targetId} " +
            s"STICKY error $errorCode — operator reconciliation required (subject ${row.subjectId})")
        } else {
          // Retryable business failure (e.g. SETTLEMENT-FAILED, CBS-DELIVERY-FAILED).
          MessageOutbox.recordAttempt(row.id, row.attempts + 1, errorCode, replyJson)
          logger.warn(s"message outbox row ${row.id}: ${row.operationName} to ${row.targetId} " +
            s"replied $errorCode — will retry")
        }
      case failure =>
        val error = failure match {
          case Failure(msg, _, _) => msg
          case _ => "no reply"
        }
        MessageOutbox.recordAttempt(row.id, row.attempts + 1, error.take(2000))
        logger.warn(s"message outbox row ${row.id}: ${row.operationName} to ${row.targetId} " +
          s"transport failure (attempt ${row.attempts}): $error")
    }
  }
}
