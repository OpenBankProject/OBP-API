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
  object Description extends MappedText(this)
  object ExampleRequestBody extends MappedText(this)
  object SuccessResponseBody extends MappedText(this)
  object ErrorResponseBodies extends MappedText(this)
  object Tags extends MappedText(this)
  object Roles extends MappedText(this)
  object MethodBody extends MappedText(this)
  // Provenance: who created / last updated this runtime-compiled endpoint, and a SHA-256 of the
  // (decoded) method body so tampering / drift is detectable. Set server-side from the CallContext
  // user — never from the request body. createdAt / updatedAt come from the CreatedUpdated trait.
  object CreatedByUserId extends MappedString(this, 255)
  object UpdatedByUserId extends MappedString(this, 255)
  object MethodBodyHash extends MappedString(this, 64)
  // Maker/checker (see MAKER_CHECKER_DYNAMIC_CODE_DESIGN.md): the runtime only loads this row when
  // IsActive is true and, when maker/checker is enabled for this target type, when MethodBodyHash
  // equals ApprovedHash. ApprovedHash is written only by an approved DynamicChangeRequest (or the
  // one-off seeding of pre-existing rows when the feature is first enabled), never from a request body.
  object ApprovedHash extends MappedString(this, 64)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }

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
    roles = dynamicResourceDoc.Roles.get
  )
}

