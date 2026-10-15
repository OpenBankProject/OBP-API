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
package code.api.v7_0_0

import java.util.Date

import code.dynamicentitywebhook.{DynamicEntityWebhookFieldCondition, DynamicEntityWebhookTrait}
import code.messageoutbox.{MessageOutbox, WebhookOutbox}
import org.json4s.{JBool, JValue}

/*
 * The JSON of the v7.0.0 Dynamic Entity webhook endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/**
 * One field condition. `from` and `to` are optional: left out, any old (or new) value matches.
 */
case class DynamicEntityWebhookFieldConditionJsonV700(field: String, from: Option[JValue] = None, to: Option[JValue] = None)

/**
 * Create a webhook. `field_conditions` defaults to none, `is_active` to true and `description` to empty.
 */
case class PostDynamicEntityWebhookJsonV700(
  url: String,
  events: List[String],
  field_conditions: Option[List[DynamicEntityWebhookFieldConditionJsonV700]] = None,
  is_active: Option[Boolean] = None,
  description: Option[String] = None
)

/**
 * Change a webhook. The same fields as on create, all optional: a field left out keeps its current value.
 * An empty `field_conditions` list removes the conditions.
 */
case class PutDynamicEntityWebhookJsonV700(
  url: Option[String] = None,
  events: Option[List[String]] = None,
  field_conditions: Option[List[DynamicEntityWebhookFieldConditionJsonV700]] = None,
  is_active: Option[Boolean] = None,
  description: Option[String] = None
)

case class DynamicEntityWebhookJsonV700(
  webhook_id: String,
  bank_id: String,
  dynamic_entity_id: String,
  entity_name: String,
  url: String,
  events: List[String],
  field_conditions: List[DynamicEntityWebhookFieldConditionJsonV700],
  is_active: Boolean,
  /** Why the webhook is not active; left out while it is. */
  deactivated_reason: Option[String],
  description: String,
  /** Whether deliveries on this instance are signed: signed or not_configured. */
  signature_status: String,
  created_by_user_id: String,
  created_at: Date,
  updated_at: Date
)

case class DynamicEntityWebhooksJsonV700(web_hooks: List[DynamicEntityWebhookJsonV700])

/**
 * One delivery of a webhook: one event queued for it, and how sending it has gone. `status` is the
 * message outbox's own: PENDING, DELIVERED or STICKY. `last_http_status` is left out while no attempt
 * has had a reply, `record_id` for the test endpoint's ping, and `last_error` while there is none.
 */
case class DynamicEntityWebhookDeliveryJsonV700(
  event_id: String,
  event_name: String,
  record_id: Option[String],
  status: String,
  attempts: Int,
  last_http_status: Option[Int],
  last_error: Option[String],
  created_at: Date,
  updated_at: Date
)

object JSONFactory700DynamicEntityWebhooks {

  def toCondition(json: DynamicEntityWebhookFieldConditionJsonV700): DynamicEntityWebhookFieldCondition =
    DynamicEntityWebhookFieldCondition(field = Option(json.field).map(_.trim).orNull, from = json.from, to = json.to)

  def createConditionJson(condition: DynamicEntityWebhookFieldCondition): DynamicEntityWebhookFieldConditionJsonV700 =
    DynamicEntityWebhookFieldConditionJsonV700(condition.field, condition.from, condition.to)

  def createDeliveryJson(row: MessageOutbox): DynamicEntityWebhookDeliveryJsonV700 = DynamicEntityWebhookDeliveryJsonV700(
    event_id = row.subjectId,
    event_name = row.operationName,
    record_id = scala.util.Try(com.openbankproject.commons.util.JsonAliases.parse(row.payloadJson) \ "record_id")
      .toOption.collect { case org.json4s.JString(id) => id },
    status = row.status,
    attempts = row.attempts,
    last_http_status = WebhookOutbox.lastReply(row).map(_.http_status),
    last_error = Option(row.LastError.get).filter(_.trim.nonEmpty),
    created_at = row.CreatedAt.get,
    updated_at = row.UpdatedAt.get
  )

  def createWebhookJson(webhook: DynamicEntityWebhookTrait): DynamicEntityWebhookJsonV700 = DynamicEntityWebhookJsonV700(
    webhook_id = webhook.webhookId,
    bank_id = webhook.bankId,
    dynamic_entity_id = webhook.dynamicEntityId,
    entity_name = webhook.entityName,
    url = webhook.url,
    events = webhook.events,
    field_conditions = webhook.fieldConditions.map(createConditionJson),
    is_active = webhook.isActive,
    deactivated_reason = webhook.deactivatedReason,
    description = webhook.description,
    signature_status = WebhookOutbox.signatureStatus,
    created_by_user_id = webhook.createdByUserId,
    created_at = webhook.createdAt,
    updated_at = webhook.updatedAt
  )

  val postDynamicEntityWebhookJsonV700Example = PostDynamicEntityWebhookJsonV700(
    url = "https://receiver.example.com/hooks/obp",
    events = List("CREATED", "UPDATED", "DELETED"),
    field_conditions = Some(List(
      DynamicEntityWebhookFieldConditionJsonV700("approved", Some(JBool(false)), Some(JBool(true))),
      DynamicEntityWebhookFieldConditionJsonV700("amount")
    )),
    is_active = Some(true),
    description = Some("Tell the loan system when an application is approved or its amount changes")
  )

  val putDynamicEntityWebhookJsonV700Example = PutDynamicEntityWebhookJsonV700(
    url = Some(postDynamicEntityWebhookJsonV700Example.url),
    events = Some(postDynamicEntityWebhookJsonV700Example.events),
    field_conditions = postDynamicEntityWebhookJsonV700Example.field_conditions,
    is_active = Some(true),
    description = postDynamicEntityWebhookJsonV700Example.description
  )

  val dynamicEntityWebhookJsonV700Example = DynamicEntityWebhookJsonV700(
    webhook_id = "7d3c1c2e-5b7a-4f0e-9a51-2f1f6f0c9b11",
    bank_id = "gh.29.uk",
    dynamic_entity_id = "f6c2a8d4-1b3e-4c5f-8a7b-9d0e1f2a3b4c",
    entity_name = "loan_application",
    url = "https://receiver.example.com/hooks/obp",
    events = List("CREATED", "UPDATED", "DELETED"),
    field_conditions = postDynamicEntityWebhookJsonV700Example.field_conditions.getOrElse(Nil),
    is_active = true,
    deactivated_reason = None,
    description = "Tell the loan system when an application is approved or its amount changes",
    signature_status = WebhookOutbox.SignatureStatusSigned,
    created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    created_at = new Date(),
    updated_at = new Date()
  )

  val dynamicEntityWebhooksJsonV700Example = DynamicEntityWebhooksJsonV700(List(dynamicEntityWebhookJsonV700Example))

  val dynamicEntityWebhookDeliveryJsonV700Example = DynamicEntityWebhookDeliveryJsonV700(
    event_id = "0f8e4a71-5ff1-4006-95bb-7fd9e4adaef9",
    event_name = "webhook.test",
    record_id = None,
    status = "PENDING",
    attempts = 0,
    last_http_status = None,
    last_error = None,
    created_at = new Date(),
    updated_at = new Date()
  )
}
