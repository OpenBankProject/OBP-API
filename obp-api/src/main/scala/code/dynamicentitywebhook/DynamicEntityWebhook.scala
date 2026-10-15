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
import code.users.{UserReference, Users}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector
import org.json4s.{DefaultFormats, Formats, JValue}
import org.json4s.native.Serialization

/**
 * A Dynamic Entity webhook asks OBP to send a request to a URL when records of one Dynamic Entity
 * are created, updated or deleted through the endpoints. The request carries identifiers only, never
 * record values: a receiver that needs the record fetches it with its own credentials, so the normal
 * access checks apply when it reads. See the Glossary item "Dynamic Entity Webhooks".
 */
object DynamicEntityWebhooks extends SimpleInjector {
  val dynamicEntityWebhookProvider = new Inject(() => buildOne) {}
  def buildOne: DynamicEntityWebhookProvider = DynamicEntityWebhookDbProvider

  /** The record changes a webhook can subscribe to. */
  val EventCreated = "CREATED"
  val EventUpdated = "UPDATED"
  val EventDeleted = "DELETED"
  val allEvents: List[String] = List(EventCreated, EventUpdated, EventDeleted)

  /** Why a webhook is not active. Null (None) while it is active. */
  val DeactivatedTurnedOff = "TURNED_OFF"
  val DeactivatedCircuitBreaker = "CIRCUIT_BREAKER"
  val DeactivatedEntityFieldRemoved = "ENTITY_FIELD_REMOVED"
  val DeactivatedEntityFieldTypeChanged = "ENTITY_FIELD_TYPE_CHANGED"
}

/**
 * This is one field condition of a webhook. An update matches it when the named field's value
 * changed, and, where they are given, the old value equals `from` and the new value equals `to`.
 * A webhook with conditions fires on an update when at least one of them matches.
 */
case class DynamicEntityWebhookFieldCondition(field: String, from: Option[JValue] = None, to: Option[JValue] = None)

object DynamicEntityWebhookFieldCondition {
  private implicit val formats: Formats = DefaultFormats

  def toJsonString(conditions: List[DynamicEntityWebhookFieldCondition]): String =
    Serialization.write(conditions)

  def fromJsonString(json: String): List[DynamicEntityWebhookFieldCondition] =
    Option(json).filter(_.trim.nonEmpty)
      .flatMap(s => tryo(Serialization.read[List[DynamicEntityWebhookFieldCondition]](s)).toOption)
      .getOrElse(Nil)
}

trait DynamicEntityWebhookTrait {
  def webhookId: String
  /** A bank id, or SYS for the system space. Never empty. */
  def bankId: String
  def dynamicEntityId: String
  def entityName: String
  def url: String
  /** Some of CREATED, UPDATED and DELETED. */
  def events: List[String]
  def fieldConditions: List[DynamicEntityWebhookFieldCondition]
  def isActive: Boolean
  /** Why the webhook is not active; None while it is. */
  def deactivatedReason: Option[String]
  def description: String
  /** The user who made the call that created the webhook, an agent's own id when an agent made it. */
  def createdByUserId: String
  /** The user that call was made for; the same as createdByUserId when nobody was delegating. */
  def createdByOnBehalfOfUserId: String
  def createdAt: Date
  def updatedAt: Date
}

trait DynamicEntityWebhookProvider {
  def create(bankId: String, dynamicEntityId: String, entityName: String, url: String, events: List[String],
             fieldConditions: List[DynamicEntityWebhookFieldCondition], isActive: Boolean, description: String,
             createdByUserId: String): Box[DynamicEntityWebhookTrait]
  def get(bankId: String, dynamicEntityId: String, webhookId: String): Box[DynamicEntityWebhookTrait]
  /** The webhooks of one Dynamic Entity. */
  def getByDynamicEntity(bankId: String, dynamicEntityId: String): Box[List[DynamicEntityWebhookTrait]]
  /** The webhooks of every Dynamic Entity of a space. */
  def getByBank(bankId: String): Box[List[DynamicEntityWebhookTrait]]
  /** The webhook of a Dynamic Entity with this URL, if there is one. */
  def getByUrl(bankId: String, dynamicEntityId: String, url: String): Box[DynamicEntityWebhookTrait]
  /**
   * This replaces a webhook's settings. `deactivatedReason` is stored as given, so the caller decides
   * it: None when the webhook is active, a reason when it is not.
   */
  def update(bankId: String, dynamicEntityId: String, webhookId: String, url: String, events: List[String],
             fieldConditions: List[DynamicEntityWebhookFieldCondition], isActive: Boolean,
             deactivatedReason: Option[String], description: String): Box[DynamicEntityWebhookTrait]
  def delete(bankId: String, dynamicEntityId: String, webhookId: String): Box[Boolean]
}

object DynamicEntityWebhookDbProvider extends DynamicEntityWebhookProvider {
  import DynamicEntityWebhooks._

  /**
   * This works out the two user ids to record as the webhook's creator: the caller itself, which is
   * an agent's own id when an agent made the call, and the user the call was made for. Both are kept
   * because a webhook sends data out of OBP and outlives the Consent that created it, so "which agent
   * set this up?" and "on whose authority?" must both be answerable from the row.
   */
  private def creatorsOf(createdByUserId: String): (String, String) =
    if (createdByUserId == null || createdByUserId.isEmpty) (createdByUserId, createdByUserId)
    else Users.users.vend.attributionOf(createdByUserId, UserReference.DynamicEntityWebhook_CreatedByUserId) match {
      case Full(attribution) => (attribution.userId, attribution.onBehalfOfUserId)
      case _                 => (createdByUserId, createdByUserId)
    }

  private def find(bankId: String, dynamicEntityId: String, webhookId: String): Box[DynamicEntityWebhook] =
    DynamicEntityWebhook.find(
      By(DynamicEntityWebhook.BankId, bankId),
      By(DynamicEntityWebhook.DynamicEntityId, dynamicEntityId),
      By(DynamicEntityWebhook.WebhookId, webhookId))

  override def create(bankId: String, dynamicEntityId: String, entityName: String, url: String, events: List[String],
                      fieldConditions: List[DynamicEntityWebhookFieldCondition], isActive: Boolean, description: String,
                      createdByUserId: String): Box[DynamicEntityWebhookTrait] = {
    val (creator, creatorOnBehalfOf) = creatorsOf(createdByUserId)
    tryo {
      DynamicEntityWebhook.create
        .WebhookId(APIUtil.generateUUID())
        .BankId(bankId)
        .DynamicEntityId(dynamicEntityId)
        .EntityName(entityName)
        .Url(url)
        .Events(events.mkString(","))
        .FieldConditions(DynamicEntityWebhookFieldCondition.toJsonString(fieldConditions))
        .IsActive(isActive)
        .DeactivatedReason(if (isActive) null else DeactivatedTurnedOff)
        .Description(description)
        .CreatedByUserId(creator)
        .CreatedByOnBehalfOfUserId(creatorOnBehalfOf)
        .saveMe()
    }
  }

  override def get(bankId: String, dynamicEntityId: String, webhookId: String): Box[DynamicEntityWebhookTrait] =
    find(bankId, dynamicEntityId, webhookId)

  override def getByDynamicEntity(bankId: String, dynamicEntityId: String): Box[List[DynamicEntityWebhookTrait]] =
    tryo(DynamicEntityWebhook.findAll(
      By(DynamicEntityWebhook.BankId, bankId),
      By(DynamicEntityWebhook.DynamicEntityId, dynamicEntityId),
      OrderBy(DynamicEntityWebhook.CreatedAt, Ascending)))

  override def getByBank(bankId: String): Box[List[DynamicEntityWebhookTrait]] =
    tryo(DynamicEntityWebhook.findAll(
      By(DynamicEntityWebhook.BankId, bankId),
      OrderBy(DynamicEntityWebhook.EntityName, Ascending)))

  override def getByUrl(bankId: String, dynamicEntityId: String, url: String): Box[DynamicEntityWebhookTrait] =
    DynamicEntityWebhook.find(
      By(DynamicEntityWebhook.BankId, bankId),
      By(DynamicEntityWebhook.DynamicEntityId, dynamicEntityId),
      By(DynamicEntityWebhook.Url, url))

  override def update(bankId: String, dynamicEntityId: String, webhookId: String, url: String, events: List[String],
                      fieldConditions: List[DynamicEntityWebhookFieldCondition], isActive: Boolean,
                      deactivatedReason: Option[String], description: String): Box[DynamicEntityWebhookTrait] =
    find(bankId, dynamicEntityId, webhookId).flatMap { row =>
      tryo {
        row.Url(url)
          .Events(events.mkString(","))
          .FieldConditions(DynamicEntityWebhookFieldCondition.toJsonString(fieldConditions))
          .IsActive(isActive)
          .DeactivatedReason(deactivatedReason.orNull)
          .Description(description)
          .UpdatedAt(new Date())
          .saveMe()
      }
    }

  override def delete(bankId: String, dynamicEntityId: String, webhookId: String): Box[Boolean] =
    find(bankId, dynamicEntityId, webhookId).flatMap(row => tryo(row.delete_!))
}

class DynamicEntityWebhook extends DynamicEntityWebhookTrait with LongKeyedMapper[DynamicEntityWebhook] with IdPK {
  def getSingleton = DynamicEntityWebhook

  object WebhookId extends MappedString(this, 36) {
    override def dbColumnName = "webhook_id"
  }
  // SYS for the system space, never NULL: see DynamicEntitySpace.
  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
    override def defaultValue = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  }
  object DynamicEntityId extends MappedString(this, 255) {
    override def dbColumnName = "dynamic_entity_id"
  }
  // Kept beside the id because a record write knows the entity by name, and looks webhooks up by it.
  object EntityName extends MappedString(this, 255) {
    override def dbColumnName = "entity_name"
  }
  object Url extends MappedString(this, 1024) {
    override def dbColumnName = "url"
  }
  // A comma separated list of CREATED, UPDATED and DELETED.
  object Events extends MappedString(this, 64) {
    override def dbColumnName = "events"
  }
  // A JSON array of DynamicEntityWebhookFieldCondition.
  object FieldConditions extends MappedText(this) {
    override def dbColumnName = "field_conditions"
  }
  object IsActive extends MappedBoolean(this) {
    override def dbColumnName = "is_active"
    override def defaultValue = true
  }
  object DeactivatedReason extends MappedString(this, 64) {
    override def dbColumnName = "deactivated_reason"
  }
  object Description extends MappedString(this, 2000) {
    override def dbColumnName = "description"
  }
  object CreatedByUserId extends MappedString(this, 255) {
    override def dbColumnName = "created_by_user_id"
  }
  object CreatedByOnBehalfOfUserId extends MappedString(this, 255) {
    override def dbColumnName = "created_by_on_behalf_of_user_id"
  }
  object CreatedAt extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new Date()
  }
  object UpdatedAt extends MappedDateTime(this) {
    override def dbColumnName = "updated_at"
    override def defaultValue = new Date()
  }

  override def webhookId: String = WebhookId.get
  override def bankId: String = BankId.get
  override def dynamicEntityId: String = DynamicEntityId.get
  override def entityName: String = EntityName.get
  override def url: String = Url.get
  override def events: List[String] = Option(Events.get).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
  override def fieldConditions: List[DynamicEntityWebhookFieldCondition] = DynamicEntityWebhookFieldCondition.fromJsonString(FieldConditions.get)
  override def isActive: Boolean = IsActive.get
  override def deactivatedReason: Option[String] = Option(DeactivatedReason.get).filter(_.nonEmpty)
  override def description: String = Option(Description.get).getOrElse("")
  override def createdByUserId: String = CreatedByUserId.get
  override def createdByOnBehalfOfUserId: String = CreatedByOnBehalfOfUserId.get
  override def createdAt: Date = CreatedAt.get
  override def updatedAt: Date = UpdatedAt.get
}

object DynamicEntityWebhook extends DynamicEntityWebhook with LongKeyedMetaMapper[DynamicEntityWebhook] {
  override def dbTableName = "dynamic_entity_webhook"
  override def dbIndexes =
    UniqueIndex(WebhookId) ::
      UniqueIndex(DynamicEntityId, Url) ::
      Index(BankId, EntityName) ::
      super.dbIndexes
}
