package code.methodrouting

/* For Connector method routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, JsonFieldReName, ProductCollection, ProductCollectionCommons}
import net.liftweb.common.Box
import net.liftweb.json.JsonAST.{JArray, JBool, JField, JNull, JObject, JString}
import net.liftweb.util.SimpleInjector

object MethodRoutingProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedMethodRoutingProvider.type = MappedMethodRoutingProvider
}

trait MethodRoutingT {
  def methodRoutingId: Option[String]
  def methodName: String
  def bankIdPattern: Option[String]

  /**
    * whether bankIdPattern is exact match the bankId value, or regex expression match
    * @return true if exact match, false if regex match
    */
  def isBankIdExactMatch: Boolean
  def connectorName: String
  def parameters: List[MethodRoutingParam]
}

case class MethodRoutingCommons(methodName: String,
                                connectorName: String,
                                isBankIdExactMatch: Boolean,
                                bankIdPattern: Option[String],
                                parameters: List[MethodRoutingParam] = Nil,
                                methodRoutingId: Option[String] = None,
                               ) extends MethodRoutingT with JsonFieldReName {
  /**
    * when serialized to json, the  Option filed will be not shown, this method just generate a full fields json, include all None value fields
    * @return JObject include all fields
    */
  def toJson = {
    val paramsJson: List[JObject] = this.parameters.map(param => JObject(List(JField("key", JString(param.key)), JField("value", JString(param.value)))))

    JObject(List(
      JField("method_name", JString(this.methodName)),
      JField("connector_name", JString(this.connectorName)),
      JField("is_bank_id_exact_match", JBool(this.isBankIdExactMatch)),
      JField("bank_id_pattern", this.bankIdPattern.map(JString(_)).getOrElse(JString("*"))),
      JField("parameters", JArray(paramsJson)),
      JField("method_routing_id", this.methodRoutingId.map(JString(_)).getOrElse(JNull))
    ))
  }
}

object MethodRoutingCommons extends Converter[MethodRoutingT, MethodRoutingCommons]

case class MethodRoutingParam(key: String, value: String)

trait MethodRoutingProvider {
  def getById(methodRoutingId: String): Box[MethodRoutingT]

  def getMethodRoutings(methodName: Option[String], isBankIdExactMatch: Option[Boolean] = None, bankIdPattern: Option[String] = None): List[MethodRoutingT]

  def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT]

  def delete(methodRoutingId: String):Box[Boolean]
}






