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
import code.DynamicData.DynamicData
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.{CallContext, CustomJsonFormats, Glossary, NewStyle}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.api.v6_0_0._
import code.dynamicEntity.DynamicEntityCommons
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoints that manage Dynamic Entity definitions.
 *
 * Before v7.0.0 a definition was managed at one of two kinds of URL: `/management/system-dynamic-entities`
 * for the system space and `/management/banks/BANK_ID/dynamic-entities` for a bank, each with its own
 * Roles. Here there is one set of URLs, `/management/banks/BANK_ID/dynamic-entities`, and BANK_ID is
 * either a bank id or SYS, the bank id of the system space. The ResourceDocs declare allowSystemSpace()
 * so that the middleware lets SYS through with no bank resolved, and it checks each Definition Role at
 * the BANK_ID in the URL, SYS included. Every response carries `bank_id`, SYS for the system space.
 *
 * The endpoints live outside Http4s700 to keep that object's initialiser clear of the JVM's 64KB method
 * limit; Http4s700 adds [[resourceDocs]] to its own. The work itself is done by the v6.0.0 and v4.0.0
 * functions the older endpoints use, so the behaviour stays the same across versions.
 * See DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md, phase 6.
 */
object Http4s700DynamicEntityDefinitions {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  /**
   * The `_links` of a definition as v7.0.0 gives them: the v7.0.0 data URLs,
   * `/obp/v7.0.0/banks/BANK_ID/dynamic-entities/...` with SYS for the system space, in place of the
   * unversioned `/obp/dynamic-entity/[banks/BANK_ID/]...` the v6.0.0 factory builds.
   */
  private def v700Links(links: Option[DynamicEntityLinksJsonV600], bankId: Option[String]): Option[DynamicEntityLinksJsonV600] = {
    val unversionedPrefix = s"^/obp/${ApiVersion.`dynamic-entity`}(/banks/[^/]+)?"
    val v700Prefix = s"/obp/${implementedInApiVersion}/banks/${DynamicEntitySpace.bankIdOrSystem(bankId)}/dynamic-entities"
    links.map(l => l.copy(related = l.related.map(link =>
      link.copy(href = link.href.replaceFirst(unversionedPrefix, java.util.regex.Matcher.quoteReplacement(v700Prefix))))))
  }

  /** The definition as v7.0.0 returns it: `bank_id` always present (SYS for the system space), v7.0.0 links. */
  private def withBankId(definition: DynamicEntityDefinitionJsonV600): DynamicEntityDefinitionJsonV600 =
    definition.copy(
      bank_id = Some(DynamicEntitySpace.bankIdOrSystem(definition.bank_id)),
      _links = v700Links(definition._links, definition.bank_id))

  private val exampleSchema = com.openbankproject.commons.util.JsonAliases.parse(
    """{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference", "indexed": true}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}}}"""
  ).asInstanceOf[org.json4s.JsonAST.JObject]

  private val exampleDefinition = DynamicEntityDefinitionJsonV600(
    dynamic_entity_id = "abc-123-def",
    entity_name = "customer_preferences",
    user_id = "user-456",
    bank_id = Some("SYS"),
    has_personal_entity = true,
    schema = exampleSchema
  )

  private val spaceDescription =
    s"""BANK_ID is the space the definition lives in: the id of a bank, or `SYS` for the system space.
       |The Role is checked at that BANK_ID, so a Role granted at `SYS` covers the system space and nothing else.
       |Every response carries `bank_id`, `SYS` included.""".stripMargin

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities
  lazy val getDynamicEntityDefinitions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" =>
      EndpointHelpers.withUser(req) { (_, _) =>
        val bankId = DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl)
        for {
          dynamicEntities <- Future(NewStyle.function.getDynamicEntities(bankId, false))
        } yield {
          val entitiesWithCounts = dynamicEntities.sortBy(_.entityName).map { entity =>
            val commons: DynamicEntityCommons = entity
            val recordCount = DynamicData.count(
              By(DynamicData.DynamicEntityName, entity.entityName),
              By(DynamicData.IsPersonalEntity, false),
              By(DynamicData.BankId, DynamicEntitySpace.bankIdOrSystem(entity.bankId))
            )
            (commons, recordCount)
          }
          val listed = JSONFactory600.createDynamicEntitiesWithCountJson(entitiesWithCounts)
          listed.copy(dynamic_entities = listed.dynamic_entities.map(definition =>
            definition.copy(
              bank_id = Some(DynamicEntitySpace.bankIdOrSystem(definition.bank_id)),
              _links = v700Links(definition._links, definition.bank_id))))
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityDefinitions),
    "GET",
    "/management/banks/BANK_ID/dynamic-entities",
    "Get Dynamic Entity Definitions",
    s"""Get the Dynamic Entity definitions in one space, each with a `record_count` of its non-personal records.
       |
       |$spaceDescription
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
    EmptyBody,
    DynamicEntitiesWithCountJsonV600(List(DynamicEntityDefinitionWithCountJsonV600(
      dynamic_entity_id = "abc-123-def",
      entity_name = "customer_preferences",
      user_id = "user-456",
      bank_id = Some("SYS"),
      has_personal_entity = true,
      schema = exampleSchema,
      record_count = 42
    ))),
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityDefinitions :: Nil),
    http4sPartialFunction = Some(getDynamicEntityDefinitions)
  ).allowSystemSpace()

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities (201)
  lazy val createDynamicEntityDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" =>
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        val rawBody = cc.httpBody.getOrElse("")
        for {
          request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
            com.openbankproject.commons.util.JsonAliases.parse(rawBody).extract[CreateDynamicEntityRequestJsonV600]
          }
          _ <- Implementations6_0_0.validateEntityNameV600(request.entity_name, cc)
          dynamicEntity <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
            DynamicEntityCommons(JSONFactory600.convertV600RequestToInternal(request), None, cc.userId,
              DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl))
          }
          result <- Implementations6_0_0.createDynamicEntityV600(cc, dynamicEntity)
        } yield withBankId(result)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDynamicEntityDefinition),
    "POST",
    "/management/banks/BANK_ID/dynamic-entities",
    "Create Dynamic Entity Definition",
    s"""Create a Dynamic Entity definition in one space.
       |
       |$spaceDescription
       |
       |The request body is the same as v6.0.0's: `entity_name` in lowercase snake_case, the access flags
       |(`has_personal_entity`, `has_public_access`, `has_community_access`, `personal_requires_role`,
       |`use_row_level_access`, `auth_mode`) and a `schema` whose every property carries an `example`.
       |
       |The caller is granted the new entity's Record Roles (`CanCreateDynamicEntityRecord_<entity_name>` and
       |its Get, Update and Delete siblings) at the same BANK_ID.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")} and ${Glossary.getGlossaryItemLink("Dynamic-Entity-Access-Model")}""",
    CreateDynamicEntityRequestJsonV600(
      entity_name = "customer_preferences",
      has_personal_entity = Some(true),
      schema = exampleSchema
    ),
    exampleDefinition,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canCreateDynamicEntityDefinition :: Nil),
    authMode = UserOrApplication,
    http4sPartialFunction = Some(createDynamicEntityDefinition)
  ).allowSystemSpace()

  // Route: PUT /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID (200)
  lazy val updateDynamicEntityDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId =>
      EndpointHelpers.executeAndRespond(req) { implicit cc =>
        val rawBody = cc.httpBody.getOrElse("")
        for {
          request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
            com.openbankproject.commons.util.JsonAliases.parse(rawBody).extract[UpdateDynamicEntityRequestJsonV600]
          }
          _ <- Implementations6_0_0.validateEntityNameV600(request.entity_name, cc)
          internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
          dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId,
            DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl))
          result <- Implementations6_0_0.updateDynamicEntityV600(cc, dynamicEntity)
        } yield withBankId(result)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateDynamicEntityDefinition),
    "PUT",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
    "Update Dynamic Entity Definition",
    s"""Update a Dynamic Entity definition in one space. A definition in another space is not found, whatever its id.
       |
       |$spaceDescription
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
    UpdateDynamicEntityRequestJsonV600(
      entity_name = "customer_preferences",
      has_personal_entity = Some(true),
      schema = exampleSchema
    ),
    exampleDefinition,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
      DynamicEntityNotFoundByDynamicEntityId, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canUpdateDynamicEntityDefinition :: Nil),
    http4sPartialFunction = Some(updateDynamicEntityDefinition)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID (204)
  lazy val deleteDynamicEntityDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId =>
      EndpointHelpers.executeDelete(req) { cc =>
        Implementations4_0_0.deleteDynamicEntityImpl(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl), dynamicEntityId, cc)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteDynamicEntityDefinition),
    "DELETE",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
    "Delete Dynamic Entity Definition",
    s"""Delete a Dynamic Entity definition that has no records. To delete one together with its records,
       |use the cascade endpoint.
       |
       |$spaceDescription
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityOperationNotAllowed,
      DynamicEntityNotFoundByDynamicEntityId, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canDeleteDynamicEntityDefinition :: Nil),
    http4sPartialFunction = Some(deleteDynamicEntityDefinition)
  ).allowSystemSpace()

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/backup (201)
  lazy val backupDynamicEntityDefinition: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "backup" =>
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        Implementations6_0_0.backupDynamicEntityFut(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl), dynamicEntityId, cc)
          .map(withBankId)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(backupDynamicEntityDefinition),
    "POST",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/backup",
    "Backup Dynamic Entity Definition",
    s"""Copy a Dynamic Entity definition and all its records to a new entity in the same space, named with a
       |`_BAK` suffix (`_BAK2`, `_BAK3` and so on when that name is taken).
       |
       |The caller needs the entity's `CanGetDynamicEntityRecord_<entity_name>` Role as well, since the copy reads
       |every record, and is granted the same Role on the backup.
       |
       |$spaceDescription
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
    EmptyBody,
    exampleDefinition.copy(entity_name = "customer_preferences_BAK", has_personal_entity = false),
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canBackupDynamicEntityDefinition :: Nil),
    http4sPartialFunction = Some(backupDynamicEntityDefinition)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/cascade/DYNAMIC_ENTITY_ID (204)
  lazy val deleteDynamicEntityDefinitionCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / "cascade" / dynamicEntityId =>
      EndpointHelpers.executeDelete(req) { cc =>
        Implementations6_0_0.deleteDynamicEntityCascadeFut(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl), dynamicEntityId, cc)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteDynamicEntityDefinitionCascade),
    "DELETE",
    "/management/banks/BANK_ID/dynamic-entities/cascade/DYNAMIC_ENTITY_ID",
    "Delete Dynamic Entity Definition Cascade",
    s"""Delete a Dynamic Entity definition together with all its records.
       |
       |The definition and its records are first copied to an entity named with a `ZZ_BAK_` prefix in the same
       |space, overwriting an earlier `ZZ_BAK_` copy; an entity whose name already starts with `ZZ_BAK_` is not
       |copied again. Only an entity without personal (`my`) records can be deleted this way.
       |
       |$spaceDescription
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, CannotDeleteCascadePersonalEntity,
      DynamicEntityNotFoundByDynamicEntityId, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canDeleteCascadeDynamicEntityDefinition :: Nil),
    http4sPartialFunction = Some(deleteDynamicEntityDefinitionCascade)
  ).allowSystemSpace()
}
