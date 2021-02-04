package code.connectormethod

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import java.net.URLDecoder

object ConnectorMethodProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedConnectorMethodProvider.type = MappedConnectorMethodProvider
}

case class JsonConnectorMethod(internalConnectorId: Option[String], methodName: String, methodBody: String) extends JsonFieldReName{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

case class JsonConnectorMethodMethodBody(methodBody: String) extends JsonFieldReName {
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
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
