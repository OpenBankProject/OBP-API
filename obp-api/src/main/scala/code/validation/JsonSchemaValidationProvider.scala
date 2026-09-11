package code.validation

/* For CardAttribute */

import org.json4s._
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import org.json4s.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector
import org.json4s.JsonDSL._
import com.openbankproject.commons.util.json

object JsonSchemaValidationProvider extends SimpleInjector {

  val validationProvider = new Inject(() => buildOne) {}

  // Widened from MappedJsonSchemaValidationProvider.type: the return type named the concrete
  // object, so the provider could not be swapped without changing this line too.
  def buildOne: JsonSchemaValidationProvider = DoobieJsonSchemaValidationProvider
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
