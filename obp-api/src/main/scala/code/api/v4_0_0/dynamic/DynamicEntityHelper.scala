package code.api.v4_0_0.dynamic

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
  // unapply result structure: (BankId, entityName, id)
  def unapply(url: List[String]): Option[(Option[String], String, String)] = url match {
    //no bank:
    //eg: /FooBar21
    case entityName ::  Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1 == entityName && definitionMap._2.bankId.isEmpty)
        .map(_ => (None, entityName, ""))
    //eg: /FooBar21/FOO_BAR21_ID
    case entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1 == entityName && definitionMap._2.bankId.isEmpty)
        .map(_ => (None, entityName, id))
      
    //contains Bank:
    //eg: /Banks/BANK_ID/FooBar21
    case "banks" :: bankId :: entityName :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1 == entityName && definitionMap._2.bankId == Some(bankId))
        .map(_ => (Some(bankId), entityName, ""))
    //eg: /Banks/BANK_ID/FooBar21/FOO_BAR21_ID
    case "banks" :: bankId :: entityName :: id :: Nil =>
      DynamicEntityHelper.definitionsMap.find(definitionMap => definitionMap._1 == entityName && definitionMap._2.bankId == Some(bankId))
        .map(_ => (Some(bankId),entityName, id))
      
    case _ => None
  }
}

object DynamicEntityHelper {
  private val implementedInApiVersion = ApiVersion.v4_0_0

  def definitionsMap: Map[String, DynamicEntityInfo] = NewStyle.function.getDynamicEntities().map(it => (it.entityName, DynamicEntityInfo(it.metadataJson, it.entityName, it.bankId))).toMap

  def dynamicEntityRoles: List[String] = NewStyle.function.getDynamicEntities().flatMap(dEntity => DynamicEntityInfo.roleNames(dEntity.entityName, dEntity.bankId))

  def doc: ArrayBuffer[ResourceDoc] = {
    val docs = operationToResourceDoc.values.toList
    collection.mutable.ArrayBuffer(docs:_*)
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
    // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
    val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
    val splitName = capitalizedNameParts.mkString(" ")

    val idNameInUrl = StringHelpers.snakify(dynamicEntityInfo.idName).toUpperCase()
    val listName = dynamicEntityInfo.listName
    val bankId = dynamicEntityInfo.bankId
    val resourceDocUrl = if(bankId.isDefined)  s"/banks/BANK_ID/$entityName" else  s"/$entityName"

    val endPoint = APIUtil.dynamicEndpointStub

    // (operationType, entityName) -> ResourceDoc
    val resourceDocs = scala.collection.mutable.Map[(DynamicEntityOperation, String),ResourceDoc]()
    val apiTag: ResourceDocTag = fun(splitName, entityName)

    resourceDocs += (DynamicEntityOperation.GET_ALL, entityName) -> ResourceDoc(
      endPoint,
      implementedInApiVersion,
      buildGetAllFunctionName(entityName),
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
      List(apiTag, apiTagApi, apiTagNewStyle, apiTagDynamicEndpoint, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole))
    )
    resourceDocs += (DynamicEntityOperation.GET_ONE, entityName) -> ResourceDoc(
      endPoint,
      implementedInApiVersion,
      buildGetOneFunctionName(entityName),
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
      List(apiTag, apiTagApi, apiTagNewStyle, apiTagDynamicEndpoint, apiTagDynamic),
      Some(List(dynamicEntityInfo.canGetRole))
    )

    resourceDocs += (DynamicEntityOperation.CREATE, entityName) -> ResourceDoc(
      endPoint,
      implementedInApiVersion,
      buildCreateFunctionName(entityName),
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
      List(apiTag, apiTagApi, apiTagNewStyle, apiTagDynamicEndpoint, apiTagDynamic),
      Some(List(dynamicEntityInfo.canCreateRole))
      )

    resourceDocs += (DynamicEntityOperation.UPDATE, entityName) -> ResourceDoc(
      endPoint,
      implementedInApiVersion,
      buildUpdateFunctionName(entityName),
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
      List(apiTag, apiTagApi, apiTagNewStyle, apiTagDynamicEndpoint, apiTagDynamic),
      Some(List(dynamicEntityInfo.canUpdateRole))
    )

    resourceDocs += (DynamicEntityOperation.DELETE, entityName) -> ResourceDoc(
      endPoint,
      implementedInApiVersion,
      buildDeleteFunctionName(entityName),
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
      List(apiTag, apiTagApi, apiTagNewStyle, apiTagDynamicEndpoint, apiTagDynamic),
      Some(List(dynamicEntityInfo.canDeleteRole))
    )

    resourceDocs
  }

  private def buildCreateFunctionName(entityName: String) = s"dynamicEntity_create$entityName"
  private def buildUpdateFunctionName(entityName: String) = s"dynamicEntity_update$entityName"
  private def buildDeleteFunctionName(entityName: String) = s"dynamicEntity_delete$entityName"
  private def buildGetOneFunctionName(entityName: String) = s"dynamicEntity_getSingle$entityName"
  private def buildGetAllFunctionName(entityName: String) = s"dynamicEntity_get${entityName}List"

  @inline
  private def buildOperationId(entityName: String, fun: String => String): String = {
    APIUtil.buildOperationId(implementedInApiVersion, fun(entityName))
  }

  def buildCreateOperationId(entityName: String) = buildOperationId(entityName, buildCreateFunctionName)
  def buildUpdateOperationId(entityName: String) = buildOperationId(entityName, buildUpdateFunctionName)
  def buildDeleteOperationId(entityName: String) = buildOperationId(entityName, buildDeleteFunctionName)
  def buildGetOneOperationId(entityName: String) = buildOperationId(entityName, buildGetOneFunctionName)
  def buildGetAllOperationId(entityName: String) = buildOperationId(entityName, buildGetAllFunctionName)

  private def methodRoutingExample(entityName: String) =
    s"""
      |MethodRouting settings example:
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
      |""".stripMargin

}
case class DynamicEntityInfo(definition: String, entityName: String, bankId: Option[String]) {

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
  def canCreateRole(entityName: String, bankId:Option[String]): ApiRole = getOrCreateDynamicApiRole("CanCreateDynamicEntity_" + entityName, bankId.isDefined)
  def canUpdateRole(entityName: String, bankId:Option[String]): ApiRole = getOrCreateDynamicApiRole("CanUpdateDynamicEntity_" + entityName, bankId.isDefined)
  def canGetRole(entityName: String, bankId:Option[String]): ApiRole = getOrCreateDynamicApiRole("CanGetDynamicEntity_" + entityName, bankId.isDefined)
  def canDeleteRole(entityName: String, bankId:Option[String]): ApiRole = getOrCreateDynamicApiRole("CanDeleteDynamicEntity_" + entityName, bankId.isDefined)

  def roleNames(entityName: String, bankId:Option[String]): List[String] = List(
    canCreateRole(entityName, bankId), 
    canUpdateRole(entityName, bankId),
    canGetRole(entityName, bankId), 
    canDeleteRole(entityName, bankId)
  ).map(_.toString())
}