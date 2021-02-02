package code.connectormethod

/* For CardAttribute */

import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector

import java.net.URLDecoder

object ConnectorMethodProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedConnectorMethodProvider.type = MappedConnectorMethodProvider
}

case class JsonConnectorMethod(internal_connector_id: Option[String], method_name: String, method_body: String) extends JsonAble with JsonFieldReName{
  override def toJValue(implicit format: Formats): JsonAST.JValue = internal_connector_id match{
    case Some(id) =>  ("internal_connector_id", id) ~ ("method_name", method_name) ~ ("method_body", method_body)
    case _ => ("method_name", method_name) ~ ("method_body", method_body)
  }
  def decodedMethodBody: String = URLDecoder.decode(method_body, "UTF-8")
}

case class JsonConnectorMethodMethodBody(method_body: String) extends JsonAble with JsonFieldReName {
  override def toJValue(implicit format: Formats): JsonAST.JValue = ("method_body", method_body)

  def decodedMethodBody: String = URLDecoder.decode(method_body, "UTF-8")
}

trait ConnectorMethodProvider {

  def getById(connectorMethodId: String): Box[JsonConnectorMethod]
  def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod]
  def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod]

  def getAll(): List[JsonConnectorMethod]

  def create(entity: JsonConnectorMethod): Box[JsonConnectorMethod]
  def update(connectorMethodId: String, connectorMethodBody: String): Box[JsonConnectorMethod]
  def deleteById(connectorMethodId: String): Box[Boolean]

}
