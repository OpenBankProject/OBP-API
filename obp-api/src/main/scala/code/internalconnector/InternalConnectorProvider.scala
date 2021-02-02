package code.internalconnector

/* For CardAttribute */

import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector

object InternalConnectorProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedAuthTypeValidationProvider.type = MappedAuthTypeValidationProvider
}

case class JsonInternalConnector(internalConnectorId: Option[String], methodName: String, methodBody: String) extends JsonAble with JsonFieldReName{
  override def toJValue(implicit format: Formats): JsonAST.JValue = internalConnectorId match{
    case Some(id) =>  ("internal_connector_id", id) ~ ("method_name", methodName) ~ ("method_body", methodBody)
    case _ => ("method_name", methodName) ~ ("method_body", methodBody)
  }
}

case class JsonInternalConnectorMethodBody(methodBody: String)extends JsonAble with JsonFieldReName {
  override def toJValue(implicit format: Formats): JsonAST.JValue = ("method_body", methodBody)
}

trait InternalConnectorProvider {

  def getById(internalConnectorId: String): Box[JsonInternalConnector]
  def getByMethodNameWithCache(methodName: String): Box[JsonInternalConnector]
  def getByMethodNameWithoutCache(methodName: String): Box[JsonInternalConnector]

  def getAll(): List[JsonInternalConnector]

  def create(entity: JsonInternalConnector): Box[JsonInternalConnector]
  def update(internalConnectorId: String, connectorMethodBody: String): Box[JsonInternalConnector]
  def deleteById(internalConnectorId: String): Box[Boolean]

}
