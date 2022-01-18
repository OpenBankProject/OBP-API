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
   bankId: Option[String],
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

  def getById(bankId: Option[String], dynamicResourceDocId: String): Box[JsonDynamicResourceDoc]
  def getByVerbAndUrl(bankId: Option[String], requestVerb: String, requestUrl: String): Box[JsonDynamicResourceDoc]
  
  def getAll(bankId: Option[String]): List[JsonDynamicResourceDoc] = getAllAndConvert(bankId, identity)

  def getAllAndConvert[T: Manifest](bankId: Option[String], transform: JsonDynamicResourceDoc => T): List[T]

  def create(bankId: Option[String], entity: JsonDynamicResourceDoc): Box[JsonDynamicResourceDoc]
  def update(bankId: Option[String], entity: JsonDynamicResourceDoc): Box[JsonDynamicResourceDoc]
  def deleteById(bankId: Option[String], dynamicResourceDocId: String): Box[Boolean]

}
