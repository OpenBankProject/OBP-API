package code.dynamicResourceDoc

import org.json4s._
import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JNothing
import org.json4s.{Formats, JValue, JsonAST}
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
   roles: String,
   // Read-only provenance, populated server-side on the way out. Any value a caller supplies for
   // these on create/update is ignored (see MappedDynamicResourceDocProvider) — provenance is taken
   // from the authenticated CallContext user and computed hash, never trusted from the request body.
   createdByUserId: Option[String] = None,
   updatedByUserId: Option[String] = None,
   methodBodyHash: Option[String] = None,
   createdAt: Option[String] = None,
   updatedAt: Option[String] = None
) extends JsonFieldReName {
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait DynamicResourceDocProvider {

  def getById(bankId: Option[String], dynamicResourceDocId: String): Box[JsonDynamicResourceDoc]
  def getByVerbAndUrl(bankId: Option[String], requestVerb: String, requestUrl: String): Box[JsonDynamicResourceDoc]

  def getAll(bankId: Option[String]): List[JsonDynamicResourceDoc] = getAllAndConvert(bankId, identity)

  def getAllAndConvert[T: Manifest](bankId: Option[String], transform: JsonDynamicResourceDoc => T): List[T]

  def create(bankId: Option[String], entity: JsonDynamicResourceDoc, createdByUserId: Option[String]): Box[JsonDynamicResourceDoc]
  def update(bankId: Option[String], entity: JsonDynamicResourceDoc, updatedByUserId: Option[String]): Box[JsonDynamicResourceDoc]
  def deleteById(bankId: Option[String], dynamicResourceDocId: String): Box[Boolean]

}
