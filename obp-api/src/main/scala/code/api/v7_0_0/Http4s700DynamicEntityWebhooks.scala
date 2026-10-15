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

import cats.effect.IO
import code.api.Constant.ApiPathZero
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CallContext, CustomJsonFormats, Glossary}
import code.dynamicEntity.{DynamicEntityProvider, DynamicEntityT}
import code.dynamicentitywebhook.{DynamicEntityWebhookConditions, DynamicEntityWebhookFieldCondition, DynamicEntityWebhookSigning, DynamicEntityWebhookTrait, DynamicEntityWebhookUrlCheck, DynamicEntityWebhooks}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoints that manage Dynamic Entity webhooks: URLs OBP sends a request to
 * when records of one Dynamic Entity are created, updated or deleted through the endpoints (see
 * [[code.dynamicentitywebhook.DynamicEntityWebhooks]] and the Glossary item "Dynamic Entity Webhooks").
 *
 * As for the v7.0.0 Dynamic Entity definitions, BANK_ID is a bank's id or SYS for the system space; the
 * ResourceDocs declare allowSystemSpace() so the middleware lets SYS through and checks each Role at the
 * BANK_ID in the URL. A webhook belongs to one Dynamic Entity of that space, named by DYNAMIC_ENTITY_ID; an
 * entity of another space is not found, whatever its id.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700DynamicEntityWebhooks {
  import DynamicEntityWebhooks._

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val MaxDescriptionLength = 2000

  private def provider = DynamicEntityWebhooks.dynamicEntityWebhookProvider.vend

  /** The space as webhooks store it: a bank id, or SYS for the system space. */
  private def space(bankIdInUrl: String): String =
    DynamicEntitySpace.bankIdOrSystem(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl))

  private def fullOrFail[T: Manifest](box: Box[T], cc: CallContext): T =
    code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400)

  /** The Dynamic Entity DYNAMIC_ENTITY_ID of the space BANK_ID, or a 404. */
  private def entityFound(bankIdInUrl: String, dynamicEntityId: String, cc: CallContext): Future[DynamicEntityT] =
    Future(DynamicEntityProvider.connectorMethodProvider.vend.getById(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl), dynamicEntityId))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc),
        s"$DynamicEntityNotFoundByDynamicEntityId Current DYNAMIC_ENTITY_ID is $dynamicEntityId.", 404))

  /** The webhook WEBHOOK_ID of that Dynamic Entity, or a 404. */
  private def webhookFound(spaceId: String, dynamicEntityId: String, webhookId: String, cc: CallContext): Future[DynamicEntityWebhookTrait] =
    Future(provider.get(spaceId, dynamicEntityId, webhookId))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), s"$DynamicEntityWebhookNotFound Current WEBHOOK_ID is $webhookId.", 404))

  private case class Checked(url: String, events: List[String], conditions: List[DynamicEntityWebhookFieldCondition], description: String)

  /**
   * The checks a webhook's settings must pass, on create and on update (`webhookId` is the one being
   * updated, which may keep its own URL): the events, the field conditions against the entity's current
   * fields, the description's length, the URL (see DynamicEntityWebhookUrlCheck), and that no other webhook
   * of the entity has the same URL.
   */
  private def check(spaceId: String, entity: DynamicEntityT, url: String, events: List[String],
                    conditions: List[DynamicEntityWebhookFieldCondition], description: String,
                    webhookId: Option[String], cc: CallContext): Future[Checked] = {
    val trimmedUrl = Option(url).map(_.trim).getOrElse("")
    val eventsProblem = DynamicEntityWebhookConditions.eventsProblem(events)
    lazy val conditionsProblem = DynamicEntityWebhookConditions.conditionsProblem(conditions, events, entity)
    lazy val urlProblem = DynamicEntityWebhookUrlCheck.problem(trimmedUrl)
    lazy val sameUrl = provider.getByUrl(spaceId, entity.dynamicEntityId.getOrElse(""), trimmedUrl).toOption
      .filterNot(other => webhookId.contains(other.webhookId))
    for {
      _ <- Helper.booleanToFuture(s"$InvalidDynamicEntityWebhookEvents${eventsProblem.getOrElse("")}", 400, Some(cc)) {
        eventsProblem.isEmpty
      }
      _ <- Helper.booleanToFuture(s"$InvalidDynamicEntityWebhookFieldCondition${conditionsProblem.getOrElse("")}", 400, Some(cc)) {
        conditionsProblem.isEmpty
      }
      _ <- Helper.booleanToFuture(InvalidDynamicEntityWebhookDescription, 400, Some(cc)) {
        description.length <= MaxDescriptionLength
      }
      _ <- Helper.booleanToFuture(s"$InvalidDynamicEntityWebhookUrl${urlProblem.getOrElse("")}", 400, Some(cc)) {
        urlProblem.isEmpty
      }
      _ <- Helper.booleanToFuture(s"$DynamicEntityWebhookUrlAlreadyExists Current WEBHOOK_ID with this url is ${sameUrl.map(_.webhookId).getOrElse("")}.", 409, Some(cc)) {
        sameUrl.isEmpty
      }
    } yield Checked(trimmedUrl, events, conditions, description)
  }

  private val webhooksDescription =
    s"""A Dynamic Entity webhook makes OBP send a POST request to a URL when records of one Dynamic Entity are
       |created, updated or deleted through the endpoints: the Dynamic Entity endpoints, Domain APIs, Dynamic
       |Endpoints and Dynamic Resource Docs. Changes to the entity's definition, backups, restores and writes
       |that do not go through the endpoints send nothing.
       |
       |The request carries identifiers only (the event, the bank, the entity name and the record id), never the
       |record's values: a receiver that needs the record fetches it with its own credentials, so the normal access
       |checks apply when it reads. Delivery is at least once and in no particular order; each event carries an
       |`event_id` to drop duplicates by.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.
       |
       |`events` names any of `CREATED`, `UPDATED` and `DELETED`. `field_conditions` narrows `UPDATED`: each
       |condition names a field of the entity and, optionally, the value it must change `from` and the value it
       |must change `to`, and an update fires the webhook when at least one condition matches. `{"field": "amount"}`
       |matches any change to `amount`. Conditions do not apply to `CREATED` or `DELETED`.
       |
       |`url` must be an absolute `https` URL. It may not point at this OBP instance, nor at a loopback, private,
       |link-local or cloud metadata address. An entity has at most one webhook per URL.
       |
       |`signature_status` says whether deliveries from this instance are signed: on this instance it is
       |`${DynamicEntityWebhookSigning.signatureStatus}`. A signed delivery carries `OBP-Signature`, a JSON Web
       |Signature over the request body made with the key published at `GET /obp/v3.1.0/certs`.
       |
       |`deactivated_reason` is present only while the webhook is not active: `TURNED_OFF` when someone set
       |`is_active` false, `CIRCUIT_BREAKER` when it fired too often, and `ENTITY_FIELD_REMOVED` or
       |`ENTITY_FIELD_TYPE_CHANGED` when the entity's definition changed under one of its field conditions.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic Entity Webhooks")}""".stripMargin

  private val errorsOnWrite = List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
    DynamicEntityNotFoundByDynamicEntityId, InvalidDynamicEntityWebhookEvents, InvalidDynamicEntityWebhookFieldCondition,
    InvalidDynamicEntityWebhookDescription, InvalidDynamicEntityWebhookUrl, DynamicEntityWebhookUrlAlreadyExists, UnknownError)

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks (201)
  lazy val createDynamicEntityWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "web-hooks" =>
      EndpointHelpers.withUserAndBodyCreated[PostDynamicEntityWebhookJsonV700, DynamicEntityWebhookJsonV700](req) { (user, body, cc) =>
        val spaceId = space(bankIdInUrl)
        for {
          entity <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          checked <- check(spaceId, entity, body.url, Option(body.events).getOrElse(Nil),
            body.field_conditions.getOrElse(Nil).map(JSONFactory700DynamicEntityWebhooks.toCondition),
            body.description.map(_.trim).getOrElse(""), None, cc)
          created <- Future(provider.create(spaceId, dynamicEntityId, entity.entityName, checked.url, checked.events,
            checked.conditions, body.is_active.getOrElse(true), checked.description, user.userId))
            .map(fullOrFail(_, cc))
        } yield JSONFactory700DynamicEntityWebhooks.createWebhookJson(created)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDynamicEntityWebhook),
    "POST",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks",
    "Create Dynamic Entity Webhook",
    s"""Register a URL that OBP sends a request to when records of a Dynamic Entity change.
       |
       |Only `url` and `events` are required. `field_conditions` defaults to none, `is_active` to true and
       |`description` to empty.
       |
       |$webhooksDescription""".stripMargin,
    JSONFactory700DynamicEntityWebhooks.postDynamicEntityWebhookJsonV700Example,
    JSONFactory700DynamicEntityWebhooks.dynamicEntityWebhookJsonV700Example,
    errorsOnWrite,
    apiTagManageDynamicEntity :: apiTagWebhook :: apiTagApi :: Nil,
    Some(canCreateDynamicEntityWebhook :: Nil),
    http4sPartialFunction = Some(createDynamicEntityWebhook)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks
  lazy val getDynamicEntityWebhooks: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "web-hooks" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          _ <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          webhooks <- Future(provider.getByDynamicEntity(space(bankIdInUrl), dynamicEntityId)).map(fullOrFail(_, cc))
        } yield DynamicEntityWebhooksJsonV700(webhooks.map(JSONFactory700DynamicEntityWebhooks.createWebhookJson))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityWebhooks),
    "GET",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks",
    "Get Dynamic Entity Webhooks",
    s"""The webhooks of one Dynamic Entity, oldest first.
       |
       |$webhooksDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityWebhooks.dynamicEntityWebhooksJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId, UnknownError),
    apiTagManageDynamicEntity :: apiTagWebhook :: apiTagApi :: Nil,
    Some(canGetDynamicEntityWebhooks :: Nil),
    http4sPartialFunction = Some(getDynamicEntityWebhooks)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entity-web-hooks
  lazy val getDynamicEntityWebhooksAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entity-web-hooks" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        Future(provider.getByBank(space(bankIdInUrl))).map(box =>
          DynamicEntityWebhooksJsonV700(fullOrFail(box, cc).map(JSONFactory700DynamicEntityWebhooks.createWebhookJson)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityWebhooksAtBank),
    "GET",
    "/management/banks/BANK_ID/dynamic-entity-web-hooks",
    "Get Dynamic Entity Webhooks at Bank",
    s"""The webhooks of every Dynamic Entity of a space, ordered by entity name.
       |
       |$webhooksDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityWebhooks.dynamicEntityWebhooksJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    apiTagManageDynamicEntity :: apiTagWebhook :: apiTagApi :: Nil,
    Some(canGetDynamicEntityWebhooks :: Nil),
    http4sPartialFunction = Some(getDynamicEntityWebhooksAtBank)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks/WEBHOOK_ID
  lazy val getDynamicEntityWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "web-hooks" / webhookId =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          _ <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          webhook <- webhookFound(space(bankIdInUrl), dynamicEntityId, webhookId, cc)
        } yield JSONFactory700DynamicEntityWebhooks.createWebhookJson(webhook)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityWebhook),
    "GET",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks/WEBHOOK_ID",
    "Get Dynamic Entity Webhook",
    s"""One webhook of a Dynamic Entity.
       |
       |$webhooksDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityWebhooks.dynamicEntityWebhookJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId,
      DynamicEntityWebhookNotFound, UnknownError),
    apiTagManageDynamicEntity :: apiTagWebhook :: apiTagApi :: Nil,
    Some(canGetDynamicEntityWebhooks :: Nil),
    http4sPartialFunction = Some(getDynamicEntityWebhook)
  ).allowSystemSpace()

  // Route: PUT /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks/WEBHOOK_ID
  lazy val updateDynamicEntityWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "web-hooks" / webhookId =>
      EndpointHelpers.withUserAndBody[PutDynamicEntityWebhookJsonV700, DynamicEntityWebhookJsonV700](req) { (_, body, cc) =>
        val spaceId = space(bankIdInUrl)
        for {
          entity <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          existing <- webhookFound(spaceId, dynamicEntityId, webhookId, cc)
          // The conditions are checked against the entity on every update, kept or not, so a webhook the
          // entity's definition deactivated cannot be turned back on until its conditions fit again.
          checked <- check(spaceId, entity, body.url.getOrElse(existing.url), body.events.getOrElse(existing.events),
            body.field_conditions.map(_.map(JSONFactory700DynamicEntityWebhooks.toCondition)).getOrElse(existing.fieldConditions),
            body.description.map(_.trim).getOrElse(existing.description), Some(webhookId), cc)
          isActive = body.is_active.getOrElse(existing.isActive)
          // Active: no reason. Turned off now: TURNED_OFF. Already inactive and left so: the reason it had.
          deactivatedReason = if (isActive) None
            else if (existing.isActive) Some(DeactivatedTurnedOff)
            else existing.deactivatedReason.orElse(Some(DeactivatedTurnedOff))
          updated <- Future(provider.update(spaceId, dynamicEntityId, webhookId, checked.url, checked.events,
            checked.conditions, isActive, deactivatedReason, checked.description))
            .map(fullOrFail(_, cc))
        } yield JSONFactory700DynamicEntityWebhooks.createWebhookJson(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateDynamicEntityWebhook),
    "PUT",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks/WEBHOOK_ID",
    "Update Dynamic Entity Webhook",
    s"""Change a webhook of a Dynamic Entity. Every field is optional, and a field left out keeps its current
       |value; an empty `field_conditions` list removes the conditions. The same checks as on create apply, and
       |the field conditions are checked against the entity's current definition even when they are left out.
       |
       |Setting `is_active` to true turns the webhook back on and clears `deactivated_reason`; setting it to false
       |turns it off with `deactivated_reason` `TURNED_OFF`.
       |
       |$webhooksDescription""".stripMargin,
    JSONFactory700DynamicEntityWebhooks.putDynamicEntityWebhookJsonV700Example,
    JSONFactory700DynamicEntityWebhooks.dynamicEntityWebhookJsonV700Example,
    DynamicEntityWebhookNotFound :: errorsOnWrite,
    apiTagManageDynamicEntity :: apiTagWebhook :: apiTagApi :: Nil,
    Some(canUpdateDynamicEntityWebhook :: Nil),
    http4sPartialFunction = Some(updateDynamicEntityWebhook)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks/WEBHOOK_ID (204)
  lazy val deleteDynamicEntityWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "web-hooks" / webhookId =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        val spaceId = space(bankIdInUrl)
        for {
          _ <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          _ <- webhookFound(spaceId, dynamicEntityId, webhookId, cc)
          deleted <- Future(provider.delete(spaceId, dynamicEntityId, webhookId)).map(fullOrFail(_, cc))
        } yield deleted
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteDynamicEntityWebhook),
    "DELETE",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/web-hooks/WEBHOOK_ID",
    "Delete Dynamic Entity Webhook",
    s"""Delete a webhook of a Dynamic Entity. No further events are queued for it.
       |
       |$webhooksDescription""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId,
      DynamicEntityWebhookNotFound, UnknownError),
    apiTagManageDynamicEntity :: apiTagWebhook :: apiTagApi :: Nil,
    Some(canDeleteDynamicEntityWebhook :: Nil),
    http4sPartialFunction = Some(deleteDynamicEntityWebhook)
  ).allowSystemSpace()
}
