package code.dynamicResourceDoc

import code.util.UUIDString
import net.liftweb.json
import net.liftweb.mapper._
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

class DynamicResourceDoc extends LongKeyedMapper[DynamicResourceDoc] with IdPK {

  override def getSingleton = DynamicResourceDoc
  
  object DynamicResourceDocId extends UUIDString(this)
  object PartialFunctionName extends MappedString(this, 255)
  object RequestVerb extends MappedString(this, 255)
  object RequestUrl extends MappedString(this, 255)
  object Summary extends MappedString(this, 255)
  object Description extends MappedString(this, 255) 
  object ExampleRequestBody extends MappedString(this, 255)
  object SuccessResponseBody extends MappedString(this, 255)
  object ErrorResponseBodies extends MappedString(this, 255) 
  object Tags extends MappedString(this, 255)
  object Roles extends MappedString(this, 255)
  object MethodBody extends MappedText(this)

}


object DynamicResourceDoc extends DynamicResourceDoc with LongKeyedMetaMapper[DynamicResourceDoc] {
  override def dbIndexes: List[BaseIndex[DynamicResourceDoc]] = UniqueIndex(DynamicResourceDocId) :: UniqueIndex(RequestUrl,RequestVerb) :: super.dbIndexes
  def getJsonDynamicResourceDoc(dynamicResourceDoc: DynamicResourceDoc) = JsonDynamicResourceDoc(
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

