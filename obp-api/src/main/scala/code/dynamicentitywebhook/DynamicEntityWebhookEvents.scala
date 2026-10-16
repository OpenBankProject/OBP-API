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

package code.dynamicentitywebhook

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import code.api.util.APIUtil
import code.messageoutbox.{MessageOutbox, WebhookOutbox}
import org.json4s._
import org.json4s.native.JsonMethods.{compact, render}

/**
 * This object builds the request a Dynamic Entity webhook sends, and queues it through
 * [[code.messageoutbox.WebhookOutbox]].
 *
 * The body carries identifiers only, never the record's values or any user id: a receiver that needs
 * the record fetches it with its own credentials, so the normal access checks apply when it reads.
 * `matched_conditions` lists the conditions an update matched, as the subscriber wrote them, so it
 * says nothing the subscriber did not already ask about.
 */
object DynamicEntityWebhookEvents {

  val EventNameCreated = "dynamic_entity.record.created"
  val EventNameUpdated = "dynamic_entity.record.updated"
  val EventNameDeleted = "dynamic_entity.record.deleted"
  /** The ping the test endpoint sends. */
  val EventNameTest = "webhook.test"

  /** The event name of a record change, e.g. UPDATED -> dynamic_entity.record.updated. */
  def eventNameOf(event: String): String = event match {
    case DynamicEntityWebhooks.EventCreated => EventNameCreated
    case DynamicEntityWebhooks.EventUpdated => EventNameUpdated
    case DynamicEntityWebhooks.EventDeleted => EventNameDeleted
  }

  /** A time as the body gives it: UTC, to the second, e.g. 2026-10-14T09:12:44Z. */
  private def timestamp(date: Date): String = date.toInstant.truncatedTo(ChronoUnit.SECONDS).toString

  /** The body of one event, exactly as it is posted and signed. */
  def body(eventName: String, eventId: String, occurredAt: Date, webhook: DynamicEntityWebhookTrait,
           recordId: Option[String], recordUpdatedAt: Option[Date],
           matchedConditions: List[DynamicEntityWebhookFieldCondition]): String = {
    def condition(c: DynamicEntityWebhookFieldCondition): JValue =
      JObject(List(JField("field", JString(c.field))) ++ c.from.map(JField("from", _)) ++ c.to.map(JField("to", _)))
    compact(render(JObject(
      List(
        JField("event_name", JString(eventName)),
        JField("event_id", JString(eventId)),
        JField("occurred_at", JString(timestamp(occurredAt))),
        JField("webhook_id", JString(webhook.webhookId)),
        JField("bank_id", JString(webhook.bankId)),
        JField("entity_name", JString(webhook.entityName))
      ) ++
        recordId.map(id => JField("record_id", JString(id))) ++
        recordUpdatedAt.map(at => JField("record_updated_at", JString(timestamp(at)))) ++
        (if (matchedConditions.isEmpty) None else Some(JField("matched_conditions", JArray(matchedConditions.map(condition)))))
    )))
  }

  /**
   * This queues one event for one webhook, in the current transaction. The event id is new: the same
   * event queued for two webhooks gets two ids, one per delivery.
   */
  def enqueue(webhook: DynamicEntityWebhookTrait, eventName: String, recordId: Option[String],
              recordUpdatedAt: Option[Date], matchedConditions: List[DynamicEntityWebhookFieldCondition]): MessageOutbox = {
    val eventId = APIUtil.generateUUID()
    WebhookOutbox.enqueue(webhook.webhookId, eventId, eventName, webhook.url,
      body(eventName, eventId, Date.from(Instant.now()), webhook, recordId, recordUpdatedAt, matchedConditions))
  }

  /** This queues the test endpoint's ping for a webhook. */
  def enqueueTest(webhook: DynamicEntityWebhookTrait): MessageOutbox =
    enqueue(webhook, EventNameTest, None, None, Nil)
}
