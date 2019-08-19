package code.api.v4_0_0

import code.api.util.APIUtil.generateUUID
import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidUrl}
import code.api.util.NewStyle
import net.liftweb.common.Box
import net.liftweb.json._

import scala.collection.immutable.{List, Nil}


object EntityName {

  def unapply(entityName: String): Option[String] = MockerConnector.definitionsMap.keySet.find(entityName ==)

  def unapply(url: List[String]): Option[(String, String)] = url match {
    case entityName :: id :: Nil => MockerConnector.definitionsMap.keySet.find(entityName ==).map((_, id))
    case _ => None
  }

}

object MockerConnector {

  lazy val definitionsMap = NewStyle.function.getDynamicEntities().map(it => (it.entityName, DynamicEntityInfo(it.metadataJson, it.entityName))).toMap

  val persistedEntities = scala.collection.mutable.Map[String, (String, JObject)]()

  def persist(entityName: String, requestBody: JObject, id: Option[String] = None) = {
    val idValue = id.orElse(Some(generateUUID()))
    val entityToPersist = this.definitionsMap(entityName).toReponse(requestBody, id)
    val haveIdEntity = (entityToPersist \ "id") match {
      case JNothing => JObject(JField("id", JString(idValue.get)) :: entityToPersist.obj)
      case _ => entityToPersist
    }
    persistedEntities.put(idValue.get, (entityName, haveIdEntity))
    haveIdEntity
  }

  def getSingle(entityName: String, id: String) = {
    persistedEntities.find(pair => pair._1 == id && pair._2._1 == entityName).map(_._2._2).getOrElse(throw new RuntimeException(s"$InvalidUrl not exists entity of id = $id"))
  }

  def getAll(entityName: String) = persistedEntities.values.filter(_._1 == entityName).map(_._2)

  def delete(entityName: String, id: String): Box[Boolean] = persistedEntities.exists(it => it._1 == id && it._2._1 == entityName) match {
    case true => persistedEntities.remove(id).map(_ => true)
    case false => Some(false)
  }
}
case class DynamicEntityInfo(defination: String, entityName: String) {

  import net.liftweb.json

  val subEntities: List[DynamicEntityInfo] = Nil

  val jsonTypeMap = Map[String, Class[_]](
    ("boolean", classOf[JBool]),
    ("string", classOf[JString]),
    ("array", classOf[JArray]),
    ("integer", classOf[JInt]),
    ("number", classOf[JDouble]),
  )

  val definationJson = json.parse(defination).asInstanceOf[JObject]
  val entity = (definationJson \ "definitions" \ entityName).asInstanceOf[JObject]

  def toReponse(result: JObject, id: Option[String]): JObject = {

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

    (id, fields.exists(_.name == "id")) match {
      case (Some(idValue), false) => JObject(JField("id", JString(idValue)) :: fields)
      case _ => JObject(fields)
    }
  }
}