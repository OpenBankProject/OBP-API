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

import java.util.Date

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.messageoutbox.WebhookOutbox
import code.util.Helper.MdcLoggable
import org.json4s._

/**
 * This object fires Dynamic Entity webhooks when a record is written. The record data provider calls it
 * from inside each write, in the same transaction, so the queued requests commit or roll back with the
 * record, and every route that writes records through that provider fires them: the Dynamic Entity
 * endpoints, Domain APIs, Dynamic Endpoints and Dynamic Resource Docs.
 *
 * For each active webhook of the record's entity that subscribes to the event, one request is queued
 * through [[code.messageoutbox.WebhookOutbox]]. An update only fires a webhook with field conditions
 * when at least one of them matches the change; the old values are the stored record's as it was
 * before the write.
 *
 * A webhook that has had more than `webhook.circuit_breaker_per_minute` requests queued in the last
 * minute is turned off with `deactivated_reason` CIRCUIT_BREAKER instead of being fired again. This is
 * the backstop against a receiver whose reply writes a record that fires the webhook again.
 */
object DynamicEntityWebhookFiring extends MdcLoggable {
  import DynamicEntityWebhooks._

  private def provider = DynamicEntityWebhooks.dynamicEntityWebhookProvider.vend

  val defaultCircuitBreakerPerMinute = 60
  def circuitBreakerPerMinute: Int =
    APIUtil.getPropsAsIntValue("webhook.circuit_breaker_per_minute", defaultCircuitBreakerPerMinute)

  /** A record was created (`before` None) or updated (`before` its stored values before the write). */
  def recordSaved(bankId: Option[String], entityName: String, recordId: String, before: Option[JObject],
                  after: JObject, updatedAt: Date): Unit =
    before match {
      case None => fire(bankId, entityName, EventCreated, recordId, updatedAt, _ => Some(Nil))
      case Some(old) => fire(bankId, entityName, EventUpdated, recordId, updatedAt, webhook =>
        if (webhook.fieldConditions.isEmpty) Some(Nil)
        else {
          val types = provider.entityFieldTypes(webhook.dynamicEntityId)
          val matched = webhook.fieldConditions.filter(matches(_, old, after, types))
          if (matched.isEmpty) None else Some(matched)
        })
    }

  /** A record was deleted. */
  def recordDeleted(bankId: Option[String], entityName: String, recordId: String): Unit =
    fire(bankId, entityName, EventDeleted, recordId, new Date(), _ => Some(Nil))

  /**
   * This queues `event` for each active webhook of the entity that subscribes to it. `decide` says,
   * for a webhook, whether it fires (Some) and with which matched conditions, or not (None).
   */
  private def fire(bankId: Option[String], entityName: String, event: String, recordId: String, at: Date,
                   decide: DynamicEntityWebhookTrait => Option[List[DynamicEntityWebhookFieldCondition]]): Unit = {
    val space = bankId.filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
    provider.getActiveByEntityName(space, entityName).openOrThrowException("the entity's webhooks")
      .filter(_.events.contains(event))
      .foreach { webhook =>
        decide(webhook).foreach { matched =>
          if (tripsCircuitBreaker(webhook)) {
            provider.deactivate(webhook.webhookId, DeactivatedCircuitBreaker).openOrThrowException("deactivate the webhook")
            logger.warn(s"fire says: webhook ${webhook.webhookId} of $entityName turned off, more than " +
              s"$circuitBreakerPerMinute requests in a minute ($DeactivatedCircuitBreaker)")
          } else
            DynamicEntityWebhookEvents.enqueue(webhook, DynamicEntityWebhookEvents.eventNameOf(event), Some(recordId), Some(at), matched)
        }
      }
  }

  private def tripsCircuitBreaker(webhook: DynamicEntityWebhookTrait): Boolean =
    WebhookOutbox.queuedSince(webhook.webhookId, new Date(System.currentTimeMillis() - 60 * 1000L)) >= circuitBreakerPerMinute

  /**
   * A value as conditions compare it, for a field of `typeName`. A boolean field also accepts the
   * strings "true" and "false", and a number may arrive as an integer or a decimal, so both are brought
   * to one form; a missing field and null are the same.
   */
  private def normalised(value: JValue, typeName: Option[String]): JValue = (value, typeName) match {
    case (JNothing, _) => JNull
    case (JString(text), Some("boolean")) if text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false") => JBool(text.toBoolean)
    case (JInt(number), _) => JDecimal(BigDecimal(number))
    case (JDouble(number), _) => JDecimal(BigDecimal(number))
    case (JDecimal(number), _) => JDecimal(number.underlying.stripTrailingZeros)
    case _ => value
  }

  /** Whether an update from `old` to `after` matches a condition: the field changed, from `from` to `to` where given. */
  private def matches(condition: DynamicEntityWebhookFieldCondition, old: JObject, after: JObject, types: Map[String, String]): Boolean = {
    val typeName = types.get(condition.field)
    def same(left: JValue, right: JValue): Boolean = (normalised(left, typeName), normalised(right, typeName)) match {
      case (JDecimal(a), JDecimal(b)) => a.compare(b) == 0
      case (a, b) => a == b
    }
    val oldValue = old \ condition.field
    val newValue = after \ condition.field
    !same(oldValue, newValue) &&
      condition.from.forall(same(_, oldValue)) &&
      condition.to.forall(same(_, newValue))
  }
}
