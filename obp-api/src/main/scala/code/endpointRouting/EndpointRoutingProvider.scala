package code.endpointRouting

/* For Connector endpoint routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.json.{Formats}
import net.liftweb.json.JsonAST.{JField, JNull, JObject, JString}
import net.liftweb.util.SimpleInjector

object EndpointRoutingProvider extends SimpleInjector {

  val endpointRoutingProvider = new Inject(buildOne _) {}

  def buildOne: MappedEndpointRoutingProvider.type = MappedEndpointRoutingProvider
}

trait EndpointRoutingT {
  def endpointRoutingId: Option[String]
  def operationId: String
  def requestMapping: String 
  def responseMapping: String 
}

case class EndpointRoutingCommons(
  endpointRoutingId: Option[String],
  operationId: String,
  requestMapping: String,
  responseMapping: String) extends EndpointRoutingT with JsonFieldReName {
  /**
    * when serialized to json, the  Option field will be not shown, this endpoint just generate a full fields json, include all None value fields
    * @return JObject include all fields
    */
  def toJson(implicit format: Formats) = {
    JObject(List(
      JField("operation_id", JString(this.operationId)),
      JField("request_mapping", JString(this.requestMapping)),
      JField("response_mapping", JString(this.responseMapping)),
      JField("endpoint_routing_id", this.endpointRoutingId.map(JString(_)).getOrElse(JNull))
    ))
  }
}

object EndpointRoutingCommons extends Converter[EndpointRoutingT, EndpointRoutingCommons]

trait EndpointRoutingProvider {
  def getById(endpointRoutingId: String): Box[EndpointRoutingT]
  
  def getByEndpointRoutings: List[EndpointRoutingT]

  def createOrUpdate(endpointRouting: EndpointRoutingT): Box[EndpointRoutingT]

  def delete(endpointRoutingId: String):Box[Boolean]
}






