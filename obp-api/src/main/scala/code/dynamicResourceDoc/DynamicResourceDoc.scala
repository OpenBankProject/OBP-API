package code.dynamicResourceDoc

import org.json4s._
import code.util.UUIDString
import com.openbankproject.commons.util.json
import net.liftweb.mapper._
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

class DynamicResourceDoc extends LongKeyedMapper[DynamicResourceDoc] with IdPK with CreatedUpdated {

  override def getSingleton = DynamicResourceDoc

  object BankId extends MappedString(this, 255)
  object DynamicResourceDocId extends UUIDString(this)
  object PartialFunctionName extends MappedString(this, 255)
  object RequestVerb extends MappedString(this, 255)
  object RequestUrl extends MappedString(this, 255)
  object Summary extends MappedString(this, 255)
  object Description extends MappedString(this, 255)
  object ExampleRequestBody extends MappedText(this)
  object SuccessResponseBody extends MappedText(this)
  object ErrorResponseBodies extends MappedText(this)
  object Tags extends MappedString(this, 255)
  object Roles extends MappedString(this, 255)
  object MethodBody extends MappedText(this)
  // Source language of MethodBody: "Scala" (default) or "Java". Mirrors DynamicMessageDoc.Lang /
  // ConnectorMethod.programmingLang — same field name/width convention, see DynamicEndpoints.
  object Lang extends MappedString(this, 50)
  // Provenance: who created / last updated this runtime-compiled endpoint, and a SHA-256 of the
  // (decoded) method body so tampering / drift is detectable. Set server-side from the CallContext
  // user — never from the request body. createdAt / updatedAt come from the CreatedUpdated trait.
  object CreatedByUserId extends MappedString(this, 255)
  object UpdatedByUserId extends MappedString(this, 255)
  object MethodBodyHash extends MappedString(this, 64)

}


object DynamicResourceDoc extends DynamicResourceDoc with LongKeyedMetaMapper[DynamicResourceDoc] {
  override def dbIndexes: List[BaseIndex[DynamicResourceDoc]] = UniqueIndex(DynamicResourceDocId) :: UniqueIndex(RequestUrl,RequestVerb) :: super.dbIndexes
  def getJsonDynamicResourceDoc(dynamicResourceDoc: DynamicResourceDoc) = JsonDynamicResourceDoc(
    bankId = Some(dynamicResourceDoc.BankId.get),
    dynamicResourceDocId = Some(dynamicResourceDoc.DynamicResourceDocId.get),
    methodBody = dynamicResourceDoc.MethodBody.get,
    partialFunctionName = dynamicResourceDoc.PartialFunctionName.get,
    requestVerb = dynamicResourceDoc.RequestVerb.get,
    requestUrl = dynamicResourceDoc.RequestUrl.get,
    summary = dynamicResourceDoc.Summary.get,
    description = dynamicResourceDoc.Description.get,
    exampleRequestBody = Option(dynamicResourceDoc.ExampleRequestBody.get).filter(StringUtils.isNotBlank).map(json.parse),
    successResponseBody = Option(dynamicResourceDoc.SuccessResponseBody.get).filter(StringUtils.isNotBlank).map(json.parse),
    errorResponseBodies = dynamicResourceDoc.ErrorResponseBodies.get,
    tags = dynamicResourceDoc.Tags.get,
    roles = dynamicResourceDoc.Roles.get,
    // Rows created before the Lang column existed have NULL there, not "Scala" -- a bare
    // Lang.get would surface that as an empty/null programming_lang instead of falling back to
    // JsonDynamicResourceDoc's own "Scala" default, since an explicit null argument bypasses a
    // case class default (that only applies when the argument is omitted entirely).
    programmingLang = Option(dynamicResourceDoc.Lang.get).filter(StringUtils.isNotBlank).getOrElse("Scala")
  )
}

