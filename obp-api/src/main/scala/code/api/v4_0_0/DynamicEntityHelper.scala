package code.api.v4_0_0

import code.api.util.APIUtil.{Catalogs, ResourceDoc, authenticationRequiredMessage, emptyObjectJson, generateUUID, notCore, notOBWG, notPSD2}
import code.api.util.ApiTag.{apiTagApi, apiTagNewStyle}
import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidUrl, UnknownError, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{ApiTag, ApiVersion, NewStyle}
import net.liftweb.common.Box
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

  //(id, entityName) -> entity
  val persistedEntities = scala.collection.mutable.Map[(String, String), JObject]()

  def persist(entityName: String, requestBody: JObject, id: Option[String] = None) = {
    val idValue = id.orElse(Some(generateUUID()))
    val idName = StringUtils.uncapitalize(entityName) + "Id"
    val entityToPersist = this.definitionsMap(entityName).toResponse(requestBody, id)
    val haveIdEntity = (entityToPersist \ idName) match {
      case JNothing => JObject(JField(idName, JString(idValue.get)) :: entityToPersist.obj)
      case _ => entityToPersist
    }
    persistedEntities.put((idValue.get, entityName), haveIdEntity)
    haveIdEntity
  }

  def getSingle(entityName: String, id: String) = {
    val idName = StringUtils.uncapitalize(entityName) + "Id"
    require(persistedEntities.contains(id -> entityName), s"$InvalidUrl not exists ${entityName} of ${idName} = $id")
    persistedEntities.get(id, entityName)
  }

  def getAll(entityName: String) = persistedEntities.filter(pair => pair._1._2 == entityName).values

  def delete(entityName: String, id: String): Box[Boolean] = persistedEntities.remove(id -> entityName).map(_ => true)

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

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"get$pluralEntityName",
      "GET",
      s"/${entityName}",
      s"Get all $pluralEntityName",
      s"""Get all $pluralEntityName.""",
      emptyObjectJson,
      dynamicEntityInfo.getExampleList,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(ApiTag(dynamicEntityInfo.entityName), apiTagApi, apiTagNewStyle),
      Some(List())
    )
    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"getSingle$pluralEntityName",
      "GET",
      s"/${entityName}/$idNameInUrl",
      s"Get one $entityName",
      s"""Get one $entityName.""",
      emptyObjectJson,
      dynamicEntityInfo.getSingleExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(ApiTag(dynamicEntityInfo.entityName), apiTagApi, apiTagNewStyle),
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
      List(ApiTag(dynamicEntityInfo.entityName), apiTagApi, apiTagNewStyle),
      Some(List()))

    resourceDocs += ResourceDoc(
      endPoint,
      implementedInApiVersion,
      s"update$pluralEntityName",
      "PUT",
      s"/${entityName}/$idNameInUrl",
      s"Update $entityName",
      s"""Update a $entityName.
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
      List(ApiTag(dynamicEntityInfo.entityName), apiTagApi, apiTagNewStyle),
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
      List(ApiTag(dynamicEntityInfo.entityName), apiTagApi, apiTagNewStyle),
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

  def toResponse(result: JObject, id: Option[String]): JObject = {

    val fieldNameToTypeName: Map[String, String] = (entity \ "properties")
      .asInstanceOf[JObject]
      .obj
      .map(field => (field.name, (field.value \ "type").asInstanceOf[JString].s))
      .toMap

    val fieldNameToType: Map[String, Class[_]] = fieldNameToTypeName
      .mapValues(jsonTypeMap(_))

    val requiredFieldNames: Set[String] = (entity \ "required").asInstanceOf[JArray].arr.map(_.asInstanceOf[JString].s).toSet

    val fields = result.obj.filter(it => fieldNameToType.keySet.contains(it.name))

    def check(v: Boolean, msg: String) = if (!v) throw new RuntimeException(msg)
    // if there are field type are not match the definitions, there must be bug.
    fields.foreach(it => check(fieldNameToType(it.name).isInstance(it.value), s"""$InvalidJsonFormat "${it.name}" required type is "${fieldNameToTypeName(it.name)}"."""))
    // if there are required field not presented, must be some bug.
    requiredFieldNames.foreach(it => check(fields.exists(_.name == it), s"""$InvalidJsonFormat required field "$it" not presented."""))

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