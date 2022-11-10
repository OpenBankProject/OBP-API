package code.api.dynamic.entity.helper

import code.api.util.APIUtil.{EmptyBody, ResourceDoc, authenticationRequiredMessage, generateUUID}
import code.api.util.ApiRole.getOrCreateDynamicApiRole
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{InvalidJsonFormat, UnknownError, UserHasMissingRoles, UserNotLoggedIn}
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

object DynamicEntityHelper {
  private val implementedInApiVersion = ApiVersion.v4_0_0
  
  //                       (Some(BankId), EntityName, DynamicEntityInfo)
  def definitionsMap: Map[(Option[String], String), DynamicEntityInfo] = NewStyle.function.getDynamicEntities(None, true).map(it => ((it.bankId, it.entityName), DynamicEntityInfo(it.metadataJson, it.entityName, it.bankId, it.hasPersonalEntity))).toMap

  def dynamicEntityRoles: List[String] = NewStyle.function.getDynamicEntities(None, true).flatMap(dEntity => DynamicEntityInfo.roleNames(dEntity.entityName, dEntity.bankId))

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
    def prettyTagName(s: String) = s.capitalize.split("(?<=[^-_])[-_]+").reduceLeft(_ + " " + _.capitalize)

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
    
    // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
    val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
    val splitName = s"""${capitalizedNameParts.mkString(" ")}"""
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

    val endPoint = APIUtil.dynamicEndpointStub

    // (operationType, entityName) -> ResourceDoc
    val resourceDocs = scala.collection.mutable.Map[(DynamicEntityOperation, String),ResourceDoc]()
    val apiTag: ResourceDocTag = fun(entityName,splitNameWithBankId)

    resourceDocs += (DynamicEntityOperation.GET_ALL, splitNameWithBankId) -> ResourceDoc(
      endPoint,
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
         |${authenticationRequiredMessage(true)}
         |
         |Can do filter on the fields
         |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
         |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
         |""".stripMargin,
      EmptyBody,
      dynamicEntityInfo.getExampleList,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )
    
    resourceDocs += (DynamicEntityOperation.GET_ONE, splitNameWithBankId) -> ResourceDoc(
      endPoint,
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
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      dynamicEntityInfo.getSingleExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    resourceDocs += (DynamicEntityOperation.CREATE, splitNameWithBankId) -> ResourceDoc(
      endPoint,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutId,
      dynamicEntityInfo.getSingleExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canCreateRole)),
      createdByBankId= dynamicEntityInfo.bankId
      )

    resourceDocs += (DynamicEntityOperation.UPDATE, splitNameWithBankId) -> ResourceDoc(
      endPoint,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutId,
      dynamicEntityInfo.getSingleExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canUpdateRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    resourceDocs += (DynamicEntityOperation.DELETE, splitNameWithBankId) -> ResourceDoc(
      endPoint,
      implementedInApiVersion,
      buildDeleteFunctionName(bankId, entityName),
      "DELETE",
      s"$resourceDocUrl/$idNameInUrl",
      s"Delete $splitName by id",
      s"""Delete $splitName by id
         |
         |${methodRoutingExample(entityName)}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      dynamicEntityInfo.getSingleExampleWithoutId,
      dynamicEntityInfo.getSingleExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
      Some(List(dynamicEntityInfo.canDeleteRole)),
      createdByBankId= dynamicEntityInfo.bankId
    )

    if(hasPersonalEntity){ //only hasPersonalEntity == true, then create the myEndpoints
      resourceDocs += (DynamicEntityOperation.GET_ALL, mySplitNameWithBankId) -> ResourceDoc(
        endPoint,
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
           |${authenticationRequiredMessage(true)}
           |
           |Can do filter on the fields
           |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
           |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
           |""".stripMargin,
        EmptyBody,
        dynamicEntityInfo.getExampleList,
        List(
          UserNotLoggedIn,
          UnknownError
        ),
        List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )
      
      resourceDocs += (DynamicEntityOperation.GET_ONE, mySplitNameWithBankId) -> ResourceDoc(
        endPoint,
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
           |${authenticationRequiredMessage(true)}
           |""".stripMargin,
        EmptyBody,
        dynamicEntityInfo.getSingleExample,
        List(
          UserNotLoggedIn,
          UnknownError
        ),
        List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )
  
      resourceDocs += (DynamicEntityOperation.CREATE, mySplitNameWithBankId) -> ResourceDoc(
        endPoint,
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
           |${authenticationRequiredMessage(true)}
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutId,
        dynamicEntityInfo.getSingleExample,
        List(
          UserNotLoggedIn,
          InvalidJsonFormat,
          UnknownError
        ),
        List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
        )
  
      resourceDocs += (DynamicEntityOperation.UPDATE, mySplitNameWithBankId) -> ResourceDoc(
        endPoint,
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
           |${authenticationRequiredMessage(true)}
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutId,
        dynamicEntityInfo.getSingleExample,
        List(
          UserNotLoggedIn,
          InvalidJsonFormat,
          UnknownError
        ),
        List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
        Some(List(dynamicEntityInfo.canUpdateRole)),
        createdByBankId= dynamicEntityInfo.bankId
      )
  
      resourceDocs += (DynamicEntityOperation.DELETE, mySplitNameWithBankId) -> ResourceDoc(
        endPoint,
        implementedInApiVersion,
        buildDeleteFunctionName(bankId, s"My$entityName"),
        "DELETE",
        s"$myResourceDocUrl/$idNameInUrl",
        s"Delete My $splitName by id",
        s"""Delete My $splitName by id
           |
           |${methodRoutingExample(entityName)}
           |
           |${authenticationRequiredMessage(true)}
           |
           |""",
        dynamicEntityInfo.getSingleExampleWithoutId,
        dynamicEntityInfo.getSingleExample,
        List(
          UserNotLoggedIn,
          UnknownError
        ),
        List(apiTag, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic),
        createdByBankId= dynamicEntityInfo.bankId
      )
    }
    resourceDocs
  }

  private def buildCreateFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_create${entityName}_${bankId.getOrElse("")}"
  private def buildUpdateFunctionName(bankId:Option[String], entityName: String) = s"dynamicEntity_update${entityName}_${bankId.getOrElse("")}"
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
case class DynamicEntityInfo(definition: String, entityName: String, bankId: Option[String], hasPersonalEntity: Boolean) {

  import net.liftweb.json

  val subEntities: List[DynamicEntityInfo] = Nil

  val idName = StringUtils.uncapitalize(entityName) + "Id"

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
    if(descriptions.nonEmpty) {
      descriptions
        .map(field => s"""* ${field.name}: ${(field.value \ "description").asInstanceOf[JString].s}""")
        .mkString("**Property List:** \n\n", "\n", "")
    } else {
      ""
    }
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
  val bankIdJObject: JObject = ("bank-id" -> ExampleValue.bankIdExample.value)
  
  def getSingleExample: JObject = if (bankId.isDefined){
    val SingleObject: JObject = (singleName -> (JObject(JField(idName, JString(generateUUID())) :: getSingleExampleWithoutId.obj)))
    bankIdJObject merge SingleObject
  } else{
    (singleName -> (JObject(JField(idName, JString(generateUUID())) :: getSingleExampleWithoutId.obj)))
  }

  def getExampleList: JObject =  if (bankId.isDefined){
    val objectList: JObject = (listName -> JArray(List(getSingleExample)))
    bankIdJObject merge objectList 
  } else{
    (listName -> JArray(List(getSingleExample)))
  }

  val canCreateRole: ApiRole = DynamicEntityInfo.canCreateRole(entityName, bankId)
  val canUpdateRole: ApiRole = DynamicEntityInfo.canUpdateRole(entityName, bankId)
  val canGetRole: ApiRole = DynamicEntityInfo.canGetRole(entityName, bankId)
  val canDeleteRole: ApiRole = DynamicEntityInfo.canDeleteRole(entityName, bankId)
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
}