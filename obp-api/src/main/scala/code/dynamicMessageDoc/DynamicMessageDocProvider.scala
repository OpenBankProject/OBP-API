package code.dynamicMessageDoc

import java.net.URLDecoder
import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object DynamicMessageDocProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicMessageDocProvider.type = MappedDynamicMessageDocProvider
}

case class JsonDynamicMessageDoc(
  bankId: Option[String],
  dynamicMessageDocId: Option[String],
  process: String,
  messageFormat: String, 
  description: String, 
  outboundTopic: String, 
  inboundTopic: String, 
  exampleOutboundMessage: JValue, 
  exampleInboundMessage: JValue, 
  outboundAvroSchema: String, 
  inboundAvroSchema: String,
  adapterImplementation: String,
  methodBody: String,
  programmingLang: String
) extends JsonFieldReName{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait DynamicMessageDocProvider {

  def getById(bankId: Option[String], dynamicMessageDocId: String): Box[JsonDynamicMessageDoc]
  def getByProcess(bankId: Option[String], process: String): Box[JsonDynamicMessageDoc]
  def getAll(bankId: Option[String]): List[JsonDynamicMessageDoc]

  def create(bankId: Option[String], entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc]
  def update(bankId: Option[String], entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc]
  def deleteById(bankId: Option[String], dynamicMessageDocId: String): Box[Boolean]

}