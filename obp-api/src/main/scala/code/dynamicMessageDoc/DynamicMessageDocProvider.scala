package code.dynamicMessageDoc

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object DynamicMessageDocProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicMessageDocProvider.type = MappedDynamicMessageDocProvider
}

case class JsonDynamicMessageDoc(
  dynamicMessageDocId: Option[String],
  process: String,
  messageFormat: String, 
  description: String, 
  outboundTopic: String, 
  inboundTopic: String, 
  exampleOutboundMessage: String, 
  exampleInboundMessage: String, 
  outboundAvroSchema: String, 
  inboundAvroSchema: String,
  adapterImplementation: String,
  methodBody: String
) extends JsonFieldReName

trait DynamicMessageDocProvider {

  def getById(dynamicMessageDocId: String): Box[JsonDynamicMessageDoc]
  def getAll(): List[JsonDynamicMessageDoc]

  def create(entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc]
  def update(entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc]
  def deleteById(dynamicMessageDocId: String): Box[Boolean]

}