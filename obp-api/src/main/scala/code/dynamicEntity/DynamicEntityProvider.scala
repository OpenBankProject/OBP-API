package code.dynamicEntity

import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{JField, JObject, JsonAST}
import net.liftweb.util.SimpleInjector

object DynamicEntityProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicEntityProvider.type = MappedDynamicEntityProvider
}

trait DynamicEntityT {
  def dynamicEntityId: Option[String]
  def entityName: String
  def metadataJson: String
}

case class DynamicEntityCommons(entityName: String,
                                metadataJson: String,
                                dynamicEntityId: Option[String] = None,
                               ) extends DynamicEntityT with JsonFieldReName {
  private val definition: JObject = net.liftweb.json.parse(metadataJson).asInstanceOf[JObject]
  //convert metadataJson to JValue, so the final json field metadataJson have no escaped " to \", have good readable
  lazy val jValue = dynamicEntityId match {
    case Some(id) => {
      val jId: JObject = "dynamicEntityId" -> id
      // add dynamicEntityId to JObject
      definition merge jId
    }
    case None => definition
  }
}

/**
 * an example schema of DynamicEntity, this is as request body example usage
 * @param FooBar
 */
case class DynamicEntityFooBar(FooBar: DynamicEntityDefinition, dynamicEntityId: Option[String] = None)
case class DynamicEntityDefinition(required: List[String],properties: DynamicEntityFullBarFields)
case class DynamicEntityFullBarFields(name: DynamicEntityTypeExample, number: DynamicEntityTypeExample)
case class DynamicEntityTypeExample(`type`: DynamicEntityFieldType, example: String)

object DynamicEntityCommons extends Converter[DynamicEntityT, DynamicEntityCommons]


trait DynamicEntityProvider {
  def getById(dynamicEntityId: String): Box[DynamicEntityT]

  def getDynamicEntities(): List[DynamicEntityT]

  def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT]

  def delete(dynamicEntityId: String):Box[Boolean]
}






