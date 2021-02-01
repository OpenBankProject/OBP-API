package code.internalconnector

/* For CardAttribute */

import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector

object InternalConnectorProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedAuthTypeValidationProvider.type = MappedAuthTypeValidationProvider
}

case class JsonInternalConnector(internalConnectorId: String, methodName: String, methodBody: String) extends JsonAble {
  override def toJValue(implicit format: Formats): JsonAST.JValue =
    ("internal_connector_id", internalConnectorId) ~ ("method_name", methodName) ~ ("method_body", methodBody)
}

trait InternalConnectorProvider {

  def getById(internalConnectorId: String): Box[JsonInternalConnector]
  def getByMethodName(methodName: String): Box[JsonInternalConnector]

  def getAll(): List[JsonInternalConnector]

  def create(entity: JsonInternalConnector): Box[JsonInternalConnector]
  def update(entity: JsonInternalConnector): Box[JsonInternalConnector]
  def deleteById(internalConnectorId: String): Box[Boolean]

}
