package code.dynamicEntity

import code.api.util.ErrorMessages.InvalidJsonFormat
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{JArray, JBool, JDouble, JField, JInt, JNothing, JNull, JObject, JString, compactRender, parse}
import net.liftweb.util.SimpleInjector

object DynamicEntityProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicEntityProvider.type = MappedDynamicEntityProvider
}

trait DynamicEntityT {
  def dynamicEntityId: Option[String]
  def entityName: String
  def metadataJson: String


  //---------util methods

  private lazy val definition: JObject = parse(metadataJson).asInstanceOf[JObject]
  //convert metadataJson to JValue, so the final json field metadataJson have no escaped " to \", have good readable
  lazy val jValue = dynamicEntityId match {
    case Some(id) => {
      val jId: JObject = "dynamicEntityId" -> id
      // add dynamicEntityId to JObject
      definition merge jId
    }
    case None => definition
  }

  /**
   * validate the commit json whether fulfil DynamicEntity schema
   * @param entityJson commit json object to add new instance of given dynamic entity
   * @return return Success[Unit], or return Left[String] error message
   */
  def validateEntityJson(entityJson: JObject): Either[String, Unit] = {
    val required: List[String] = (definition \ entityName \ "required").asInstanceOf[JArray].arr.map(_.asInstanceOf[JString].s)

    val missingProperties = required diff entityJson.obj.map(_.name)

    if(missingProperties.nonEmpty) {
      return Left(s"$InvalidJsonFormat The 'required' field's not be fulfilled, missing properties: ${missingProperties.mkString(", ")}")
    }

    val invalidPropertyMsg = (definition \ entityName \ "properties").asInstanceOf[JObject].obj
      .map(it => {
        val JField(propertyName, propertyDef: JObject) = it
        val propertyTypeName = (propertyDef \ "type").asInstanceOf[JString].s
        (propertyName, propertyTypeName)
      })
      .map(it => {
        val (propertyName, propertyType) = it
        val propertyValue = entityJson \ propertyName
        propertyType match {
          case _ if propertyValue == JNothing || propertyValue == JNull  => "" // required properties already checked.
          case "string"  if !propertyValue.isInstanceOf[JString]         => s"$InvalidJsonFormat The type of '$propertyName' should be string"
          case "number"  if !propertyValue.isInstanceOf[JDouble]         => s"$InvalidJsonFormat The type of '$propertyName' should be number"
          case "integer" if !propertyValue.isInstanceOf[JInt]            => s"$InvalidJsonFormat The type of '$propertyName' should be integer"
          case "boolean" if !propertyValue.isInstanceOf[JBool]           => s"$InvalidJsonFormat The type of '$propertyName' should be boolean"
          case "array"   if !propertyValue.isInstanceOf[JArray]          => s"$InvalidJsonFormat The type of '$propertyName' should be array"
          case "object"  if !propertyValue.isInstanceOf[JObject]         => s"$InvalidJsonFormat The type of '$propertyName' should be object"
          case _                                                         => ""
        }
      })
      .filter(_.nonEmpty)
      .mkString("; ")
    if(invalidPropertyMsg.nonEmpty) {
      Left(invalidPropertyMsg)
    } else {
      Right(Unit)
    }
  }
}

case class DynamicEntityCommons(entityName: String,
                                metadataJson: String,
                                dynamicEntityId: Option[String] = None
                               ) extends DynamicEntityT with JsonFieldReName

object DynamicEntityCommons extends Converter[DynamicEntityT, DynamicEntityCommons] {

  /**
   * create DynamicEntityCommons object, and do validation
   *
   * @param jsonObject the follow schema json:
   * {{{
   *   {
   *     "FooBar": {
   *         "required": [
   *             "name"
   *         ],
   *         "properties": {
   *             "name": {
   *                 "type": "string",
   *                 "example": "James Brown"
   *             },
   *             "number": {
   *                 "type": "integer",
   *                 "example": "698761728934"
   *             }
   *         }
   *     }
   * }
   * }}}
   * @param dynamicEntityId
   * @return object of DynamicEntityCommons
   */
  def apply(jsonObject: JObject, dynamicEntityId: Option[String]): DynamicEntityCommons = {

    def checkFormat(requirement: Boolean, message: String) = {
      if (!requirement) throw new IllegalArgumentException(message)
    }

    val fields = jsonObject.obj

    // validate whether json is object and have a single field, currently support one entity definition
    checkFormat(fields.nonEmpty, s"$InvalidJsonFormat The Json root object should have a single entity, but current have none.")
    checkFormat(fields.size == 1, s"$InvalidJsonFormat The Json root object should have a single entity, but current entityNames: ${fields.map(_.name).mkString(",  ")}")

    val JField(entityName, metadataJson) = fields.head

    // validate entityName corresponding value is json object
    val metadataStr = compactRender(metadataJson)
    checkFormat(metadataJson.isInstanceOf[JObject], s"$InvalidJsonFormat The $entityName should have an object value, but current value is: $metadataStr")

    val required = metadataJson \ "required"

    // validate 'required' field exists and is a json array[string]
    checkFormat(required != JNothing , s"$InvalidJsonFormat There must be 'required' field in $entityName, and type is json array[string]")
    checkFormat(required.isInstanceOf[JArray] && required.asInstanceOf[JArray].arr.forall(_.isInstanceOf[JString]), s"$InvalidJsonFormat The 'required' field's type of $entityName should be array[string]")

    val properties = metadataJson \ "properties"

    // validate 'properties' field exists and is json object
    checkFormat(properties != JNothing , s"$InvalidJsonFormat There must be 'required' field in $entityName, and type is array[string]")
    checkFormat(properties.isInstanceOf[JObject], s"$InvalidJsonFormat The 'properties' field's type of $entityName should be json object")

    val propertiesObj = properties.asInstanceOf[JObject]

    val requiredFields = required.asInstanceOf[JArray].arr.map(_.asInstanceOf[JString].s)

    val allFields = propertiesObj.obj

    val missingRequiredFields = requiredFields diff allFields.map(_.name)

    checkFormat(missingRequiredFields.isEmpty , s"$InvalidJsonFormat missing properties: ${missingRequiredFields.mkString(", ")}")

    // validate there is no required field missing in properties
    val notFoundRequiredField = requiredFields.diff(allFields.map(_.name))
    checkFormat(metadataJson.isInstanceOf[JObject], s"$InvalidJsonFormat In the $entityName, all 'required' fields should be present, these are missing: ${notFoundRequiredField.mkString(", ")}")

    // validate all properties have a type and example
    allFields.foreach(field => {
      val JField(fieldName, value) = field
      checkFormat(value.isInstanceOf[JObject], s"$InvalidJsonFormat The property of $fieldName's type should be json object")

      // 'type' exists and value should be one of allowed type
      val fieldType = value \ "type"
      checkFormat(fieldType.isInstanceOf[JString] && fieldType.asInstanceOf[JString].s.nonEmpty, s"$InvalidJsonFormat The property of $fieldName's 'type' field should be exists and type is json string")
      checkFormat(allowedFieldType.contains(fieldType.asInstanceOf[JString].s), s"$InvalidJsonFormat The property of $fieldName's 'type' field should be json string and value should be one of: ${allowedFieldType.mkString(", ")}")

      // example is exists
      val fieldExample = value \ "example"
      checkFormat(fieldExample != JNothing, s"$InvalidJsonFormat The property of $fieldName's 'example' field should be exists")
      // example type is correct
      val dEntityFieldType: DynamicEntityFieldType = DynamicEntityFieldType.withName(fieldType.asInstanceOf[JString].s)
      checkFormat(dEntityFieldType.isJValueValid(fieldExample), s"$InvalidJsonFormat The property of $fieldName's 'example' field should be type $dEntityFieldType")
    })

    DynamicEntityCommons(entityName, compactRender(jsonObject), dynamicEntityId)
  }

  private val allowedFieldType: Set[String] = DynamicEntityFieldType.values.map(_.toString).toSet
}

/**
 * example case classes, as an example schema of DynamicEntity, for request body example usage
 * @param FooBar
 */
case class DynamicEntityFooBar(FooBar: DynamicEntityDefinition, dynamicEntityId: Option[String] = None)
case class DynamicEntityDefinition(required: List[String],properties: DynamicEntityFullBarFields)
case class DynamicEntityFullBarFields(name: DynamicEntityStringTypeExample, number: DynamicEntityIntTypeExample)
case class DynamicEntityStringTypeExample(`type`: DynamicEntityFieldType, example: String)
case class DynamicEntityIntTypeExample(`type`: DynamicEntityFieldType, example: Int)
//-------------------example case class end


trait DynamicEntityProvider {
  def getById(dynamicEntityId: String): Box[DynamicEntityT]

  def getByEntityName(entityName: String): Box[DynamicEntityT]

  def getDynamicEntities(): List[DynamicEntityT]

  def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT]

  def delete(dynamicEntityId: String):Box[Boolean]
}






