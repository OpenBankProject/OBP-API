package code.dynamicResourceDoc

import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.JsonAST.JNothing
import net.liftweb.json.{Formats, JValue, JsonAST}
import net.liftweb.util.SimpleInjector
import org.apache.commons.lang3.StringUtils

import java.net.URLDecoder
import scala.collection.immutable.List

object DynamicResourceDocProvider extends SimpleInjector {

  val provider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicResourceDocProvider.type = MappedDynamicResourceDocProvider
}

case class JsonDynamicResourceDoc(
   dynamicResourceDocId: Option[String],
   methodBody: String,
   partialFunctionName: String,
   requestVerb: String,
   requestUrl: String,
   summary: String,
   description: String,
   exampleRequestBody: Option[JValue],
   successResponseBody: Option[JValue],
   errorResponseBodies: String,
   tags: String,
   roles: String
) extends JsonFieldReName {
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
