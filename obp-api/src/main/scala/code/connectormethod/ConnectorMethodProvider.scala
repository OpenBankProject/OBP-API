package code.connectormethod

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import java.net.URLDecoder

object ConnectorMethodProvider extends SimpleInjector {

  val provider = new Inject(() => buildOne) {}

  def buildOne: DoobieConnectorMethodProvider.type = DoobieConnectorMethodProvider
}

case class JsonConnectorMethod(connectorMethodId: Option[String], methodName: String, methodBody: String, programmingLang: String="Scala") extends JsonFieldReName{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

case class JsonConnectorMethodMethodBody(methodBody: String, programmingLang: String="Scala") extends JsonFieldReName {
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait ConnectorMethodProvider {

  def getById(connectorMethodId: String): Box[JsonConnectorMethod]
  def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod]
  def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod]

  def getAll(): List[JsonConnectorMethod]

  def create(entity: JsonConnectorMethod, createdByUserId: Option[String]): Box[JsonConnectorMethod]
  def update(connectorMethodId: String, connectorMethodBody: String, programmingLang: String, updatedByUserId: Option[String]): Box[JsonConnectorMethod]
  def deleteById(connectorMethodId: String): Box[Boolean]

}

/**
 * A connector method plus the provenance columns, for the v7.0.0 read-only endpoints.
 *
 * Kept separate from JsonConnectorMethod because that one is the request/response contract for
 * create and update, and adding server-set fields to it would let a caller submit them.
 */
case class ConnectorMethodWithProvenance(
  connectorMethod: JsonConnectorMethod,
  createdByUserId: Option[String],
  updatedByUserId: Option[String],
  methodBodyHash: Option[String],
  createdAt: Option[java.util.Date],
  updatedAt: Option[java.util.Date]
)
