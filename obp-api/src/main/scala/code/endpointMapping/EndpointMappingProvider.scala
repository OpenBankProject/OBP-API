package code.endpointMapping

/* For Connector endpoint routing, star connector use this provider to find proxy connector name */

import code.dynamicEntity.DynamicEntity
import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.Formats
import net.liftweb.json.JsonAST.{JArray, JField, JNull, JObject, JString, JValue}
import net.liftweb.util.SimpleInjector

object EndpointMappingProvider extends SimpleInjector {

  val endpointMappingProvider = new Inject(buildOne _) {}

  def buildOne: MappedEndpointMappingProvider.type = MappedEndpointMappingProvider
}

trait EndpointMappingT {
  def endpointMappingId: Option[String]
  def operationId: String
  def requestMapping: String 
  def responseMapping: String 
  def bankId: Option[String] 
}

case class EndpointMappingCommons(
  endpointMappingId: Option[String],
  operationId: String,
  requestMapping: String,
  responseMapping: String,
  bankId: Option[String]
  ) extends EndpointMappingT with JsonFieldReName {
  /**
    * when serialized to json, the  Option field will be not shown, this endpoint just generate a full fields json, include all None value fields
    * @return JObject include all fields
    */
  def toJson(implicit format: Formats) = {
    JObject(List(
      JField("operation_id", JString(this.operationId)),
      JField("request_mapping", json.parse(this.requestMapping)),
      JField("response_mapping", json.parse(this.responseMapping)),
      JField("endpoint_mapping_id", this.endpointMappingId.map(JString(_)).getOrElse(JNull)),
      JField("bank_id", this.bankId.map(JString(_)).getOrElse(JNull))
    ))
  }
}

object EndpointMappingCommons extends Converter[EndpointMappingT, EndpointMappingCommons]

trait EndpointMappingProvider {
  def getById(bankId: Option[String], endpointMappingId: String): Box[EndpointMappingT]
  
  def getByOperationId(bankId: Option[String], operationId: String): Box[EndpointMappingT]
  
  def getAllEndpointMappings(bankId: Option[String]): List[EndpointMappingT]

  def createOrUpdate(bankId: Option[String], endpointMapping: EndpointMappingT): Box[EndpointMappingT]

  def delete(bankId: Option[String], endpointMappingId: String):Box[Boolean]
}






