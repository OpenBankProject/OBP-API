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

import java.nio.charset.StandardCharsets
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.util.concurrent.TimeUnit

import code.api.util.{APIUtil, CertificateUtil}
import code.util.Helper.MdcLoggable
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader, JWSObject, Payload}
import net.liftweb.mapper.{By, Descending, MaxRows, OrderBy, QueryParam, StartAt}
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.json4s.native.Serialization
import org.json4s.{DefaultFormats, Formats}

import scala.util.Try

/** Where a WEBHOOK outbox row is sent, stored in its metadata_json. */
case class OutboxWebhookMetadata(url: String)

/**
 * The reply a WEBHOOK delivery last got, stored in its last_reply_json: the HTTP status and the start
 * of the body. Absent when the request got no reply at all (a timeout, a refused connection).
 */
case class OutboxWebhookReply(http_status: Int, body: String)

/**
 * This object queues and delivers webhook requests through the message outbox, as rows of type
 * WEBHOOK. It is not tied to one kind of webhook: whoever queues a request decides its body, and this
 * object posts it.
 *
 * A row is written in the same database transaction as the change it reports, so a request that rolls
 * back queues nothing. The relay then posts it (see [[deliver]]):
 *  - The body is the JSON queued, sent as it is. The URL is the one the webhook had when the request
 *    was queued, and it is checked again before every attempt (see [[WebhookUrlCheck]]). Redirects are
 *    not followed, because one could lead to an address the check would refuse.
 *  - Every attempt is signed afresh, when this instance has a stable key (see [[signatureStatus]]).
 *  - A 2xx reply means DELIVERED. A 5xx, 408 or 429 reply, a timeout or a connection that fails is
 *    retried with the relay's backoff, until `webhook.outbox_max_attempts` attempts, then STICKY. Any
 *    other reply, and a URL that is now refused, is STICKY at once, because retrying cannot change it.
 *
 * The row's columns: subject_id is the event id (the same on every attempt, so a receiver drops
 * duplicates by it), operation_name the event name, target_id the webhook id.
 */
object WebhookOutbox extends MdcLoggable {

  private implicit val formats: Formats = DefaultFormats

  val SubjectIdTypeEventId = "event_id"

  /** Attempts after which a WEBHOOK row that could not be delivered goes STICKY. With the relay's
    * backoff, which reaches its 10 minute cap after 6 attempts, the default of 144 gives up after
    * roughly a day. Read on every row so a change applies without a restart. */
  val defaultMaxAttempts = 144
  def maxAttemptsInEffect: Int = APIUtil.getPropsAsIntValue("webhook.outbox_max_attempts", defaultMaxAttempts)

  /** How much of a receiver's reply body is kept. */
  private val MaxReplyBodyLength = 2000L

  val SignatureStatusSigned = "signed"
  val SignatureStatusNotConfigured = "not_configured"

  /**
   * Whether deliveries are signed. A delivery is signed with this instance's server key, the one
   * published at GET /obp/v3.1.0/certs, so a receiver can check it came from OBP. That only works when
   * the key is stable: with `jwt.use.ssl=true` it is read from the configured keystore and every node
   * shares it. Otherwise each node generates a key pair of its own at startup, which differs between
   * nodes and changes on every restart, so a receiver could not verify against it; deliveries then go
   * out unsigned and say so.
   */
  def isSigningConfigured: Boolean = APIUtil.getPropsAsBoolValue("jwt.use.ssl", false)

  def signatureStatus: String = if (isSigningConfigured) SignatureStatusSigned else SignatureStatusNotConfigured

  /**
   * This signs a request body as a JSON Web Signature with a detached payload (RFC 7515, Appendix F):
   * the protected header and the signature, with the payload part left empty, because the payload is
   * the body itself. The header carries `kid`, the key id GET /obp/v3.1.0/certs gives the public key,
   * and `typ` `obp-webhook+jwt`, so the signature cannot be taken for a token.
   */
  def sign(body: Array[Byte], privateKey: RSAPrivateKey, publicKey: RSAPublicKey, issuedAtSeconds: Long): String = {
    val keyId = new RSAKey.Builder(publicKey).keyIDFromThumbprint().build().getKeyID
    val header = new JWSHeader.Builder(JWSAlgorithm.RS256)
      .keyID(keyId)
      .`type`(new JOSEObjectType("obp-webhook+jwt"))
      .customParam("iat", issuedAtSeconds)
      .build()
    val jws = new JWSObject(header, new Payload(body))
    jws.sign(new RSASSASigner(privateKey))
    jws.serialize(true)
  }

  /**
   * This queues one webhook request. Call it inside the transaction of the change it reports.
   *
   * `body` is the exact JSON to post; it must carry `eventId` for receivers to drop duplicates by.
   */
  def enqueue(webhookId: String, eventId: String, eventName: String, url: String, body: String): MessageOutbox =
    MessageOutbox.enqueue(MessageOutbox.TYPE_WEBHOOK, eventId, SubjectIdTypeEventId, eventName, webhookId, body,
      metadataJson = Serialization.write(OutboxWebhookMetadata(url)))

  /** This drops the requests of a webhook that have not been delivered yet, when the webhook is deleted. */
  def discardPending(webhookId: String): Int =
    MessageOutbox.findAll(
      By(MessageOutbox.OutboxType, MessageOutbox.TYPE_WEBHOOK),
      By(MessageOutbox.TargetId, webhookId),
      By(MessageOutbox.Status, MessageOutbox.STATUS_PENDING)
    ).count(_.delete_!)

  /** A webhook's requests, newest first; `status` narrows them to PENDING, DELIVERED or STICKY. */
  def deliveries(webhookId: String, status: Option[String], limit: Int, offset: Int): List[MessageOutbox] =
    MessageOutbox.findAll(
      List[QueryParam[MessageOutbox]](
        By(MessageOutbox.OutboxType, MessageOutbox.TYPE_WEBHOOK),
        By(MessageOutbox.TargetId, webhookId),
        OrderBy(MessageOutbox.id, Descending),
        StartAt(offset),
        MaxRows(limit)
      ) ++ status.map(By(MessageOutbox.Status, _)): _*)

  /** One request of a webhook, by its event id. */
  def delivery(webhookId: String, eventId: String): Option[MessageOutbox] =
    MessageOutbox.find(
      By(MessageOutbox.OutboxType, MessageOutbox.TYPE_WEBHOOK),
      By(MessageOutbox.TargetId, webhookId),
      By(MessageOutbox.SubjectId, eventId)).toOption

  /**
   * This sends a STICKY request again: it goes back to PENDING with its attempts reset, so it gets the
   * full number of attempts again, and keeps its event id and body.
   */
  def requeue(row: MessageOutbox): MessageOutbox =
    row.Status(MessageOutbox.STATUS_PENDING).Attempts(0).LastError("").saveMe()

  /** The reply a row last got, if it got one. */
  def lastReply(row: MessageOutbox): Option[OutboxWebhookReply] =
    Option(row.LastReplyJson.get).filter(_.trim.nonEmpty)
      .flatMap(json => Try(Serialization.read[OutboxWebhookReply](json)).toOption)

  private lazy val client: OkHttpClient = new OkHttpClient.Builder()
    .dns(WebhookUrlCheck.deliveryDns)
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

  private val jsonMediaType = MediaType.get("application/json; charset=utf-8")

  private def isRetryable(httpStatus: Int): Boolean = httpStatus >= 500 || httpStatus == 408 || httpStatus == 429

  /** One delivery attempt for a PENDING WEBHOOK row; called by the relay. */
  def deliver(read: MessageOutbox): Unit =
    // Another instance's relay may have claimed it first; then it is theirs to send.
    if (MessageOutbox.claimForDelivery(read)) {
      val row = MessageOutbox.find(By(MessageOutbox.id, read.id.get)).openOrThrowException("claimed row")
      val url = Try(Serialization.read[OutboxWebhookMetadata](row.MetadataJson.get).url).toOption.getOrElse("")
      WebhookUrlCheck.problem(url) match {
        case Some(problem) => giveUp(row, s"The url is refused: $problem")
        case None => post(row, url)
      }
    }

  private def post(row: MessageOutbox, url: String): Unit = {
    val body = row.payloadJson.getBytes(StandardCharsets.UTF_8)
    val request = {
      val builder = new Request.Builder()
        .url(url)
        .post(RequestBody.create(body, jsonMediaType))
        .header("OBP-Webhook-Id", row.targetId)
        .header("OBP-Event-Id", row.subjectId)
        .header("OBP-Delivery-Attempt", row.attempts.toString)
        .header("OBP-Signature-Status", signatureStatus)
      if (isSigningConfigured)
        builder.header("OBP-Signature", sign(body, CertificateUtil.privateKey, CertificateUtil.publicKey, System.currentTimeMillis() / 1000))
      builder.build()
    }
    try {
      val response = client.newCall(request).execute()
      try {
        val httpStatus = response.code
        val reply = Serialization.write(OutboxWebhookReply(httpStatus, Try(response.peekBody(MaxReplyBodyLength).string()).getOrElse("")))
        row.LastReplyJson(reply)
        if (response.isSuccessful) {
          row.Status(MessageOutbox.STATUS_DELIVERED).LastError("").saveMe()
          logger.info(s"message outbox row ${row.id.get}: ${row.operationName} for webhook ${row.targetId} DELIVERED ($httpStatus)")
        } else if (isRetryable(httpStatus)) retryOrGiveUp(row, s"The receiver replied $httpStatus ${response.message}.")
        else giveUp(row, s"The receiver replied $httpStatus ${response.message}.")
      } finally response.close()
    } catch {
      case e: ForbiddenWebhookAddress => giveUp(row.LastReplyJson(""), s"The url is refused: ${e.getMessage}")
      case e: java.io.IOException => retryOrGiveUp(row.LastReplyJson(""), s"No reply: ${e.getClass.getSimpleName} ${Option(e.getMessage).getOrElse("")}".trim)
    }
  }

  // The claim already counted this attempt.
  private def retryOrGiveUp(row: MessageOutbox, error: String): Unit =
    if (row.attempts >= maxAttemptsInEffect) giveUp(row, s"Gave up after ${row.attempts} attempts. $error")
    else {
      row.Status(MessageOutbox.STATUS_PENDING).LastError(error.take(2000)).saveMe()
      logger.warn(s"message outbox row ${row.id.get}: ${row.operationName} for webhook ${row.targetId} " +
        s"not delivered (attempt ${row.attempts}), will retry: $error")
    }

  private def giveUp(row: MessageOutbox, error: String): Unit = {
    row.Status(MessageOutbox.STATUS_STICKY).LastError(error.take(2000)).saveMe()
    logger.warn(s"message outbox row ${row.id.get}: ${row.operationName} for webhook ${row.targetId} STICKY: $error")
  }
}
