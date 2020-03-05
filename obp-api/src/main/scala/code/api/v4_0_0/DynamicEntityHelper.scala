package code.api.v4_0_0

import code.api.util.APIUtil.{Catalogs, ResourceDoc, authenticationRequiredMessage, emptyObjectJson, generateUUID, notCore, notOBWG, notPSD2}
import code.api.util.ApiTag.{apiTagApi, apiTagNewStyle}
import code.api.util.ErrorMessages.{InvalidJsonFormat, UnknownError, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{ApiTag, NewStyle}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils
import org.atteo.evo.inflector.English

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

  def doc = {
    val docs: Seq[ResourceDoc] = definitionsMap.values.flatMap(createDocs).toSeq
    collection.mutable.ArrayBuffer(docs:_*)
  }

  // TODO the requestBody and responseBody is not correct ref type
  def createDocs(dynamicEntityInfo: DynamicEntityInfo) = {
    val entityName = dynamicEntityInfo.entityName
    val idNameInUrl = StringHelpers.snakify(dynamicEntityInfo.idName).toUpperCase()
    val listName = dynamicEntityInfo.listName
    val pluralEntityName = English.plural(entityName)
    val endPoint = APIMethods400.Implementations4_0_0.genericEndpoint
    val implementedInApiVersion = ApiVersion.v4_0_0
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiTag = ApiTag("_" + dynamicEntityInfo.entityName);
    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"get$pluralEntityName",
      "GET",
      s"/${entityName}",
      s"Get $pluralEntityName",
      s"""Get $pluralEntityName.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |
         |Can do filter on the fields
         |e.g: /${entityName}?name=James%20Brown&number=123.456&number=11.11
         |Will do filter by this rule: name == "James Brown" && (number==123.456 || number=11.11)
         |""".stripMargin,
      emptyObjectJson,
      dynamicEntityInfo.getExampleList,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List())
    )
    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"getSingle$pluralEntityName",
      "GET",
      s"/${entityName}/$idNameInUrl",
      s"Get $entityName",
      s"""Get one $entityName by id.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
         |""".stripMargin,
      emptyObjectJson,
      dynamicEntityInfo.getSingleExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTag, apiTagApi, apiTagNewStyle),
      Some(List())
    )

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"create$pluralEntityName",
      "POST",
      s"/${entityName}",
      s"Add $entityName",
      s"""Add a $entityName.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
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
      Some(List()))

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"update$pluralEntityName",
      "PUT",
      s"/${entityName}/$idNameInUrl",
      s"Update $entityName",
      s"""Update a $entityName.
         |${dynamicEntityInfo.description}
         |
         |${dynamicEntityInfo.fieldsDescription}
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
      Some(List()))

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"delete$pluralEntityName",
      "DELETE",
      s"/${entityName}/$idNameInUrl",
      s"Delete $entityName",
      s"""Delete a $entityName.
         |
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
      Some(List()))

    resourceDocs
  }

}
case class DynamicEntityInfo(definition: String, entityName: String) {

  import net.liftweb.json

  val subEntities: List[DynamicEntityInfo] = Nil

  val idName = StringUtils.uncapitalize(entityName) + "Id"

  val listName = StringHelpers.snakify(English.plural(entityName))

  val jsonTypeMap = Map[String, Class[_]](
    ("boolean", classOf[JBool]),
    ("string", classOf[JString]),
    ("array", classOf[JArray]),
    ("integer", classOf[JInt]),
    ("number", classOf[JDouble]),
  )

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
}