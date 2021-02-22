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
   exampleRequestBody: String,
   successResponseBody: String,
   errorResponseBodies: String,
   tags: String,
   roles: String
) extends JsonFieldReName with JsonAble{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")

  override def toJValue(implicit format: Formats): JsonAST.JValue = {
    import net.liftweb.json.JsonDSL._
    val requestBody:JValue = if(StringUtils.isBlank(exampleRequestBody)) JNothing else json.parse(exampleRequestBody)
    val responseBody:JValue = if(StringUtils.isBlank(successResponseBody)) JNothing else json.parse(successResponseBody)

      ("dynamic_resource_doc_id" -> dynamicResourceDocId) ~
        ("request_verb" -> requestVerb) ~
        ("request_url" -> requestUrl) ~
        ("example_request_body" -> requestBody) ~
        ("success_response_body" -> responseBody) ~
        ("partial_function_name" -> partialFunctionName) ~
        ("error_response_bodies" -> errorResponseBodies) ~
        ("summary" -> summary) ~
        ("description" -> description) ~
        ("tags" -> tags) ~
        ("roles" -> roles) ~
        ("method_body" -> methodBody)
  }
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
