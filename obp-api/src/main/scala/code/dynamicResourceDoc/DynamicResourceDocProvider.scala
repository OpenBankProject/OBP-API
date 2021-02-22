package code.dynamicResourceDoc

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import java.net.URLDecoder

import scala.collection.immutable.List

object DynamicResourceDocProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicResourceDocProvider.type = MappedDynamicResourceDocProvider
}

case class JsonDynamicResourceDoc(
   dynamicResourceDocId: Option[String],
   methodBody: String,
   partialFunction: String,
   partialFunctionName: String,
   requestVerb: String,
   requestUrl: String,
   summary: String,
   description: String,
   exampleRequestBody: String,
   successResponseBody: String,
   errorResponseBodies: String,
   tags: String,
   roles: String
) extends JsonFieldReName{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait DynamicResourceDocProvider {

  def getById(dynamicResourceDocId: String): Box[JsonDynamicResourceDoc]
  def getByVerbAndUrl(requestVerb: String, requestUrl: String): Box[JsonDynamicResourceDoc]
  
  def getAll(): List[JsonDynamicResourceDoc] = getAllAndConvert(identity)

  def getAllAndConvert[T: Manifest](transform: JsonDynamicResourceDoc => T): List[T]

  def create(entity: JsonDynamicResourceDoc): Box[JsonDynamicResourceDoc]
  def update(entity: JsonDynamicResourceDoc): Box[JsonDynamicResourceDoc]
  def deleteById(dynamicResourceDocId: String): Box[Boolean]

}
