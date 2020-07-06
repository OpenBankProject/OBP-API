package code.api.v4_0_0

import code.api.util.APIUtil.{Catalogs, EmptyBody, ResourceDoc, authenticationRequiredMessage, generateUUID, notCore, notOBWG, notPSD2}
import code.api.util.ApiRole.getOrCreateDynamicApiRole
import code.api.util.ApiTag.{ResourceDocTag, apiTagApi, apiTagNewStyle}
import code.api.util.ErrorMessages.{InvalidJsonFormat, UnknownError, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole, ApiTag, NewStyle}
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer


object EntityName {

  def unapply(entityName: String): Option[String] = MockerConnector.definitionsMap.keySet.find(entityName ==)

  def unapply(url: List[String]): Option[(String, String)] = url match {
    case entityName :: id :: Nil => MockerConnector.definitionsMap.keySet.find(entityName ==).map((_, id))
    case _ => None
  }

}

object MockerConnector {

  def definitionsMap = NewStyle.function.getDynamicEntities().map(it => (it.entityName, DynamicEntityInfo(it.metadataJson, it.entityName))).toMap

  def doc: ArrayBuffer[ResourceDoc] = {
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
    val fun: DynamicEntityInfo => ArrayBuffer[ResourceDoc] = createDocs(apiTag)
    val docs: Seq[ResourceDoc] = definitionsMap.values.flatMap(fun).toSeq


    collection.mutable.ArrayBuffer(docs:_*)
  }

  // TODO the requestBody and responseBody is not correct ref type
  /**
   *
   * @param fun (singularName, entityName) => ResourceDocTag
   * @param dynamicEntityInfo dynamicEntityInfo
   * @return all ResourceDoc of given dynamicEntity
   */
  private def createDocs(fun: (String, String) => ResourceDocTag)
                (dynamicEntityInfo: DynamicEntityInfo): ArrayBuffer[ResourceDoc] = {
    val entityName = dynamicEntityInfo.entityName
    // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
    val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
    val splitName = capitalizedNameParts.mkString(" ")

    val idNameInUrl = StringHelpers.snakify(dynamicEntityInfo.idName).toUpperCase()
    val listName = dynamicEntityInfo.listName

    val endPoint = APIUtil.dynamicEndpointStub
    val implementedInApiVersion = ApiVersion.v4_0_0
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiTag: ResourceDocTag = fun(splitName, entityName)

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"get${entityName}List",
      "GET",
      s"/$entityName",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List(dynamicEntityInfo.canGetRole))
    )
    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"getSingle$entityName",
      "GET",
      s"/$entityName/$idNameInUrl",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List(dynamicEntityInfo.canGetRole))
    )

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"create$entityName",
      "POST",
      s"/$entityName",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List(dynamicEntityInfo.canCreateRole))
      )

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"update$entityName",
      "PUT",
      s"/$entityName/$idNameInUrl",
      s"Update exists $splitName",
      s"""Update exists $splitName.
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List(dynamicEntityInfo.canUpdateRole))
    )

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"delete$entityName",
      "DELETE",
      s"/$entityName/$idNameInUrl",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List(dynamicEntityInfo.canDeleteRole))
    )

    resourceDocs
  }

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
case class DynamicEntityInfo(definition: String, entityName: String) {

  import net.liftweb.json

  val subEntities: List[DynamicEntityInfo] = Nil

  val idName = StringUtils.uncapitalize(entityName) + "Id"

  val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")

  val jsonTypeMap: Map[String, Class[_]] = DynamicEntityFieldType.nameToValue.mapValues(_.jValueType)

  val definitionJson = json.parse(definition).asInstanceOf[JObject]
  val entity = (definitionJson \ entityName).asInstanceOf[JObject]

  val description = entity \ "description" match {
    case JString(s) if StringUtils.isNotBlank(s) =>
      s"""
        |**Entity Description:**
        |$s
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
          .mkString("**Properties Description:** \n", "\n", "")
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
  def getSingleExample: JObject = JObject(JField(idName, JString(generateUUID())) :: getSingleExampleWithoutId.obj)

  def getExampleList: JObject =   listName -> JArray(List(getSingleExample))

  val canCreateRole: ApiRole = DynamicEntityInfo.canCreateRole(entityName)
  val canUpdateRole: ApiRole = DynamicEntityInfo.canUpdateRole(entityName)
  val canGetRole: ApiRole = DynamicEntityInfo.canGetRole(entityName)
  val canDeleteRole: ApiRole = DynamicEntityInfo.canDeleteRole(entityName)
}

object DynamicEntityInfo {
  def canCreateRole(entityName: String): ApiRole = getOrCreateDynamicApiRole("CanCreateDynamicEntity_" + entityName)
  def canUpdateRole(entityName: String): ApiRole = getOrCreateDynamicApiRole("CanUpdateDynamicEntity_" + entityName)
  def canGetRole(entityName: String): ApiRole = getOrCreateDynamicApiRole("CanGetDynamicEntity_" + entityName)
  def canDeleteRole(entityName: String): ApiRole = getOrCreateDynamicApiRole("CanDeleteDynamicEntity_" + entityName)

  def roleNames(entityName: String): List[String] = List(
      canCreateRole(entityName), canUpdateRole(entityName),
      canGetRole(entityName), canDeleteRole(entityName)
    ).map(_.toString())
}