/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.messageoutbox

import code.api.util.CommonsEmailWrapper.EmailContent
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import org.json4s.native.Serialization
import org.json4s.{DefaultFormats, Formats}

import java.util.Date

/** An email as stored in an EMAIL outbox row: everything needed to send it, rendered at enqueue time. */
case class OutboxEmailPayload(
  from: String,
  to: List[String],
  cc: List[String],
  bcc: List[String],
  subject: String,
  text_content: Option[String],
  html_content: Option[String]
) {
  def toEmailContent: EmailContent =
    EmailContent(from = from, to = to, cc = cc, bcc = bcc, subject = subject,
      textContent = text_content, htmlContent = html_content)
}


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
 *   EMAIL         — an email, rendered at enqueue time (`payload_json` is an
 *                   OutboxEmailPayload, `target_id` the recipients), sent over
 *                   SMTP by the relay. Queued rather than sent in the request
 *                   so that a rolled back request emails nobody, and so a burst
 *                   of emails never blocks request threads.
 *   WEBHOOK       — a webhook request: `payload_json` is the body to POST,
 *                   `metadata_json` the URL, `target_id` the webhook id and
 *                   `subject_id` the event id. Sent by WebhookOutbox.
 *
 * Row lifecycle:
 *   PENDING   — not yet delivered; the relay keeps publishing with backoff.
 *   DELIVERED — the receiver replied success.
 *   STICKY    — the receiver replied with an error that retrying cannot fix,
 *               or the relay used up the type's attempt limit without
 *               delivering it. Needs operator reconciliation: visible via
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
  val TYPE_EMAIL = "EMAIL"
  val TYPE_WEBHOOK = "WEBHOOK"

  val OPERATION_ROLE_GRANTED_EMAIL = "role_granted_email"

  // subject_id_type holds the OBP id-field name whose value space subject_id
  // belongs to (exact snake_case field name, e.g. transaction_request_id,
  // settlement_id, consent_id, customer_id ...).
  val SUBJECT_TYPE_SETTLEMENT_ID = "settlement_id"
  val SUBJECT_TYPE_TRANSACTION_REQUEST_ID = "transaction_request_id"
  val SUBJECT_TYPE_ENTITLEMENT_ID = "entitlement_id"

  private val emailFormats: Formats = DefaultFormats

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
        createdAt.map(t => new Date(t.getTime)).orNull, updatedAt.map(t => new Date(t.getTime)).orNull)
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
    payloadJson: String,
    metadataJson: String = ""
  ): MessageOutbox = {
    val ts = now()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO message_outbox
            (outbox_type, subject_id, subject_id_type, operation_name, target_id, payload_json,
             status, attempts, last_error, last_reply_json, metadata_json, created_at, updated_at)
            VALUES
            ($outboxType, $subjectId, $subjectIdType, $operationName, $targetId, $payloadJson,
             $STATUS_PENDING, 0, '', '', ${Option(metadataJson).getOrElse("")}, $ts, $ts)"""
        .update.run)
    val id = DoobieUtil.runQuery(
      sql"SELECT MAX(id) FROM message_outbox WHERE subject_id = $subjectId AND operation_name = $operationName"
        .query[Long].unique)
    MessageOutbox(id, outboxType, subjectId, subjectIdType, operationName, targetId, payloadJson,
      STATUS_PENDING, 0, "", "", Option(metadataJson).getOrElse(""), new Date(ts.getTime), new Date(ts.getTime))
  }

  /** Queue an email; the relay sends it (attachments are not supported). */
  def enqueueEmail(subjectId: String, subjectIdType: String, operationName: String, email: EmailContent,
                   metadataJson: String = ""): MessageOutbox = {
    val payload = OutboxEmailPayload(email.from, email.to, email.cc, email.bcc, email.subject,
      email.textContent, email.htmlContent)
    enqueue(TYPE_EMAIL, subjectId, subjectIdType, operationName,
      email.to.mkString(",").take(255), Serialization.write(payload)(emailFormats), metadataJson)
  }

  def emailPayload(row: MessageOutbox): OutboxEmailPayload =
    Serialization.read[OutboxEmailPayload](row.payloadJson)(emailFormats, manifest[OutboxEmailPayload])

  /**
   * Claim a PENDING row for one delivery attempt: counts the attempt, but only if the row is still
   * as `row` read it. Returns true for exactly one caller, so with several OBP-API instances each
   * running the relay a message is sent by one of them only. It commits at once (outside a request);
   * if the claimant dies before sending, the row is still PENDING and is retried after the backoff.
   */
  def claimForDelivery(row: MessageOutbox): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox
            SET attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ${row.id}
              AND status = $STATUS_PENDING
              AND attempts = ${row.attempts}""".update.run) == 1

  /** Set the status and last error, leaving the attempt counter alone (the claim already counted it). */
  def setStatusAndError(id: Long, status: String, lastError: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET status = $status, last_error = $lastError, updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
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

  /** Set the outcome of an attempt the claim already counted: status, last error and last reply together. */
  def setOutcome(id: Long, status: String, lastError: String, lastReplyJson: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE message_outbox SET status = $status, last_error = $lastError,
              last_reply_json = $lastReplyJson, updated_at = ${now()}
            WHERE id = $id""".update.run)
    ()
  }

  private def webhookRows(webhookId: String): Fragment =
    fr"WHERE outbox_type = $TYPE_WEBHOOK AND target_id = $webhookId"

  /** Drop a webhook's requests that have not been delivered yet; the number dropped. */
  def deletePendingWebhookRows(webhookId: String): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM message_outbox" ++ webhookRows(webhookId) ++ fr"AND status = $STATUS_PENDING").update.run)

  /** A webhook's requests, newest first, optionally narrowed by status. */
  def findWebhookRows(webhookId: String, status: Option[String], limit: Int, offset: Int): List[MessageOutbox] =
    query(webhookRows(webhookId) ++ status.map(s => fr"AND status = $s").getOrElse(Fragment.empty) ++
      fr"ORDER BY id DESC" ++ DoobieUtil.pagination(limit, offset))

  /** How many messages of one type and operation were queued about one subject after `since`. */
  def countQueuedSince(outboxType: String, operationName: String, subjectId: String, since: Date): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM message_outbox
            WHERE outbox_type = $outboxType AND operation_name = $operationName AND subject_id = $subjectId
              AND created_at > ${new java.sql.Timestamp(since.getTime)}""".query[Long].unique)

  /** How many requests were queued for a webhook after `since`. */
  def countWebhookRowsSince(webhookId: String, since: Date): Long =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM message_outbox" ++ webhookRows(webhookId) ++
        fr"AND created_at > ${new java.sql.Timestamp(since.getTime)}").query[Long].unique)

  /** One request of a webhook, by its event id. */
  def findWebhookRow(webhookId: String, eventId: String): Option[MessageOutbox] =
    query(webhookRows(webhookId) ++ fr"AND subject_id = $eventId ORDER BY id ASC").headOption

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM message_outbox".update.run)
    ()
  }
}
