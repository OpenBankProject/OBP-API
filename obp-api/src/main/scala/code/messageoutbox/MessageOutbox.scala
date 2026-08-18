package code.messageoutbox

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import java.util.Date

/**
 * Generic transactional outbox for asynchronous messages OBP-API must deliver.
 *
 * The business event (e.g. an Open Corridor settle) commits in one DB
 * transaction; the outbound messages must survive a crash between that commit
 * and the publish. So they are written as rows here in the SAME transaction,
 * and the relay publishes them afterwards with at-least-once redelivery,
 * recording each reply. Publishing to a broker cannot participate in the DB
 * transaction — this table is what closes that atomicity gap.
 *
 * `outbox_type` discriminates message families; each type contributes its own
 * publish behavior to the relay. Types so far:
 *   OPEN_CORRIDOR — Interface C messages to a bank's RabbitMQ vhost
 *                   (`target_id` = bank_id, publisher = OpenCorridorPublisher).
 *
 * Row lifecycle:
 *   PENDING   — not yet delivered; the relay keeps publishing with backoff.
 *   DELIVERED — the receiver replied success.
 *   STICKY    — the receiver replied with an error that retrying cannot fix.
 *               Needs operator reconciliation: visible via
 *               GET /management/message-outbox, re-queued via its /retry.
 *
 * `updatedAt` is load-bearing rather than decoration: the relay computes its
 * exponential backoff from it, so every mutation stamps it. That is why the
 * update helpers below all write updated_at rather than leaving it to a
 * database default.
 */
case class MessageOutbox(
  id: Long,
  outboxType: String,
  subjectId: String,
  subjectIdType: String,
  operationName: String,
  targetId: String,
  payloadJson: String,
  status: String,
  attempts: Int,
  lastError: String,
  lastReplyJson: String,
  metadataJson: String,
  createdAt: Date,
  updatedAt: Date
)

object MessageOutbox {
  val STATUS_PENDING = "PENDING"
  val STATUS_DELIVERED = "DELIVERED"
  val STATUS_STICKY = "STICKY"

  val TYPE_OPEN_CORRIDOR = "OPEN_CORRIDOR"

  // subject_id_type holds the OBP id-field name whose value space subject_id
  // belongs to (exact snake_case field name, e.g. transaction_request_id,
  // settlement_id, consent_id, customer_id ...).
  val SUBJECT_TYPE_SETTLEMENT_ID = "settlement_id"
  val SUBJECT_TYPE_TRANSACTION_REQUEST_ID = "transaction_request_id"

  private val selectColumns =
    fr"""SELECT id, outbox_type, subject_id, subject_id_type, operation_name, target_id,
                payload_json, status, attempts, last_error, last_reply_json, metadata_json,
                created_at, updated_at
         FROM message_outbox"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[Int], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def fromRow(row: Row): MessageOutbox = row match {
    case (id, outboxType, subjectId, subjectIdType, operationName, targetId, payloadJson,
          status, attempts, lastError, lastReplyJson, metadataJson, createdAt, updatedAt) =>
      MessageOutbox(id, outboxType.orNull, subjectId.orNull, subjectIdType.orNull,
        operationName.orNull, targetId.orNull, payloadJson.orNull, status.orNull,
        attempts.getOrElse(0), lastError.orNull, lastReplyJson.orNull, metadataJson.orNull,
        createdAt.orNull, updatedAt.orNull)
  }

  private def query(condition: Fragment): List[MessageOutbox] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def now(): java.sql.Timestamp = new java.sql.Timestamp(System.currentTimeMillis())

  def enqueue(
    outboxType: String,
    subjectId: String,
    subjectIdType: String,
    operationName: String,
    targetId: String,
    payloadJson: String
  ): MessageOutbox = {
    val ts = now()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO message_outbox
            (outbox_type, subject_id, subject_id_type, operation_name, target_id, payload_json,
             status, attempts, last_error, last_reply_json, metadata_json, created_at, updated_at)
            VALUES
            ($outboxType, $subjectId, $subjectIdType, $operationName, $targetId, $payloadJson,
             $STATUS_PENDING, 0, '', '', '', $ts, $ts)"""
        .update.run)
    val id = DoobieUtil.runQuery(
      sql"SELECT MAX(id) FROM message_outbox WHERE subject_id = $subjectId AND operation_name = $operationName"
        .query[Long].unique)
    MessageOutbox(id, outboxType, subjectId, subjectIdType, operationName, targetId, payloadJson,
      STATUS_PENDING, 0, "", "", "", ts, ts)
  }

  def pending(): List[MessageOutbox] = query(fr"WHERE status = $STATUS_PENDING")

  def bySubjectId(subjectId: String): List[MessageOutbox] = query(fr"WHERE subject_id = $subjectId")

  def findById(id: Long): Box[MessageOutbox] =
    query(fr"WHERE id = $id LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** Operator listing: newest first, optionally narrowed by status and/or type. */
  def findAllFiltered(status: Option[String], outboxType: Option[String], limit: Int): List[MessageOutbox] = {
    val conditions = List(
      status.map(s => fr"status = $s"),
      outboxType.map(t => fr"outbox_type = $t")
    ).flatten
    val where = if (conditions.isEmpty) Fragment.empty else fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
    query(where ++ fr"ORDER BY id DESC LIMIT $limit")
  }

  /** Record an attempt that did not settle the row: bumps attempts, keeps it PENDING. */
  def recordAttempt(id: Long, attempts: Int, lastError: String, lastReplyJson: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET attempts = $attempts, last_error = $lastError,
              last_reply_json = $lastReplyJson, updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
  }

  /** Record an attempt with no reply body to store (transport failure). */
  def recordAttempt(id: Long, attempts: Int, lastError: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET attempts = $attempts, last_error = $lastError,
              updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
  }

  def markDelivered(id: Long, lastReplyJson: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET status = $STATUS_DELIVERED, last_error = '',
              last_reply_json = $lastReplyJson, updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
  }

  def markSticky(id: Long, attempts: Int, lastError: String, lastReplyJson: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET status = $STATUS_STICKY, attempts = $attempts,
              last_error = $lastError, last_reply_json = $lastReplyJson, updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
  }

  def markSticky(id: Long, attempts: Int, lastError: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET status = $STATUS_STICKY, attempts = $attempts,
              last_error = $lastError, updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
  }

  /** Operator retry: back to PENDING with the attempt counter and error cleared. */
  def resetForRetry(id: Long): Box[MessageOutbox] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET status = $STATUS_PENDING, attempts = 0, last_error = '',
              updated_at = ${now()}
            WHERE id = $id""".update.run)
    findById(id)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM message_outbox".update.run)
    ()
  }
}
