package code.api.dynamic.entity.helper

import code.api.util.APIUtil.{EmptyBody, ResourceDoc, userAuthenticationMessage}
import code.api.util.ApiRole.getOrCreateDynamicApiRole
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{InvalidJsonFormat, UnknownError, UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.util._
import com.openbankproject.commons.model.enums.{DynamicEntityFieldType, DynamicEntityOperation}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object EntityName {
  // unapply result structure: (BankId, entityName, id, isPersonalEntity)
  def unapply(url: List[String]): Option[(Option[String], String, String, Boolean)] = url match {

    //eg: /my/FooBar21
    case "my" :: entityName ::  Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty && definitionMap._2.hasPersonalEntity)
        .map(_ => (None, entityName, "", true))
    //eg: /my/FooBar21/FOO_BAR21_ID
    case "my" :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty && definitionMap._2.hasPersonalEntity)
        .map(_ => (None, entityName, id, true))

    //eg: /FooBar21
    case entityName ::  Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty)
        .map(_ => (None, entityName, "", false))
    //eg: /FooBar21/FOO_BAR21_ID
    case entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty)
        .map(_ => (None, entityName, id, false))


    //eg: /Banks/BANK_ID/my/FooBar21
    case "banks" :: bankId :: "my" :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId) && definitionMap._2.hasPersonalEntity)
        .map(_ => (Some(bankId), entityName, "", true))
    //eg: /Banks/BANK_ID/my/FooBar21/FOO_BAR21_ID
    case "banks" :: bankId :: "my" :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId) && definitionMap._2.hasPersonalEntity)
        .map(_ => (Some(bankId),entityName, id, true))

    //contains Bank:
    //eg: /Banks/BANK_ID/FooBar21
    case "banks" :: bankId :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId))
        .map(_ => (Some(bankId), entityName, "", false))
    //eg: /Banks/BANK_ID/FooBar21/FOO_BAR21_ID
    case "banks" :: bankId :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId))
        .map(_ => (Some(bankId),entityName, id, false))//no bank:

    case _ => None
  }
}

object PublicEntityName {
  // unapply result structure: (BankId, entityName, id)
  // Only matches entities where hasPublicAccess = true
  def unapply(url: List[String]): Option[(Option[String], String, String)] = url match {

    //eg: /public/FooBar21
    case "public" :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty && definitionMap._2.hasPublicAccess)
        .map(_ => (None, entityName, ""))
    //eg: /public/FooBar21/FOO_BAR21_ID
    case "public" :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty && definitionMap._2.hasPublicAccess)
        .map(_ => (None, entityName, id))

    //eg: /banks/BANK_ID/public/FooBar21
    case "banks" :: bankId :: "public" :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId) && definitionMap._2.hasPublicAccess)
        .map(_ => (Some(bankId), entityName, ""))
    //eg: /banks/BANK_ID/public/FooBar21/FOO_BAR21_ID
    case "banks" :: bankId :: "public" :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId) && definitionMap._2.hasPublicAccess)
        .map(_ => (Some(bankId), entityName, id))

    case _ => None
  }
}

object CommunityEntityName {
  // unapply result structure: (BankId, entityName, id)
  // Only matches entities where hasCommunityAccess = true
  def unapply(url: List[String]): Option[(Option[String], String, String)] = url match {

    //eg: /community/FooBar21
    case "community" :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty && definitionMap._2.hasCommunityAccess)
        .map(_ => (None, entityName, ""))
    //eg: /community/FooBar21/FOO_BAR21_ID
    case "community" :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == None && definitionMap._1._2 == entityName && definitionMap._2.bankId.isEmpty && definitionMap._2.hasCommunityAccess)
        .map(_ => (None, entityName, id))

    //eg: /banks/BANK_ID/community/FooBar21
    case "banks" :: bankId :: "community" :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId) && definitionMap._2.hasCommunityAccess)
        .map(_ => (Some(bankId), entityName, ""))
    //eg: /banks/BANK_ID/community/FooBar21/FOO_BAR21_ID
    case "banks" :: bankId :: "community" :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1._1 == Some(bankId) && definitionMap._1._2 == entityName && definitionMap._2.bankId == Some(bankId) && definitionMap._2.hasCommunityAccess)
        .map(_ => (Some(bankId), entityName, id))

    case _ => None
  }
}

object DynamicEntityHelper {
  private val implementedInApiVersion = ApiVersion.v4_0_0

  //                       (Some(BankId), EntityName, DynamicEntityInfo)
  def definitionsMap: Map[(Option[String], String), DynamicEntityInfo] = NewStyle.function.getDynamicEntities(None, true).map(it => ((it.bankId, it.entityName), DynamicEntityInfo(it.metadataJson, it.entityName, it.bankId, it.hasPersonalEntity, it.hasPublicAccess, it.hasCommunityAccess, it.personalRequiresRole))).toMap

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

  def createEntityId(entityName: String) = {
    // (?<=[a-z0-9])(?=[A-Z]) --> mean `Positive Lookbehind (?<=[a-z0-9])` && Positive Lookahead (?=[A-Z]) --> So we can find the space to replace to  `_`
    val regexPattern = "(?<=[a-z0-9])(?=[A-Z])|-"
    // eg: entityName = PetEntity => entityIdName = pet_entity_id
    s"${entityName}_Id".replaceAll(regexPattern, "_").toLowerCase
  }

  def operationToResourceDoc: Map[(DynamicEntityOperation, String), ResourceDoc] = {
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
    val fun: DynamicEntityInfo => mutable.Map[(DynamicEntityOperation, String), ResourceDoc] = createDocs(apiTag)
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
  private def createDocs(fun: (String, String) => ResourceDocTag)
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
    val resourceDocUrl = if(bankId.isDefined)  s"/banks/${bankId.getOrElse("")}/$entityName" else  s"/$entityName"
    val myResourceDocUrl = if(bankId.isDefined)  s"/banks/${bankId.getOrElse("")}/my/$entityName" else  s"/my/$entityName"


    // (operationType, entityName) -> ResourceDoc
    val resourceDocs = scala.collection.mutable.Map[(DynamicEntityOperation, String),ResourceDoc]()
    val apiTag: ResourceDocTag = fun(entityName,splitNameWithBankId)

    resourceDocs += (DynamicEntityOperation.GET_ALL, splitNameWithBankId) -> ResourceDoc(
      implementedInApiVersion,
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
         |Can do filter on the fields
         |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
         |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
         |""".stripMargin,
      EmptyBody,
      dynamicEntityInfo.getExampleList,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    resourceDocs += (DynamicEntityOperation.GET_ONE, splitNameWithBankId) -> ResourceDoc(
      implementedInApiVersion,
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
      dynamicEntityInfo.getSingleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    resourceDocs += (DynamicEntityOperation.CREATE, splitNameWithBankId) -> ResourceDoc(
      implementedInApiVersion,
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
      dynamicEntityInfo.getSingleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canCreateRole)),
      createdByBankId= dynamicEntityInfo.bankId
      )

    resourceDocs += (DynamicEntityOperation.UPDATE, splitNameWithBankId) -> ResourceDoc(
      implementedInApiVersion,
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
      dynamicEntityInfo.getSingleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canUpdateRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    resourceDocs += (DynamicEntityOperation.PATCH, splitNameWithBankId) -> ResourceDoc(
      implementedInApiVersion,
      buildPatchFunctionName(bankId, entityName),
      "PATCH",
      s"$resourceDocUrl/$idNameInUrl",
      s"Partially update $splitName",
      s"""Partially update $splitName: only the fields supplied in the body are changed; others are preserved.
         |
         |This is also the write path for **field-level write-restricted** fields (those declared with
         |`writeRoleRequired` or an explicit `writeRole`). To write such a field the caller must hold that field's
         |write role; otherwise the request is rejected with 403 (missing role). Unrestricted fields require
         |the entity update role, as for PUT.
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
      dynamicEntityInfo.getSingleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canUpdateRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    resourceDocs += (DynamicEntityOperation.DELETE, splitNameWithBankId) -> ResourceDoc(
      implementedInApiVersion,
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
      dynamicEntityInfo.getSingleExample,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canDeleteRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    if(hasPersonalEntity){ //only hasPersonalEntity == true, then create the myEndpoints
      val personalRequiresRole = dynamicEntityInfo.personalRequiresRole
      val myErrorMessages = if(personalRequiresRole) List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError) else List(AuthenticatedUserIsRequired, UnknownError)
      val myErrorMessagesWithJson = if(personalRequiresRole) List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError) else List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError)

      resourceDocs += (DynamicEntityOperation.GET_ALL, mySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |Can do filter on the fields
           |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
           |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
           |""".stripMargin,
        EmptyBody,
        dynamicEntityInfo.getExampleList,
        myErrorMessages,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canGetRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.GET_ONE, mySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |""".stripMargin,
        EmptyBody,
        dynamicEntityInfo.getSingleExample,
        myErrorMessages,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canGetRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.CREATE, mySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |""",
        dynamicEntityInfo.getSingleExampleWithoutIdWritable,
        dynamicEntityInfo.getSingleExample,
        myErrorMessagesWithJson,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canCreateRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
        )

      resourceDocs += (DynamicEntityOperation.UPDATE, mySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |""",
        dynamicEntityInfo.getSingleExampleWithoutIdWritable,
        dynamicEntityInfo.getSingleExample,
        myErrorMessagesWithJson,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canUpdateRole)) else Some(List(dynamicEntityInfo.canUpdateRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.PATCH, mySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |""",
        dynamicEntityInfo.getSingleExampleWithoutId,
        dynamicEntityInfo.getSingleExample,
        myErrorMessagesWithJson,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canUpdateRole)) else Some(List(dynamicEntityInfo.canUpdateRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.DELETE, mySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |""",
        dynamicEntityInfo.getSingleExampleWithoutIdWritable,
        dynamicEntityInfo.getSingleExample,
        myErrorMessages,
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        if(personalRequiresRole) Some(List(dynamicEntityInfo.canDeleteRole)) else None,
        createdByBankId= dynamicEntityInfo.bankId
      )
    }

    val hasPublicAccess = dynamicEntityInfo.hasPublicAccess
    if(hasPublicAccess) {
      val publicResourceDocUrl = if(bankId.isDefined) s"/banks/${bankId.getOrElse("")}/public/$entityName" else s"/public/$entityName"
      val publicSplitNameWithBankId = s"Public$splitNameWithBankId"

      resourceDocs += (DynamicEntityOperation.GET_ALL, publicSplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |Can do filter on the fields
           |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
           |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
           |""".stripMargin,
        EmptyBody,
        dynamicEntityInfo.getExampleList,
        List(
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )

      resourceDocs += (DynamicEntityOperation.GET_ONE, publicSplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
        dynamicEntityInfo.getSingleExample,
        List(
          UnknownError
        ),
        List(apiTag, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )
    }

    val hasCommunityAccess = dynamicEntityInfo.hasCommunityAccess
    if(hasCommunityAccess) {
      val communityResourceDocUrl = if(bankId.isDefined) s"/banks/${bankId.getOrElse("")}/community/$entityName" else s"/community/$entityName"
      val communitySplitNameWithBankId = s"Community$splitNameWithBankId"

      resourceDocs += (DynamicEntityOperation.GET_ALL, communitySplitNameWithBankId) -> ResourceDoc(
        implementedInApiVersion,
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
           |Can do filter on the fields
           |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
           |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
           |""".stripMargin,
        EmptyBody,
        dynamicEntityInfo.getExampleList,
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
        implementedInApiVersion,
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
        dynamicEntityInfo.getSingleExample,
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

    resourceDocs
  }

  private def buildCreateFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_create${entityName}_${bankId.getOrElse("")}"
  private def buildUpdateFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_update${entityName}_${bankId.getOrElse("")}"
  private def buildPatchFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_patch${entityName}_${bankId.getOrElse("")}"
  private def buildDeleteFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_delete${entityName}_${bankId.getOrElse("")}"
  private def buildGetOneFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_getSingle${entityName}_${bankId.getOrElse("")}"
  private def buildGetAllFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_get${entityName}List_${bankId.getOrElse("")}"

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
case class DynamicEntityInfo(definition: String, entityName: String, bankId: Option[String], hasPersonalEntity: Boolean, hasPublicAccess: Boolean = false, hasCommunityAccess: Boolean = false, personalRequiresRole: Boolean = false) {

  import net.liftweb.json

  val subEntities: List[DynamicEntityInfo] = Nil

  val idName = StringHelpers.snakify(entityName) + "_id"

  val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")

  val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")

  val jsonTypeMap: Map[String, Class[_]] = DynamicEntityFieldType.nameToValue.mapValues(_.jValueType)

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

    val fieldNameToType: Map[String, Class[_]] = fieldNameToTypeName
      .mapValues(jsonTypeMap(_))

    val fields = result.obj.filter(it => fieldNameToType.keySet.contains(it.name))

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
  val bankIdJObject: JObject = ("bank-id" -> ExampleValue.bankIdExample.value)

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
  lazy val writeRestrictedFields: List[String] = restrictedFields("writeRoleRequired", "writeRole")
  /** Fields omitted from GET unless the caller holds the read role. */
  lazy val readRestrictedFields: List[String] = restrictedFields("readRoleRequired", "readRole")
  def explicitWriteRole(fieldName: String): Option[String] =
    (entity \ "properties" \ fieldName \ "writeRole") match { case JString(s) if s.nonEmpty => Some(s); case _ => None }
  def explicitReadRole(fieldName: String): Option[String] =
    (entity \ "properties" \ fieldName \ "readRole") match { case JString(s) if s.nonEmpty => Some(s); case _ => None }
  /** Declared schema property names (used to bound a PATCH merge to real fields). */
  lazy val propertyNames: List[String] = (entity \ "properties") match {
    case props: JObject => props.obj.map(_.name)
    case _ => Nil
  }
}

object DynamicEntityInfo {
  def canCreateRole(entityName: String, bankId:Option[String]): ApiRole =
    if(bankId.isDefined)
      getOrCreateDynamicApiRole("CanCreateDynamicEntity_" + entityName, true)
    else
      getOrCreateDynamicApiRole("CanCreateDynamicEntity_System" + entityName, false)
  def canUpdateRole(entityName: String, bankId:Option[String]): ApiRole =
    if(bankId.isDefined)
      getOrCreateDynamicApiRole("CanUpdateDynamicEntity_" + entityName, true)
    else
      getOrCreateDynamicApiRole("CanUpdateDynamicEntity_System" + entityName, false)

  def canGetRole(entityName: String, bankId:Option[String]): ApiRole =
    if(bankId.isDefined)
      getOrCreateDynamicApiRole("CanGetDynamicEntity_" + entityName, true)
    else
      getOrCreateDynamicApiRole("CanGetDynamicEntity_System" + entityName, false)

  def canDeleteRole(entityName: String, bankId:Option[String]): ApiRole =
    if(bankId.isDefined)
      getOrCreateDynamicApiRole("CanDeleteDynamicEntity_" + entityName, true)
    else
      getOrCreateDynamicApiRole("CanDeleteDynamicEntity_System" + entityName, false)

  def roleNames(entityName: String, bankId:Option[String]): List[String] = List(
    canCreateRole(entityName, bankId),
    canUpdateRole(entityName, bankId),
    canGetRole(entityName, bankId),
    canDeleteRole(entityName, bankId)
  ).map(_.toString())

  // Field-level roles. If the definition declares an explicit writeRole/readRole, use it verbatim
  // (so many fields/entities can share one role); otherwise auto-generate a per-field role.
  def fieldWriteRole(entityName: String, fieldName: String, bankId: Option[String], explicit: Option[String]): ApiRole =
    explicit match {
      case Some(role) => getOrCreateDynamicApiRole(role, bankId.isDefined)
      case None =>
        if(bankId.isDefined) getOrCreateDynamicApiRole(s"CanWriteDynamicEntityField_${entityName}__${fieldName}", true)
        else getOrCreateDynamicApiRole(s"CanWriteDynamicEntityField_System${entityName}__${fieldName}", false)
    }

  def fieldReadRole(entityName: String, fieldName: String, bankId: Option[String], explicit: Option[String]): ApiRole =
    explicit match {
      case Some(role) => getOrCreateDynamicApiRole(role, bankId.isDefined)
      case None =>
        if(bankId.isDefined) getOrCreateDynamicApiRole(s"CanGetDynamicEntityField_${entityName}__${fieldName}", true)
        else getOrCreateDynamicApiRole(s"CanGetDynamicEntityField_System${entityName}__${fieldName}", false)
    }
}
