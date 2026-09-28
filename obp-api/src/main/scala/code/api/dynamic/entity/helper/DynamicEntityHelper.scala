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

package code.api.dynamic.entity.helper

import code.api.util.APIUtil.{EmptyBody, ResourceDoc, userAuthenticationMessage}
import code.api.util.ApiRole.getOrCreateDynamicApiRole
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{InvalidJsonFormat, UnknownError, UserHasMissingRoles, AuthenticatedUserIsRequired, ConsentMyResourcesMissing}
import code.api.util._
import com.openbankproject.commons.model.enums.{DynamicEntityFieldType, DynamicEntityOperation}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.json4s.JsonDSL._
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
 * The path segments a Dynamic Entity URL may start with, and the space they name.
 *
 * A URL either names a bank — `/banks/BANK_ID/...` — or names nothing, which means the system space.
 * Every extractor below used to write both forms out, once with `None` and once with `Some(bankId)`,
 * which is why there were twice as many cases as shapes. Splitting the space off first leaves one
 * case per shape, and is the same collapse the Roles and the storage have already had.
 */
private object SpaceSegments {
  /** Split a leading `banks/BANK_ID` off the path. None as the space means the system space. */
  def unapply(url: List[String]): Option[(Option[String], List[String])] = url match {
    case "banks" :: bankId :: rest => Some((Some(bankId), rest))
    case rest                      => Some((None, rest))
  }
}

/** Does a definition for this entity exist in this space? */
private object DefinitionIn {
  def apply(space: Option[String], entityName: String): Boolean =
    DynamicEntityHelper.definitionOf(space, entityName).isDefined

  /** As above, and the definition must also satisfy `predicate` — a flag such as hasPublicAccess. */
  def apply(space: Option[String], entityName: String, predicate: DynamicEntityInfo => Boolean): Boolean =
    DynamicEntityHelper.definitionOf(space, entityName).exists(predicate)
}

object EntityName {
  // unapply result structure: (BankId, entityName, id, isPersonalEntity)
  def unapply(url: List[String]): Option[(Option[String], String, String, Boolean)] = url match {
    case SpaceSegments(space, rest) => rest match {
      //eg: /FooBar21 or /banks/BANK_ID/FooBar21
      case entityName :: Nil if DefinitionIn(space, entityName) =>
        Some((space, entityName, "", false))
      //eg: /FooBar21/FOO_BAR21_ID or /banks/BANK_ID/FooBar21/FOO_BAR21_ID
      case entityName :: id :: Nil if DefinitionIn(space, entityName) =>
        Some((space, entityName, id, false))
      //eg: /my/FooBar21 or /banks/BANK_ID/my/FooBar21
      case "my" :: entityName :: Nil if DefinitionIn(space, entityName, _.hasPersonalEntity) =>
        Some((space, entityName, "", true))
      //eg: /my/FooBar21/FOO_BAR21_ID or /banks/BANK_ID/my/FooBar21/FOO_BAR21_ID
      case "my" :: entityName :: id :: Nil if DefinitionIn(space, entityName, _.hasPersonalEntity) =>
        Some((space, entityName, id, true))
      case _ => None
    }
    case _ => None
  }
}

object PublicEntityName {
  // unapply result structure: (BankId, entityName, id). Only matches hasPublicAccess = true
  def unapply(url: List[String]): Option[(Option[String], String, String)] = url match {
    case SpaceSegments(space, rest) => rest match {
      //eg: /public/FooBar21 or /banks/BANK_ID/public/FooBar21
      case "public" :: entityName :: Nil if DefinitionIn(space, entityName, _.hasPublicAccess) =>
        Some((space, entityName, ""))
      //eg: /public/FooBar21/FOO_BAR21_ID or /banks/BANK_ID/public/FooBar21/FOO_BAR21_ID
      case "public" :: entityName :: id :: Nil if DefinitionIn(space, entityName, _.hasPublicAccess) =>
        Some((space, entityName, id))
      case _ => None
    }
    case _ => None
  }
}

object CommunityEntityName {
  // unapply result structure: (BankId, entityName, id). Only matches hasCommunityAccess = true
  def unapply(url: List[String]): Option[(Option[String], String, String)] = url match {
    case SpaceSegments(space, rest) => rest match {
      //eg: /community/FooBar21 or /banks/BANK_ID/community/FooBar21
      case "community" :: entityName :: Nil if DefinitionIn(space, entityName, _.hasCommunityAccess) =>
        Some((space, entityName, ""))
      //eg: /community/FooBar21/FOO_BAR21_ID or /banks/BANK_ID/community/FooBar21/FOO_BAR21_ID
      case "community" :: entityName :: id :: Nil if DefinitionIn(space, entityName, _.hasCommunityAccess) =>
        Some((space, entityName, id))
      case _ => None
    }
    case _ => None
  }
}

/**
 * Row-level access (ACL) management routes: `.../<Entity>/<id>/access[/<userId>]`.
 * Result: (bankId, entityName, dataId, grantedUserIdOpt). Matches whenever the entity exists
 * (row-level or not); the handler returns 400 when the entity isn't row-level. See §6.
 */
object EntityAccessName {
  def unapply(url: List[String]): Option[(Option[String], String, String, Option[String])] = url match {
    case SpaceSegments(space, rest) => rest match {
      //eg: /FooBar21/FOO_BAR21_ID/access or /banks/BANK_ID/FooBar21/FOO_BAR21_ID/access
      case entityName :: id :: "access" :: Nil if DefinitionIn(space, entityName) =>
        Some((space, entityName, id, None))
      //eg: /FooBar21/FOO_BAR21_ID/access/USER_ID (with or without the bank segment)
      case entityName :: id :: "access" :: userId :: Nil if DefinitionIn(space, entityName) =>
        Some((space, entityName, id, Some(userId)))
      case _ => None
    }
    case _ => None
  }
}

object DynamicEntityHelper {

  /**
   * DE_indexing: may this definition update be applied to an entity that already has rows?
   *
   * The stored rows stay valid when the entity name, the set of property names and each property's `type`
   * are unchanged and `required` does not grow. Everything else — `indexed`, `index`, `example`,
   * `description`, `minLength`, `maxLength`, `read_role*`, `write_role*` — may change freely; in
   * particular this is what lets an operator switch indexing on for an existing, populated entity
   * (the projection backfill then does the rest). Unparseable input is treated as incompatible.
   */
  def isSchemaCompatibleChange(oldEntityName: String, oldMetadataJson: String,
                               newEntityName: String, newMetadataJson: String): Boolean = {
    // The stored metadataJson is the whole definition request, i.e. `{"<EntityName>": {"properties": ...}}`
    // (DynamicEntityCommons.apply keeps the outer object). Accept the bare inner object too.
    def definitionOf(metadataJson: String, entityName: String): Option[JValue] =
      scala.util.Try(parse(metadataJson)).toOption.flatMap {
        case root: JObject =>
          (root \ entityName) match {
            case inner: JObject => Some(inner)
            case _ if (root \ "properties").isInstanceOf[JObject] => Some(root)
            case _ => None
          }
        case _ => None
      }
    def propertyTypes(definition: JValue): Map[String, String] =
      (definition \ "properties") match {
        case props: JObject => props.obj.map { case JField(name, propDef) =>
          name -> ((propDef \ "type") match { case JString(t) => t; case _ => "" })
        }.toMap
        case _ => Map.empty[String, String]
      }
    def requiredNames(definition: JValue): Set[String] =
      (definition \ "required") match {
        case JArray(items) => items.collect { case JString(n) => n }.toSet
        case _ => Set.empty[String]
      }

    oldEntityName == newEntityName && {
      (definitionOf(oldMetadataJson, oldEntityName), definitionOf(newMetadataJson, newEntityName)) match {
        case (Some(oldDef), Some(newDef)) =>
          propertyTypes(oldDef) == propertyTypes(newDef) && requiredNames(newDef).subsetOf(requiredNames(oldDef))
        case _ => false
      }
    }
  }
  private val implementedInApiVersion = ApiVersion.v4_0_0

  // Keyed by (bank id as published, entity name): SYS for the system space, never None or "".
  def definitionsMap: Map[(String, String), DynamicEntityInfo] = NewStyle.function.getDynamicEntities(None, true).map(it => ((DynamicEntitySpace.bankIdOrSystem(it.bankId), it.entityName), DynamicEntityInfo(it.metadataJson, it.entityName, it.bankId, it.hasPersonalEntity, it.hasPublicAccess, it.hasCommunityAccess, it.personalRequiresRole, it.useRowLevelAccess, it.authMode))).toMap

  /**
   * The definition of one entity in one space, or None when that space holds no such entity.
   *
   * Callers ask by space and name rather than reaching into [[definitionsMap]], so that the shape of
   * the key stays an implementation detail. `bankId` is the space, with None for the system space; the
   * map itself is keyed by the published form, SYS.
   */
  def definitionOf(bankId: Option[String], entityName: String): Option[DynamicEntityInfo] =
    definitionsMap.get((DynamicEntitySpace.bankIdOrSystem(bankId), entityName))

  def dynamicEntityRoles: List[String] = NewStyle.function.getDynamicEntities(None, true).flatMap { dEntity =>
    val baseRoles = DynamicEntityInfo.roleNames(dEntity.entityName, dEntity.bankId)
    // Per-field write/read roles for any restricted fields (explicit shared role, or auto-generated).
    val writeRoles = dEntity.writeRestrictedFields.map(f =>
      DynamicEntityInfo.fieldWriteRole(dEntity.entityName, f, dEntity.bankId, dEntity.explicitWriteRole(f)).toString())
    val readRoles = dEntity.readRestrictedFields.map(f =>
      DynamicEntityInfo.fieldReadRole(dEntity.entityName, f, dEntity.bankId, dEntity.explicitReadRole(f)).toString())
    baseRoles ++ writeRoles ++ readRoles
  }.distinct

  def doc: ArrayBuffer[ResourceDoc] = {
    val docs = operationToResourceDoc.values.toList
    collection.mutable.ArrayBuffer(docs:_*)
  }

  /**
   * The v7.0.0 docs of every entity's endpoints, at `/banks/BANK_ID/dynamic-entities/...` with the
   * entity's own bank id, SYS for the system space. The same operations as [[doc]], with ids of the
   * form `OBPv7.0.0-dynamicEntity_create<entity>_<bank id>`.
   *
   * These are kept out of [[doc]] on purpose. [[doc]] is what the rest of OBP resolves operation ids
   * and partial function names against (request dispatch, interceptors, metrics, validations), and a
   * v7.0.0 doc shares its partial function name with the v4.0.0 one, so mixing them in would change
   * which id those lookups return. Only the v7.0.0 resource-docs listing serves these.
   */
  def v700Doc: List[ResourceDoc] = docsIn(ApiVersion.v7_0_0).values.toList

  /**
   * The v7.0.0 URL of an entity's endpoints as a doc template: `/banks/BANK_ID/dynamic-entities`
   * followed by what follows `/obp/dynamic-entity/[banks/BANK_ID/]` in the unversioned URL.
   */
  def v700UrlPrefix(bankId: Option[String]): String =
    s"/banks/${DynamicEntitySpace.bankIdOrSystem(bankId)}/dynamic-entities"

  def createEntityId(entityName: String) = {
    // (?<=[a-z0-9])(?=[A-Z]) --> mean `Positive Lookbehind (?<=[a-z0-9])` && Positive Lookahead (?=[A-Z]) --> So we can find the space to replace to  `_`
    val regexPattern = "(?<=[a-z0-9])(?=[A-Z])|-"
    // eg: entityName = PetEntity => entityIdName = pet_entity_id
    s"${entityName}_Id".replaceAll(regexPattern, "_").toLowerCase
  }

  def operationToResourceDoc: Map[(DynamicEntityOperation, String), ResourceDoc] = docsIn(implementedInApiVersion)

  /** Every entity's docs in one API version: v4.0.0 for the unversioned URLs, v7.0.0 for the v7.0.0 ones. */
  private def docsIn(apiVersion: ScannedApiVersion): Map[(DynamicEntityOperation, String), ResourceDoc] = {
    val addPrefix = APIUtil.getPropsAsBoolValue("dynamic_entities_have_prefix", true)

    // record exists tag names, to avoid duplicated dynamic tag name.
    var existsTagNames = ApiTag.staticTagNames
    // match string that start with _, e.g: "_abc"
    val Regex = "(_+)(.+)".r


    //convert entity name to tag name, example:
    //    Csem-case -> Csem Case
    //    _Csem-case -> _Csem Case
    //    Csem_case -> Csem Case
    //    _Csem_case -> _Csem Case
    //    csem-case -> Csem Case
    def prettyTagName(s: String) = s

    def apiTag(entityName: String, singularName: String): ResourceDocTag = {

      val existsSameStaticEntity: Boolean = existsTagNames
        .exists(it => it.equalsIgnoreCase(singularName) || it.equalsIgnoreCase(entityName))


      val tagName = if(addPrefix || existsSameStaticEntity) {
        var name = singularName match {
          case Regex(a,b) => s"$a${b.capitalize}"
          case v => s"_${v.capitalize}"
        }

        while(existsTagNames.exists(it => it.equalsIgnoreCase(name))) {
          name = s"_$name"
        }
        prettyTagName(name)
      } else {
        prettyTagName(singularName.capitalize)
      }

      existsTagNames += tagName
      ApiTag(tagName)
    }
    val fun: DynamicEntityInfo => mutable.Map[(DynamicEntityOperation, String), ResourceDoc] = createDocs(apiTag, apiVersion)
    val docs: Iterable[((DynamicEntityOperation, String), ResourceDoc)] = definitionsMap.values.flatMap(fun)
    docs.toMap
  }

  // TODO the requestBody and responseBody is not correct ref type
  /**
   *
   * @param fun (singularName, entityName) => ResourceDocTag
   * @param dynamicEntityInfo dynamicEntityInfo
   * @return all ResourceDoc of given dynamicEntity
   */
  private def createDocs(fun: (String, String) => ResourceDocTag, apiVersion: ScannedApiVersion)
                (dynamicEntityInfo: DynamicEntityInfo): mutable.Map[(DynamicEntityOperation, String), ResourceDoc] = {
    val entityName = dynamicEntityInfo.entityName
    val hasPersonalEntity = dynamicEntityInfo.hasPersonalEntity
    val splitName = entityName
    // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
    val splitNameWithBankId = if (dynamicEntityInfo.bankId.isDefined)
      s"""$splitName(${dynamicEntityInfo.bankId.getOrElse("")})"""
    else
      s"""$splitName"""

    val mySplitNameWithBankId = s"My$splitNameWithBankId"

    val idNameInUrl = StringHelpers.snakify(dynamicEntityInfo.idName).toUpperCase()
    val listName = dynamicEntityInfo.listName
    val bankId = dynamicEntityInfo.bankId
    // What comes before the entity name: in v7.0.0 the space, always named (SYS included); in the
    // unversioned URLs a bank when there is one, and nothing for the system space.
    val urlPrefix =
      if (apiVersion == ApiVersion.v7_0_0) v700UrlPrefix(bankId)
      else bankId.map(b => s"/banks/$b").getOrElse("")
    val resourceDocUrl = s"$urlPrefix/$entityName"
    // Response examples. A v7.0.0 response always names its space, `"bank_id": "SYS"` included, where
    // the unversioned URLs name a bank only for a bank level entity.
    def inSpace(example: JObject): JObject =
      if (apiVersion == ApiVersion.v7_0_0)
        (("bank_id" -> DynamicEntitySpace.bankIdOrSystem(bankId)): JObject) merge JObject(example.obj.filterNot(_.name == "bank_id"))
      else example
    val singleExample = inSpace(dynamicEntityInfo.getSingleExample)
    val listExample = inSpace(dynamicEntityInfo.getExampleList)
    val myResourceDocUrl = s"$urlPrefix/my/$entityName"


    // (operationType, entityName) -> ResourceDoc
    val resourceDocs = scala.collection.mutable.Map[(DynamicEntityOperation, String),ResourceDoc]()
    val apiTag: ResourceDocTag = fun(entityName,splitNameWithBankId)

    resourceDocs += (DynamicEntityOperation.GET_ALL, splitNameWithBankId) -> ResourceDoc(
      apiVersion,
      buildGetAllFunctionName(bankId, entityName),
      "GET",
      s"$resourceDocUrl",
      s"Get $splitName List",
      s"""Get $splitName List.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |
         |${methodRoutingExample(entityName)}
         |
         |${userAuthenticationMessage(true)}
         |
         |${dynamicEntityInfo.listQueryDoc(joinsSupported = true)}
         |""".stripMargin,
      EmptyBody,
      listExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole)),
      createdByBankId= dynamicEntityInfo.bankId,
      authMode = dynamicEntityInfo.endpointAuthMode
    )

    resourceDocs += (DynamicEntityOperation.GET_ONE, splitNameWithBankId) -> ResourceDoc(
      apiVersion,
      buildGetOneFunctionName(bankId, entityName),
      "GET",
      s"$resourceDocUrl/$idNameInUrl",
      s"Get $splitName by id",
      s"""Get $splitName by id.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |
         |${methodRoutingExample(entityName)}
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      singleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole)),
      createdByBankId= dynamicEntityInfo.bankId,
      authMode = dynamicEntityInfo.endpointAuthMode
    )

    resourceDocs += (DynamicEntityOperation.CREATE, splitNameWithBankId) -> ResourceDoc(
      apiVersion,
      buildCreateFunctionName(bankId, entityName),
      "POST",
      s"$resourceDocUrl",
      s"Create new $splitName",
      s"""Create new $splitName.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |
         |${methodRoutingExample(entityName)}
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutIdWritable,
      singleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canCreateRole)),
      createdByBankId= dynamicEntityInfo.bankId,
      authMode = dynamicEntityInfo.endpointAuthMode
      )

    resourceDocs += (DynamicEntityOperation.UPDATE, splitNameWithBankId) -> ResourceDoc(
      apiVersion,
      buildUpdateFunctionName(bankId, entityName),
      "PUT",
      s"$resourceDocUrl/$idNameInUrl",
      s"Update $splitName",
      s"""Update $splitName.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |
         |${methodRoutingExample(entityName)}
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutIdWritable,
      singleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canUpdateRole)),
      createdByBankId= dynamicEntityInfo.bankId,
      authMode = dynamicEntityInfo.endpointAuthMode
    )

    resourceDocs += (DynamicEntityOperation.PATCH, splitNameWithBankId) -> ResourceDoc(
      apiVersion,
      buildPatchFunctionName(bankId, entityName),
      "PATCH",
      s"$resourceDocUrl/$idNameInUrl",
      s"Partially update $splitName",
      s"""Partially update $splitName: only the fields supplied in the body are changed; others are preserved.
         |
         |This is also the write path for **field-level write-restricted** fields (those declared with
         |`write_role_required` or an explicit `write_role`). Authorisation is **per field present in the body**:
         |a write-restricted field requires that field's write role, and an unrestricted field requires the entity
         |update role. You may change a field if (and only if) you hold the role that governs it; the request is
         |rejected with 403 (missing role) listing the roles you lack. There is no blanket entity-update
         |precondition — holding only a field's write role is sufficient to PATCH that field alone.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |
         |${methodRoutingExample(entityName)}
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutId,
      singleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canUpdateRole)),
      createdByBankId= dynamicEntityInfo.bankId,
      authMode = dynamicEntityInfo.endpointAuthMode
    )

    resourceDocs += (DynamicEntityOperation.DELETE, splitNameWithBankId) -> ResourceDoc(
      apiVersion,
      buildDeleteFunctionName(bankId, entityName),
      "DELETE",
      s"$resourceDocUrl/$idNameInUrl",
      s"Delete $splitName by id",
      s"""Delete $splitName by id
         |
         |${methodRoutingExample(entityName)}
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutIdWritable,
      singleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canDeleteRole)),
      createdByBankId= dynamicEntityInfo.bankId,
      authMode = dynamicEntityInfo.endpointAuthMode
    )

    if(hasPersonalEntity){ //only hasPersonalEntity == true, then create the myEndpoints
      val personalRequiresRole = dynamicEntityInfo.personalRequiresRole
      val myErrorMessages = if(personalRequiresRole) List(AuthenticatedUserIsRequired, UserHasMissingRoles, ConsentMyResourcesMissing, UnknownError) else List(AuthenticatedUserIsRequired, ConsentMyResourcesMissing, UnknownError)
      val myErrorMessagesWithJson = if(personalRequiresRole) List(AuthenticatedUserIsRequired, UserHasMissingRoles, ConsentMyResourcesMissing, InvalidJsonFormat, UnknownError) else List(AuthenticatedUserIsRequired, ConsentMyResourcesMissing, InvalidJsonFormat, UnknownError)
      val myConsentUserNote =
        "With a Consent: the consent user may use this endpoint only if the Consent lists this entity in `my_resources.personal_dynamic_entities` with the needed action (`read` for GET, `write` otherwise); rows written with a Consent belong to the User who granted it." +
        (if (personalRequiresRole) " The role is required in addition." else "")

      resourceDocs += (DynamicEntityOperation.GET_ALL, mySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetAllFunctionName(bankId, s"My$entityName"),
        "GET",
        s"$myResourceDocUrl",
        s"Get My $splitName List",
        s"""Get My $splitName List.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |${userAuthenticationMessage(true)}
           |
           |$myConsentUserNote
           |
           |${dynamicEntityInfo.listQueryDoc(joinsSupported = true)}
           |""".stripMargin,
        EmptyBody,
        listExample,
        myErrorMessages,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canGetRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.GET_ONE, mySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetOneFunctionName(bankId, s"My$entityName"),
        "GET",
        s"$myResourceDocUrl/$idNameInUrl",
        s"Get My $splitName by id",
        s"""Get My $splitName by id.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |${userAuthenticationMessage(true)}
           |
           |$myConsentUserNote
           |""".stripMargin,
        EmptyBody,
        singleExample,
        myErrorMessages,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canGetRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.CREATE, mySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildCreateFunctionName(bankId, s"My$entityName"),
        "POST",
        s"$myResourceDocUrl",
        s"Create new My $splitName",
        s"""Create new My $splitName.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |${userAuthenticationMessage(true)}
           |
           |$myConsentUserNote
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutIdWritable,
        singleExample,
        myErrorMessagesWithJson,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canCreateRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
        )

      resourceDocs += (DynamicEntityOperation.UPDATE, mySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildUpdateFunctionName(bankId, s"My$entityName"),
        "PUT",
        s"$myResourceDocUrl/$idNameInUrl",
        s"Update My $splitName",
        s"""Update My $splitName.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |${userAuthenticationMessage(true)}
           |
           |$myConsentUserNote
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutIdWritable,
        singleExample,
        myErrorMessagesWithJson,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canUpdateRole)) else Some(List(dynamicEntityInfo.canUpdateRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.PATCH, mySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildPatchFunctionName(bankId, s"My$entityName"),
        "PATCH",
        s"$myResourceDocUrl/$idNameInUrl",
        s"Partially update My $splitName",
        s"""Partially update My $splitName: only the fields supplied in the body are changed; others are preserved.
           |
           |This is also the write path for **field-level write-restricted** fields; writing such a field requires the
           |caller to hold that field's write role.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |${userAuthenticationMessage(true)}
           |
           |$myConsentUserNote
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutId,
        singleExample,
        myErrorMessagesWithJson,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canUpdateRole)) else Some(List(dynamicEntityInfo.canUpdateRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.DELETE, mySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildDeleteFunctionName(bankId, s"My$entityName"),
        "DELETE",
        s"$myResourceDocUrl/$idNameInUrl",
        s"Delete My $splitName by id",
        s"""Delete My $splitName by id
           |
           |${methodRoutingExample(entityName)}
           |
           |${userAuthenticationMessage(true)}
           |
           |$myConsentUserNote
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutIdWritable,
        singleExample,
        myErrorMessages,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canDeleteRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
      )
    }

    val hasPublicAccess = dynamicEntityInfo.hasPublicAccess
    if(hasPublicAccess) {
      val publicResourceDocUrl = s"$urlPrefix/public/$entityName"
      val publicSplitNameWithBankId = s"Public$splitNameWithBankId"

      resourceDocs += (DynamicEntityOperation.GET_ALL, publicSplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetAllFunctionName(bankId, s"Public$entityName"),
        "GET",
        s"$publicResourceDocUrl",
        s"Get Public $splitName List",
        s"""Get Public $splitName List.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |Authentication is Optional
           |
           |${dynamicEntityInfo.listQueryDoc(joinsSupported = false)}
           |""".stripMargin,
        EmptyBody,
        listExample,
        List(
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.GET_ONE, publicSplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetOneFunctionName(bankId, s"Public$entityName"),
        "GET",
        s"$publicResourceDocUrl/$idNameInUrl",
        s"Get Public $splitName by id",
        s"""Get Public $splitName by id.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |Authentication is Optional
           |""".stripMargin,
        EmptyBody,
        singleExample,
        List(
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )
    }

    val hasCommunityAccess = dynamicEntityInfo.hasCommunityAccess
    if(hasCommunityAccess) {
      val communityResourceDocUrl = s"$urlPrefix/community/$entityName"
      val communitySplitNameWithBankId = s"Community$splitNameWithBankId"

      resourceDocs += (DynamicEntityOperation.GET_ALL, communitySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetAllFunctionName(bankId, s"Community$entityName"),
        "GET",
        s"$communityResourceDocUrl",
        s"Get Community $splitName List",
        s"""Get Community $splitName List. Returns ALL records (personal + non-personal from all users).
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |Authentication is Required
           |
           |${dynamicEntityInfo.listQueryDoc(joinsSupported = false)}
           |""".stripMargin,
        EmptyBody,
        listExample,
        List(
          AuthenticatedUserIsRequired,
          UserHasMissingRoles,
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        Some(List(dynamicEntityInfo.canGetRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.GET_ONE, communitySplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetOneFunctionName(bankId, s"Community$entityName"),
        "GET",
        s"$communityResourceDocUrl/$idNameInUrl",
        s"Get Community $splitName by id",
        s"""Get Community $splitName by id. Returns the record regardless of ownership.
           |${dynamicEntityInfo.description}
           |
           |${dynamicEntityInfo.fieldsDescription}
           |
           |${methodRoutingExample(entityName)}
           |
           |Authentication is Required
           |""".stripMargin,
        EmptyBody,
        singleExample,
        List(
          AuthenticatedUserIsRequired,
          UserHasMissingRoles,
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        Some(List(dynamicEntityInfo.canGetRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )
    }

    // Row-level access (useRowLevelAccess): the per-record ACL management endpoints. Documented only
    // for entities that opt in, mirroring the public/community blocks above. The keys carry an
    // `Access` prefix so they never collide with the data endpoints' docs (which the handlers look up).
    if(dynamicEntityInfo.useRowLevelAccess) {
      val accessSplitNameWithBankId = s"Access$splitNameWithBankId"
      val accessResourceDocUrl = s"$resourceDocUrl/$idNameInUrl/access"
      val accessEntryExample: JObject =
        ("user_id" -> ExampleValue.userIdExample.value) ~
        ("can_read" -> true) ~
        ("can_update" -> true) ~
        ("can_delete" -> false) ~
        ("can_grant" -> true)
      val accessGrantedByExample: JObject = ("granted_by" -> ExampleValue.userIdExample.value)
      val accessListExample: JObject =
        ("access" -> JArray(List(accessEntryExample merge accessGrantedByExample)))
      val accessRoleNote =
        s"""This endpoint exists because $splitName is defined with `useRowLevelAccess`: a per-record access list decides
           |read, update, delete and grant in place of the entity's Get, Update and Delete roles.
           |
           |The caller must hold `can_grant` on this record — the User who created it does — or the
           |`${dynamicEntityInfo.canGrantRowAccessRole.toString()}` role, which administers the access list of any record.
           |""".stripMargin

      resourceDocs += (DynamicEntityOperation.GET_ALL, accessSplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGetRowAccessFunctionName(bankId, entityName),
        "GET",
        s"$accessResourceDocUrl",
        s"Get $splitName Record Access List",
        s"""Get the access list of one $splitName record.
           |
           |$accessRoleNote
           |
           |${userAuthenticationMessage(true)}
           |""".stripMargin,
        EmptyBody,
        accessListExample,
        List(
          AuthenticatedUserIsRequired,
          UserHasMissingRoles,
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        Some(List(dynamicEntityInfo.canGrantRowAccessRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.UPDATE, accessSplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildGrantRowAccessFunctionName(bankId, entityName),
        "PUT",
        s"$accessResourceDocUrl",
        s"Grant Access to a $splitName Record",
        s"""Grant (or update) another User's access to one $splitName record.
           |
           |The body is one entry or an array of entries. `user_id` is required; `can_read`, `can_update` and
           |`can_delete` default to `false` and `can_grant` defaults to `true`, so a grantee may re-share by default.
           |Sending an entry for a User who already holds access replaces their permissions.
           |
           |$accessRoleNote
           |
           |${userAuthenticationMessage(true)}
           |""".stripMargin,
        accessEntryExample,
        accessListExample,
        List(
          AuthenticatedUserIsRequired,
          UserHasMissingRoles,
          InvalidJsonFormat,
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        Some(List(dynamicEntityInfo.canGrantRowAccessRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.DELETE, accessSplitNameWithBankId) -> ResourceDoc(
        apiVersion,
        buildRevokeRowAccessFunctionName(bankId, entityName),
        "DELETE",
        s"$accessResourceDocUrl/USER_ID",
        s"Revoke Access to a $splitName Record",
        s"""Revoke one User's access to one $splitName record.
           |
           |The revoke cascades: every grant that User made on this record, and every grant made in turn by
           |those grantees, is removed with it.
           |
           |$accessRoleNote
           |
           |${userAuthenticationMessage(true)}
           |""".stripMargin,
        EmptyBody,
        accessListExample,
        List(
          AuthenticatedUserIsRequired,
          UserHasMissingRoles,
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        Some(List(dynamicEntityInfo.canGrantRowAccessRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )
    }

    resourceDocs
  }

  private def buildCreateFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_create${entityName}_${bankId.getOrElse("")}"
  private def buildUpdateFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_update${entityName}_${bankId.getOrElse("")}"
  private def buildPatchFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_patch${entityName}_${bankId.getOrElse("")}"
  private def buildDeleteFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_delete${entityName}_${bankId.getOrElse("")}"
  private def buildGetOneFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_getSingle${entityName}_${bankId.getOrElse("")}"
  private def buildGetAllFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_get${entityName}List_${bankId.getOrElse("")}"
  private def buildGetRowAccessFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_get${entityName}RowAccess_${bankId.getOrElse("")}"
  private def buildGrantRowAccessFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_grant${entityName}RowAccess_${bankId.getOrElse("")}"
  private def buildRevokeRowAccessFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_revoke${entityName}RowAccess_${bankId.getOrElse("")}"

  @inline
  private def buildOperationId(bankId:Option[String], entityName: String, fun: (Option[String], String) => String): String = {
    APIUtil.buildOperationId(implementedInApiVersion, fun(bankId, entityName))
  }

  def buildCreateOperationId(bankId:Option[String], entityName: String) = buildOperationId(bankId, entityName, buildCreateFunctionName)
  def buildUpdateOperationId(bankId:Option[String], entityName: String) = buildOperationId(bankId, entityName, buildUpdateFunctionName)
  def buildDeleteOperationId(bankId:Option[String], entityName: String) = buildOperationId(bankId, entityName, buildDeleteFunctionName)
  def buildGetOneOperationId(bankId:Option[String], entityName: String) = buildOperationId(bankId, entityName, buildGetOneFunctionName)
  def buildGetAllOperationId(bankId:Option[String], entityName: String) = buildOperationId(bankId, entityName, buildGetAllFunctionName)

  private def methodRoutingExample(entityName: String) =
    s"""
      |MethodRouting settings example:
      |
      |<details>
      |
      |```
      |{
      |  "is_bank_id_exact_match":false,
      |  "method_name":"dynamicEntityProcess",
      |  "connector_name":"rest_vMar2019",
      |  "bank_id_pattern":".*",
      |  "parameters":[
      |    {
      |        "key":"entityName",
      |        "value":"$entityName"
      |    }
      |    {
      |        "key":"url",
      |        "value":"http://mydomain.com/xxx"
      |    }
      |  ]
      |}
      |```
      |
      |</details>
      |""".stripMargin

}
case class DynamicEntityInfo(definition: String, entityName: String, bankId: Option[String], hasPersonalEntity: Boolean, hasPublicAccess: Boolean = false, hasCommunityAccess: Boolean = false, personalRequiresRole: Boolean = false, useRowLevelAccess: Boolean = false, authMode: String = code.dynamicEntity.DynamicEntityAuthMode.default) {

  /** The entity's auth mode as the framework type; unknown or empty values read as UserOnly. */
  val endpointAuthMode: code.api.util.APIUtil.EndpointAuthMode = authMode match {
    case code.dynamicEntity.DynamicEntityAuthMode.ApplicationOnly => code.api.util.APIUtil.ApplicationOnly
    case code.dynamicEntity.DynamicEntityAuthMode.UserOrApplication => code.api.util.APIUtil.UserOrApplication
    case code.dynamicEntity.DynamicEntityAuthMode.UserAndApplication => code.api.util.APIUtil.UserAndApplication
    case _ => code.api.util.APIUtil.UserOnly
  }

  import com.openbankproject.commons.util.json
  import code.api.dynamic.entity.query.FieldSpec

  val subEntities: List[DynamicEntityInfo] = Nil

  val idName = StringHelpers.snakify(entityName) + "_id"

  val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")

  val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")


  val definitionJson = json.parse(definition).asInstanceOf[JObject]
  val entity = (definitionJson \ entityName).asInstanceOf[JObject]

  val description = entity \ "description" match {
    case JString(s) if StringUtils.isNotBlank(s) =>
      s"""
        |${s.capitalize}
        |""".stripMargin
    case _ => ""
  }

  val fieldsDescription = {
    val descriptions = (entity \ "properties")
      .asInstanceOf[JObject]
      .obj
      .filter(field =>
        field.value \ "description" match {
          case JString(s) if StringUtils.isNotBlank(s) => true
          case _ => false
        }
      )
    val propertyList = if(descriptions.nonEmpty) {
      descriptions
        .map(field => s"""* ${field.name}: ${(field.value \ "description").asInstanceOf[JString].s}""")
        .mkString("**Property List:** \n\n", "\n", "")
    } else {
      ""
    }
    val writeNote = if(writeRestrictedFields.nonEmpty) s"\n\n**Write-restricted fields** (set only via PATCH by a holder of the field's write role): ${writeRestrictedFields.mkString(", ")}" else ""
    val readNote = if(readRestrictedFields.nonEmpty) s"\n\n**Read-restricted fields** (returned only to callers holding the field's read role): ${readRestrictedFields.mkString(", ")}" else ""
    propertyList + writeNote + readNote
  }

  def toResponse(result: JObject, id: Option[String]): JObject = {

    val fieldNameToTypeName: Map[String, String] = (entity \ "properties")
      .asInstanceOf[JObject]
      .obj
      .map(field => (field.name, (field.value \ "type").asInstanceOf[JString].s))
      .toMap

    val fields = result.obj.filter(it => fieldNameToTypeName.keySet.contains(it.name))

    (id, fields.exists(_.name == idName)) match {
      case (Some(idValue), false) => JObject(JField(idName, JString(idValue)) :: fields)
      case _ => JObject(fields)
    }
  }

  def getSingleExampleWithoutId: JObject = {
    val fields = (entity \ "properties").asInstanceOf[JObject].obj

    def extractExample(typeAndExample: JValue): JValue = {
      val example = typeAndExample \ "example"
      (example, (typeAndExample \ "type")) match {
        case (JString(s), JString("boolean")) => JBool(s.toLowerCase().toBoolean)
        case (JString(s), JString("integer")) => JInt(s.toLong)
        case (JString(s), JString("number")) => JDouble(s.toDouble)
        case _ => example
      }
    }
    val exampleFields = fields.map(field => JField(field.name, extractExample(field.value)))
    JObject(exampleFields)
  }

  // Request-body example for POST/PUT: excludes write-restricted fields (they're not settable here; only via PATCH).
  def getSingleExampleWithoutIdWritable: JObject = {
    val restricted = writeRestrictedFields.toSet
    if (restricted.isEmpty) getSingleExampleWithoutId
    else JObject(getSingleExampleWithoutId.obj.filterNot(f => restricted.contains(f.name)))
  }
  // A bank level entity's responses name its bank, as `bank_id`; see Http4sDynamicEntity.wrapBankId.
  val bankIdJObject: JObject = ("bank_id" -> bankId.getOrElse(ExampleValue.bankIdExample.value))

  def getSingleExample: JObject = if (bankId.isDefined){
    val SingleObject: JObject = (singleName -> (JObject(JField(idName, JString(ExampleValue.idExample.value)) :: getSingleExampleWithoutId.obj)))
    bankIdJObject merge SingleObject
  } else{
    (singleName -> (JObject(JField(idName, JString(ExampleValue.idExample.value)) :: getSingleExampleWithoutId.obj)))
  }

  def getExampleList: JObject = {
    // Create the list item without the singleName wrapper - the actual API response
    // returns a flat list of objects, not wrapped in entity name
    val listItem: JObject = JObject(JField(idName, JString(ExampleValue.idExample.value)) :: getSingleExampleWithoutId.obj)
    if (bankId.isDefined) {
      val objectList: JObject = (listName -> JArray(List(listItem)))
      bankIdJObject merge objectList
    } else {
      (listName -> JArray(List(listItem)))
    }
  }

  val canCreateRole: ApiRole = DynamicEntityInfo.canCreateRole(entityName, bankId)
  val canUpdateRole: ApiRole = DynamicEntityInfo.canUpdateRole(entityName, bankId)
  val canGetRole: ApiRole = DynamicEntityInfo.canGetRole(entityName, bankId)
  val canDeleteRole: ApiRole = DynamicEntityInfo.canDeleteRole(entityName, bankId)
  val canGrantRowAccessRole: ApiRole = DynamicEntityInfo.canGrantRowAccessRole(entityName, bankId)

  // ----- Field-level access control (mirrors DynamicEntityT; here `entity` is already the per-entity object) -----
  private def restrictedFields(requiredFlag: String, roleKey: String): List[String] =
    (entity \ "properties") match {
      case props: JObject => props.obj.collect {
        case JField(name, propDef: JObject)
          if (propDef \ requiredFlag) == JBool(true) ||
             ((propDef \ roleKey) match { case JString(s) => s.nonEmpty; case _ => false }) => name
      }
      case _ => Nil
    }
  /** Fields written only via the role-gated PATCH path (not via POST/PUT). */
  lazy val writeRestrictedFields: List[String] = restrictedFields("write_role_required", "write_role")
  /** Fields omitted from GET unless the caller holds the read role. */
  lazy val readRestrictedFields: List[String] = restrictedFields("read_role_required", "read_role")
  def explicitWriteRole(fieldName: String): Option[String] =
    (entity \ "properties" \ fieldName \ "write_role") match { case JString(s) if s.nonEmpty => Some(s); case _ => None }
  def explicitReadRole(fieldName: String): Option[String] =
    (entity \ "properties" \ fieldName \ "read_role") match { case JString(s) if s.nonEmpty => Some(s); case _ => None }
  /** Declared schema property names (used to bound a PATCH merge to real fields). */
  lazy val propertyNames: List[String] = (entity \ "properties") match {
    case props: JObject => props.obj.map(_.name)
    case _ => Nil
  }

  /**
   * Fields declared `indexed` (DE_indexing): name -> (declared type, index kind "scalar"|"spatial").
   * A `reference:<Target>` field surfaces as [[DynamicEntityFieldType.reference]] (its value is an id
   * String, so it indexes/queries like a string); the target entity is tracked in [[referenceFields]].
   * Other unrecognised types are dropped — this is the queryable allow-list the planner validates against.
   */
  lazy val indexedFields: Map[String, FieldSpec] = (entity \ "properties") match {
    case props: JObject => props.obj.collect {
      case JField(name, propDef: JObject) if (propDef \ "indexed") == JBool(true) =>
        val typeName = (propDef \ "type") match { case JString(s) => s; case _ => "" }
        val kind = (propDef \ "index") match { case JString(s) => s; case _ => "scalar" }
        val fieldTypeOpt =
          if (typeName.startsWith("reference:")) Some(DynamicEntityFieldType.reference)
          else DynamicEntityFieldType.withNameOption(typeName)
        fieldTypeOpt.map(ft => name -> FieldSpec(ft, kind))
    }.flatten.toMap
    case _ => Map.empty
  }

  /**
   * Every `reference:<Target>` field, indexed or not: fieldName -> target entity name (the part after
   * "reference:"). Only the indexed subset ([[referenceFields]]) forms a join edge; the rest
   * ([[unindexedReferenceFields]]) exist so the planner can tell a developer precisely which field to
   * mark `"indexed": true` instead of claiming no reference is declared at all.
   */
  lazy val allReferenceFields: Map[String, String] = (entity \ "properties") match {
    case props: JObject => props.obj.collect {
      case JField(name, propDef: JObject)
        if ((propDef \ "type") match { case JString(s) => s.startsWith("reference:"); case _ => false }) =>
        val target = ((propDef \ "type"): @unchecked) match { case JString(s) => s.stripPrefix("reference:") }
        name -> target
    }.toMap
    case _ => Map.empty
  }

  private lazy val indexedPropertyNames: Set[String] = (entity \ "properties") match {
    case props: JObject => props.obj.collect {
      case JField(name, propDef: JObject) if (propDef \ "indexed") == JBool(true) => name
    }.toSet
    case _ => Set.empty
  }

  /**
   * Indexed `reference:<Target>` fields: fieldName -> target entity name (the part after "reference:").
   * The join planner uses this to resolve one-hop EXISTS/NOT EXISTS edges between entities; only
   * declared reference fields are joinable (a plain string field holding ids is not). See
   * ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md.
   */
  lazy val referenceFields: Map[String, String] =
    allReferenceFields.filter { case (name, _) => indexedPropertyNames.contains(name) }

  /** `reference:<Target>` fields that are declared but NOT `indexed`, so they cannot be joined on (yet). */
  lazy val unindexedReferenceFields: Map[String, String] = allReferenceFields -- referenceFields.keys

  /**
   * Human-facing documentation of the list-endpoint query grammar (filter / sort / paginate / one-hop
   * joins), appended to the GET-all ResourceDoc descriptions. `joinsSupported` is true on the
   * authenticated + /my/ endpoints (which can use the SQL projection backend) and false on the public /
   * community endpoints (in-memory only — a join there returns 400). See
   * ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md.
   */
  def listQueryDoc(joinsSupported: Boolean): String = {
    val indexedNames = indexedFields.keys.toList.sorted
    val indexedHint =
      if (indexedNames.isEmpty) "_(no fields on this entity are declared `indexed`, so only the legacy bare-parameter filter below is available.)_"
      else "Queryable (declared `indexed`) fields: " + indexedNames.mkString("`", "`, `", "`") + "."
    val refHint =
      if (referenceFields.isEmpty) ""
      else "\n\nThis entity's reference fields (each usable as a one-hop join edge to its target entity): " +
        referenceFields.toList.sortBy(_._1).map { case (f, t) => s"`$f` → `$t`" }.mkString(", ") + "."

    val base =
      s"""**Filtering, sorting and pagination** on the list endpoint:
         |
         |$indexedHint
         |
         |* **Filter**: `?obp_filter[FIELD]=OP:VALUE`. Operators: `eq`, `ne`, `in`, `lt`, `gt`, `le`, `ge`, `between`, `like`, `is_null`, `not_set`. `in` / `between` take comma-separated values; `is_null` / `not_set` take no value. Repeat the same key to AND several constraints on one field.
         |  e.g. `?obp_filter[status]=eq:active&obp_filter[amount]=between:10,100` or `?obp_filter[closed_date]=not_set`
         |* **Legacy filter** (still supported): bare field parameters, e.g. `?name=James%20Brown&number=123.456&number=11.11` filters by `name == "James Brown" && (number == 123.456 || number == 11.11)`.
         |* **Sort**: `?obp_sort_by=FIELD[,FIELD2]&obp_sort_direction=ASC|DESC`.
         |* **Paginate**: `?obp_limit=20&obp_offset=40`.
         |
         |Filtering, sorting, pagination and `is_null` / `not_set` work on every deployment (no special backend required).""".stripMargin

    val joins =
      if (!joinsSupported)
        "\n\nOne-hop join queries (`obp_exists` / `obp_not_exists`) are not available on this endpoint."
      else
        s"""
           |
           |**One-hop joins (EXISTS / NOT EXISTS)** — filter this entity by a condition on a *related* entity linked through a declared `reference:` field:
           |
           |* `?obp_exists[RelatedEntity]` — keep rows that have at least one related RelatedEntity.
           |* `?obp_exists[RelatedEntity]=filter[FIELD]=eq:VALUE` — ...that have at least one matching the predicate.
           |* `?obp_not_exists[RelatedEntity]` — keep rows with NO related RelatedEntity at all.
           |* `?obp_not_exists[RelatedEntity]=filter[FIELD]=eq:VALUE` — ...with no related RelatedEntity matching the predicate (this **includes** rows that have no related RelatedEntity).
           |* If two entities are linked by more than one reference, disambiguate the edge with `via:`, e.g. `?obp_exists[RelatedEntity]=via:buyer_ref;filter[status]=eq:signed`.
           |
           |The nested `filter[...]` reuses the operator grammar above. Mind the distinction: `obp_not_exists[X]=filter[flag]=eq:true` (no related X with flag=true — includes rows with no X) is **not** the same as `obp_exists[X]=filter[flag]=ne:true` (has a related X with flag≠true — excludes rows with no X).
           |
           |Only a field typed `reference:<Entity>` **and** declared `indexed: true` forms a join edge; a plain string field holding ids is not joinable.$refHint
           |
           |**Joins require the SQL projection backend** (`dynamic_entity.indexing.backend=auto` on Postgres or SQL Server) with the involved fields indexed and ready. On an in-memory deployment a join query returns `400 (OBP-09022)`; while an index is still building it returns `409`. The plain filter / sort / paginate / `is_null` / `not_set` features above do **not** need the projection backend.""".stripMargin

    base + joins
  }
}

object DynamicEntityInfo {

  /**
   * The Roles that gate a Dynamic Entity's **records** — the rows, not the schema. The schema is
   * gated by the Definition Roles in ApiRole, and the two were called the same thing until this
   * change, which hid the difference between being allowed to define `country` and being allowed to
   * put `FR` in it.
   *
   * A Role names an operation and an entity; the space it applies to is the Entitlement's bank id,
   * `SYS` for the system space or a real bank id. The name no longer carries the space, which is why
   * there is one name per operation here rather than a pair. Every one of them requires a bank id,
   * so a single grant can never reach more than the space it names.
   */
  def canCreateRole(entityName: String, bankId:Option[String]): ApiRole =
    getOrCreateDynamicApiRole("CanCreateDynamicEntityRecord_" + entityName, true)

  def canUpdateRole(entityName: String, bankId:Option[String]): ApiRole =
    getOrCreateDynamicApiRole("CanUpdateDynamicEntityRecord_" + entityName, true)

  def canGetRole(entityName: String, bankId:Option[String]): ApiRole =
    getOrCreateDynamicApiRole("CanGetDynamicEntityRecord_" + entityName, true)

  def canDeleteRole(entityName: String, bankId:Option[String]): ApiRole =
    getOrCreateDynamicApiRole("CanDeleteDynamicEntityRecord_" + entityName, true)

  // Admin override for row-level access (§3): a holder may grant/list/revoke per-row ACL
  // on any row of the entity, even rows they cannot read. Ordinary owner-driven sharing
  // does not need this role — it goes through the row's own ACL CanGrant (§8.1).
  def canGrantRowAccessRole(entityName: String, bankId:Option[String]): ApiRole =
    getOrCreateDynamicApiRole("CanGrantDynamicEntityRowAccess_" + entityName, true)

  def roleNames(entityName: String, bankId:Option[String]): List[String] = List(
    canCreateRole(entityName, bankId),
    canUpdateRole(entityName, bankId),
    canGetRole(entityName, bankId),
    canDeleteRole(entityName, bankId),
    canGrantRowAccessRole(entityName, bankId)
  ).map(_.toString())

  // Field-level roles. If the definition declares an explicit write_role/read_role, use it verbatim
  // (so many fields/entities can share one role); otherwise auto-generate a per-field role.
  def fieldWriteRole(entityName: String, fieldName: String, bankId: Option[String], explicit: Option[String]): ApiRole =
    explicit match {
      case Some(role) => getOrCreateDynamicApiRole(role, true)
      case None => getOrCreateDynamicApiRole(s"CanWriteDynamicEntityField_${entityName}__${fieldName}", true)
    }

  def fieldReadRole(entityName: String, fieldName: String, bankId: Option[String], explicit: Option[String]): ApiRole =
    explicit match {
      case Some(role) => getOrCreateDynamicApiRole(role, true)
      case None => getOrCreateDynamicApiRole(s"CanGetDynamicEntityField_${entityName}__${fieldName}", true)
    }
}
