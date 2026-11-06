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

import code.api.util.{APIUtil, DoobieUtil}
import code.users.{UserReference, Users}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
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
  /** The active webhooks of the entity named `entityName` in a space: what a record write fires. */
  def getActiveByEntityName(bankId: String, entityName: String): Box[List[DynamicEntityWebhookTrait]]
  /** The fields of a webhook's Dynamic Entity and their types, as its conditions are compared against. */
  def entityFieldTypes(dynamicEntityId: String): Map[String, String] =
    code.dynamicEntity.DynamicEntity.findById(dynamicEntityId)
      .map(DynamicEntityWebhookConditions.fieldTypes).openOr(Map.empty)
  /** The webhooks of a Dynamic Entity, by its id alone, which is unique across spaces. */
  def getByDynamicEntityId(dynamicEntityId: String): Box[List[DynamicEntityWebhookTrait]]
  /** This turns a webhook off for `reason`, without changing anything else about it. */
  def deactivate(webhookId: String, reason: String): Box[DynamicEntityWebhookTrait]
  /** This records a Dynamic Entity's new name on its webhooks, which look records up by name. */
  def renameEntity(dynamicEntityId: String, entityName: String): Box[Int]
  /** This deletes every webhook of a Dynamic Entity, with their undelivered requests. */
  def deleteByDynamicEntityId(dynamicEntityId: String): Box[Int]
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
    DynamicEntityWebhook.first(
      fr"WHERE bank_id = ${Option(bankId)} AND dynamic_entity_id = ${Option(dynamicEntityId)}" ++
        fr"AND webhook_id = ${Option(webhookId)}")

  override def create(bankId: String, dynamicEntityId: String, entityName: String, url: String, events: List[String],
                      fieldConditions: List[DynamicEntityWebhookFieldCondition], isActive: Boolean, description: String,
                      createdByUserId: String): Box[DynamicEntityWebhookTrait] = {
    val (creator, creatorOnBehalfOf) = creatorsOf(createdByUserId)
    tryo {
      val now = new Date()
      DynamicEntityWebhook.insert(DynamicEntityWebhook(
        webhookId = APIUtil.generateUUID(),
        bankId = bankId,
        dynamicEntityId = dynamicEntityId,
        entityName = entityName,
        url = url,
        eventsRaw = events.mkString(","),
        fieldConditionsRaw = DynamicEntityWebhookFieldCondition.toJsonString(fieldConditions),
        isActive = isActive,
        deactivatedReasonRaw = if (isActive) null else DeactivatedTurnedOff,
        descriptionRaw = description,
        createdByUserId = creator,
        createdByOnBehalfOfUserId = creatorOnBehalfOf,
        createdAt = now,
        updatedAt = now))
    }
  }

  override def get(bankId: String, dynamicEntityId: String, webhookId: String): Box[DynamicEntityWebhookTrait] =
    find(bankId, dynamicEntityId, webhookId)

  override def getByDynamicEntity(bankId: String, dynamicEntityId: String): Box[List[DynamicEntityWebhookTrait]] =
    tryo(DynamicEntityWebhook.query(
      fr"WHERE bank_id = ${Option(bankId)} AND dynamic_entity_id = ${Option(dynamicEntityId)}" ++
        fr"ORDER BY created_at ASC, id ASC"))

  override def getByBank(bankId: String): Box[List[DynamicEntityWebhookTrait]] =
    tryo(DynamicEntityWebhook.query(fr"WHERE bank_id = ${Option(bankId)} ORDER BY entity_name ASC, id ASC"))

  override def getByUrl(bankId: String, dynamicEntityId: String, url: String): Box[DynamicEntityWebhookTrait] =
    DynamicEntityWebhook.first(
      fr"WHERE bank_id = ${Option(bankId)} AND dynamic_entity_id = ${Option(dynamicEntityId)}" ++
        fr"AND url = ${Option(url)}")

  override def update(bankId: String, dynamicEntityId: String, webhookId: String, url: String, events: List[String],
                      fieldConditions: List[DynamicEntityWebhookFieldCondition], isActive: Boolean,
                      deactivatedReason: Option[String], description: String): Box[DynamicEntityWebhookTrait] =
    find(bankId, dynamicEntityId, webhookId).flatMap { row =>
      tryo {
        DynamicEntityWebhook.update(row.copy(
          url = url,
          eventsRaw = events.mkString(","),
          fieldConditionsRaw = DynamicEntityWebhookFieldCondition.toJsonString(fieldConditions),
          isActive = isActive,
          deactivatedReasonRaw = deactivatedReason.orNull,
          descriptionRaw = description,
          updatedAt = new Date()))
      }
    }

  // Requests still waiting to be delivered go with the webhook: nobody asked for them any more.
  override def delete(bankId: String, dynamicEntityId: String, webhookId: String): Box[Boolean] =
    find(bankId, dynamicEntityId, webhookId).flatMap { row =>
      tryo {
        code.messageoutbox.WebhookOutbox.discardPending(webhookId)
        DynamicEntityWebhook.delete(row.webhookId)
      }
    }
  override def getActiveByEntityName(bankId: String, entityName: String): Box[List[DynamicEntityWebhookTrait]] =
    tryo(DynamicEntityWebhook.query(
      fr"WHERE bank_id = ${Option(bankId)} AND entity_name = ${Option(entityName)} AND is_active = ${true}" ++
        fr"ORDER BY id ASC"))

  override def getByDynamicEntityId(dynamicEntityId: String): Box[List[DynamicEntityWebhookTrait]] =
    tryo(DynamicEntityWebhook.findByDynamicEntityId(dynamicEntityId))

  override def deactivate(webhookId: String, reason: String): Box[DynamicEntityWebhookTrait] =
    DynamicEntityWebhook.first(fr"WHERE webhook_id = ${Option(webhookId)}").flatMap { row =>
      tryo(DynamicEntityWebhook.update(row.copy(isActive = false, deactivatedReasonRaw = reason, updatedAt = new Date())))
    }

  override def renameEntity(dynamicEntityId: String, entityName: String): Box[Int] =
    tryo(DynamicEntityWebhook.findByDynamicEntityId(dynamicEntityId)
      .filter(_.entityName != entityName)
      .map(row => DynamicEntityWebhook.update(row.copy(entityName = entityName, updatedAt = new Date())))
      .size)

  override def deleteByDynamicEntityId(dynamicEntityId: String): Box[Int] =
    tryo(DynamicEntityWebhook.findByDynamicEntityId(dynamicEntityId).count { row =>
      code.messageoutbox.WebhookOutbox.discardPending(row.webhookId)
      DynamicEntityWebhook.delete(row.webhookId)
    })
}

/**
 * One row of dynamic_entity_webhook. Upstream declares this as a Lift Mapper entity; here it is a
 * Doobie store over the table the changelog creates (db.changelog-develop-merge-3.yaml), unique on
 * webhook_id and on (dynamic_entity_id, url).
 *
 * bank_id is SYS for the system space, never NULL: see DynamicEntitySpace. entity_name is kept beside
 * the id because a record write knows the entity by name, and looks webhooks up by it. events is a
 * comma separated list of CREATED, UPDATED and DELETED; field_conditions a JSON array of
 * DynamicEntityWebhookFieldCondition.
 */
case class DynamicEntityWebhook(
  webhookId: String,
  bankId: String,
  dynamicEntityId: String,
  entityName: String,
  url: String,
  private[dynamicentitywebhook] val eventsRaw: String,
  private[dynamicentitywebhook] val fieldConditionsRaw: String,
  isActive: Boolean,
  private[dynamicentitywebhook] val deactivatedReasonRaw: String,
  private[dynamicentitywebhook] val descriptionRaw: String,
  createdByUserId: String,
  createdByOnBehalfOfUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends DynamicEntityWebhookTrait {
  override def events: List[String] = Option(eventsRaw).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
  override def fieldConditions: List[DynamicEntityWebhookFieldCondition] = DynamicEntityWebhookFieldCondition.fromJsonString(fieldConditionsRaw)
  override def deactivatedReason: Option[String] = Option(deactivatedReasonRaw).filter(_.nonEmpty)
  override def description: String = Option(descriptionRaw).getOrElse("")
}

object DynamicEntityWebhook {

  private val selectColumns =
    fr"""SELECT webhook_id, bank_id, dynamic_entity_id, entity_name, url, events, field_conditions, is_active,
                deactivated_reason, description, created_by_user_id, created_by_on_behalf_of_user_id,
                created_at, updated_at
         FROM dynamic_entity_webhook"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[Boolean], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def ts(d: Date): Option[java.sql.Timestamp] = Option(d).map(x => new java.sql.Timestamp(x.getTime))

  private def fromRow(r: Row): DynamicEntityWebhook =
    DynamicEntityWebhook(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull, r._6.orNull,
      r._7.orNull, r._8.getOrElse(false), r._9.orNull, r._10.orNull, r._11.orNull, r._12.orNull,
      readDate(r._13), readDate(r._14))

  private[dynamicentitywebhook] def query(condition: Fragment): List[DynamicEntityWebhook] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private[dynamicentitywebhook] def first(condition: Fragment): Box[DynamicEntityWebhook] =
    query(condition ++ fr"ORDER BY id ASC").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByWebhookId(webhookId: String): Box[DynamicEntityWebhook] =
    first(fr"WHERE webhook_id = ${Option(webhookId)}")

  def findByDynamicEntityId(dynamicEntityId: String): List[DynamicEntityWebhook] =
    query(fr"WHERE dynamic_entity_id = ${Option(dynamicEntityId)} ORDER BY id ASC")

  def insert(w: DynamicEntityWebhook): DynamicEntityWebhook = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamic_entity_webhook (webhook_id, bank_id, dynamic_entity_id, entity_name, url, events,
              field_conditions, is_active, deactivated_reason, description, created_by_user_id,
              created_by_on_behalf_of_user_id, created_at, updated_at)
            VALUES (${Option(w.webhookId)}, ${Option(w.bankId)}, ${Option(w.dynamicEntityId)}, ${Option(w.entityName)},
              ${Option(w.url)}, ${Option(w.eventsRaw)}, ${Option(w.fieldConditionsRaw)}, ${w.isActive},
              ${Option(w.deactivatedReasonRaw)}, ${Option(w.descriptionRaw)}, ${Option(w.createdByUserId)},
              ${Option(w.createdByOnBehalfOfUserId)}, ${ts(w.createdAt)}, ${ts(w.updatedAt)})""".update.run)
    w
  }

  /** Rewrites the settings of the row with this webhook id; the creator and creation time never change. */
  def update(w: DynamicEntityWebhook): DynamicEntityWebhook = {
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamic_entity_webhook SET bank_id = ${Option(w.bankId)},
              dynamic_entity_id = ${Option(w.dynamicEntityId)}, entity_name = ${Option(w.entityName)},
              url = ${Option(w.url)}, events = ${Option(w.eventsRaw)}, field_conditions = ${Option(w.fieldConditionsRaw)},
              is_active = ${w.isActive}, deactivated_reason = ${Option(w.deactivatedReasonRaw)},
              description = ${Option(w.descriptionRaw)}, updated_at = ${ts(w.updatedAt)}
            WHERE webhook_id = ${Option(w.webhookId)}""".update.run)
    w
  }

  def delete(webhookId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamic_entity_webhook WHERE webhook_id = ${Option(webhookId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamic_entity_webhook".update.run)
    ()
  }
}
