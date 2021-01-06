package code.validation

/* For CardAttribute */

import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector
import net.liftweb.json.JsonDSL._
import net.liftweb.json

object JsonSchemaValidationProvider extends SimpleInjector {

  val validationProvider = new Inject(buildOne _) {}

  def buildOne: MappedJsonSchemaValidationProvider.type = MappedJsonSchemaValidationProvider
}

case class JsonValidation(operationId: String, jsonSchema: String) extends JsonAble {

  override def toJValue(implicit format: Formats): JsonAST.JValue =
    ("operation_id", operationId) ~ ("json_schema", json.parse(jsonSchema))
}

trait JsonSchemaValidationProvider {

  def getByOperationId(operationId: String): Box[JsonValidation]

  def getAll(): List[JsonValidation]

  def create(jsonValidation: JsonValidation): Box[JsonValidation]
  def update(jsonValidation: JsonValidation): Box[JsonValidation]
  def deleteByOperationId(operationId: String): Box[Boolean]

}
