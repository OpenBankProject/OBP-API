package code.endpointTag

/* For Connector endpoint routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.json.Formats
import net.liftweb.json.JsonAST.{JField, JNull, JObject, JString}
import net.liftweb.util.SimpleInjector

object EndpointTagProvider extends SimpleInjector {

  val endpointTagProvider = new Inject(buildOne _) {}

  def buildOne: MappedEndpointTagProvider.type = MappedEndpointTagProvider
}

trait EndpointTagT {
  def endpointTagId: Option[String]
  def operationId: String
  def tagName: String
  def bankId: Option[String]
}

case class EndpointTagCommons(
  endpointTagId: Option[String],
  operationId: String,
  tagName: String,
  bankId: Option[String],
  ) extends EndpointTagT with JsonFieldReName {
  /**
    * when serialized to json, the  Option field will be not shown, this endpoint just generate a full fields json, include all None value fields
    * @return JObject include all fields
    */
  def toJson(implicit format: Formats) = {
    JObject(List(
      JField("operation_id", JString(this.operationId)),
      JField("endpoint_mapping_id", this.endpointTagId.map(JString(_)).getOrElse(JNull)),
      JField("tagName", JString(this.tagName)),
      JField("bankId", JString(this.bankId.getOrElse("")))
    ))
  }
}

object EndpointTagCommons extends Converter[EndpointTagT, EndpointTagCommons]

trait EndpointTagProvider {
  def getById(endpointTagId: String): Box[EndpointTagT]
  
  def getByOperationId(operationId: String): Box[EndpointTagT]
  
  def getAllEndpointTags: List[EndpointTagT]

  def createOrUpdate(endpointTag: EndpointTagT): Box[EndpointTagT]

  def delete(endpointTagId: String):Box[Boolean]
}