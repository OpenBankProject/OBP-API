package code.bankconnectors.opencorridor

import code.actorsystem.ObpActorSystem
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Failure, Full}
import org.json4s._
import org.json4s.native.Serialization

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Publishes Open Corridor outbox rows to the target banks' vhosts and records
 * the replies. Runs on the actor-system scheduler (started from Boot when
 * `open_corridor_enabled=true`), one pass per tick, rows processed serially —
 * throughput is not the concern here, at-least-once delivery with a recorded
 * audit trail is.
 *
 * Reply handling (locked wire contract §4.2/§4.4):
 *  - transport failure / timeout / broker unregistered → row stays PENDING,
 *    attempts+1 (retried next tick; exponential backoff by attempts).
 *  - errorCode == "" on a credit notification → DELIVERED.
 *  - errorCode == "" on a settlement instruction → DELIVERED only when the
 *    bank reports status FINAL; SUBMITTED / SETTLING keep the row PENDING —
 *    redelivery IS the status poll, and the Bank Node never pays twice for the
 *    same idempotency_key.
 *  - OBP-BANK-NODE-SETTLEMENT-FAILED → stays PENDING: the node allows a retry
 *    when the failure provably never reached the chain, and repeats the
 *    recorded error otherwise, so redelivery is safe and the error stays
 *    visible on the row either way.
 *  - any other OBP-BANK-NODE-* error (COMMITMENT-MISMATCH, CBS-DELIVERY-FAILED,
 *    BAD-MESSAGE, NOT-IMPLEMENTED, SETTLEMENT-NOT-CONFIGURED) → STICKY: retry
 *    cannot fix it; it needs an operator. The error + full reply are recorded —
 *    never swallowed.
 */
object OpenCorridorOutboxRelay extends MdcLoggable {

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  /** Base backoff between attempts for a row; doubles per attempt, capped. */
  private val baseBackoff = 10.seconds
  private val maxBackoff = 10.minutes
  /** Cap on how long one row's publish may block the (serial) relay pass. */
  private val perRowTimeout = 60.seconds

  private val stickyErrorCodes = Set(
    "OBP-BANK-NODE-COMMITMENT-MISMATCH",
    "OBP-BANK-NODE-CBS-DELIVERY-FAILED",
    "OBP-BANK-NODE-BAD-MESSAGE",
    "OBP-BANK-NODE-NOT-IMPLEMENTED",
    "OBP-BANK-NODE-SETTLEMENT-NOT-CONFIGURED"
  )

  def start(intervalSeconds: Long): Unit = {
    implicit val executor = ObpActorSystem.localActorSystem.dispatcher
    ObpActorSystem.localActorSystem.scheduler.schedule(
      initialDelay = scala.concurrent.duration.Duration(intervalSeconds, TimeUnit.SECONDS),
      interval = scala.concurrent.duration.Duration(intervalSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit =
          try relayOnePass()
          catch { case e: Throwable => logger.error("Open Corridor outbox relay pass failed", e) }
      }
    )
    logger.info(s"Open Corridor outbox relay started (interval ${intervalSeconds}s)")
  }

  /** One pass over the PENDING rows that are due (backoff by attempts). */
  def relayOnePass(): Unit = {
    val now = System.currentTimeMillis()
    val due = OpenCorridorOutbox.pending().filter { row =>
      val backoff = (baseBackoff * math.pow(2, math.min(row.attempts, 6)).toLong).min(maxBackoff)
      row.updatedAt.get.getTime + backoff.toMillis <= now || row.attempts == 0
    }
    if (due.nonEmpty) logger.debug(s"Open Corridor outbox relay: ${due.size} row(s) due")
    due.foreach(relayRow)
  }

  def relayRow(row: OpenCorridorOutbox): Unit = {
    val replyBox: Box[com.openbankproject.commons.dto.InBoundOpenCorridorReply] =
      try {
        Await.result(
          OpenCorridorPublisher.publishRawAndAwaitReply(row.targetBankId, row.messageId, row.payloadJson),
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
            if (row.messageId == "obp_settlement_instruction")
              (reply.data \ "status").extractOpt[String].getOrElse("")
            else ""
          if (row.messageId == "obp_settlement_instruction" && settlementStatus != "FINAL") {
            // Broadcast but not final — keep polling by redelivery (§4.4).
            row.Attempts(row.attempts + 1).LastError("").LastReplyJson(replyJson).saveMe()
            logger.info(s"Open Corridor outbox row ${row.id.get}: settlement ${row.settlementId} status '$settlementStatus' — will re-poll")
          } else {
            row.Status(OpenCorridorOutbox.STATUS_DELIVERED).LastError("").LastReplyJson(replyJson).saveMe()
            logger.info(s"Open Corridor outbox row ${row.id.get}: ${row.messageId} to ${row.targetBankId} DELIVERED")
          }
        } else if (stickyErrorCodes.exists(errorCode.startsWith)) {
          row.Status(OpenCorridorOutbox.STATUS_STICKY).Attempts(row.attempts + 1)
            .LastError(errorCode).LastReplyJson(replyJson).saveMe()
          logger.error(s"Open Corridor outbox row ${row.id.get}: ${row.messageId} to ${row.targetBankId} " +
            s"STICKY error $errorCode — operator reconciliation required (settlement ${row.settlementId})")
        } else {
          // Retryable business failure (e.g. SETTLEMENT-FAILED) — keep redelivering.
          row.Attempts(row.attempts + 1).LastError(errorCode).LastReplyJson(replyJson).saveMe()
          logger.warn(s"Open Corridor outbox row ${row.id.get}: ${row.messageId} to ${row.targetBankId} " +
            s"replied $errorCode — will retry")
        }
      case failure =>
        val error = failure match {
          case Failure(msg, _, _) => msg
          case _ => "no reply"
        }
        row.Attempts(row.attempts + 1).LastError(error.take(2000)).saveMe()
        logger.warn(s"Open Corridor outbox row ${row.id.get}: ${row.messageId} to ${row.targetBankId} " +
          s"transport failure (attempt ${row.attempts}): $error")
    }
  }
}
