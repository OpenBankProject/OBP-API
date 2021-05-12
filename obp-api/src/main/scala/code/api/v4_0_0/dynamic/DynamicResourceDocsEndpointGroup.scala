package code.api.v4_0_0.dynamic

import code.api.util.APIUtil.ResourceDoc
import code.api.util.{APIUtil, ApiRole, ApiTag}
import code.dynamicResourceDoc.{DynamicResourceDocProvider, JsonDynamicResourceDoc}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

object DynamicResourceDocsEndpointGroup extends EndpointGroup {
  override lazy val urlPrefix: String = APIUtil.getPropsValue("url.prefix.dynamic.resourceDoc", "dynamic-resource-doc")


  override protected def resourceDocs: List[APIUtil.ResourceDoc] =
    DynamicResourceDocProvider.provider.vend.getAllAndConvert(toResourceDoc)

  private val apiVersion : ScannedApiVersion = ApiVersion.v4_0_0

  /**
   * this is a function, convert JsonDynamicResourceDoc => ResourceDoc
   * 
   * the core difference between JsonDynamicResourceDoc and ResourceDoc are the following:
   * 
   * 1st: JsonDynamicResourceDoc.methodBody <---vs---> ResourceDoc no methodBody
   * 
   * 2rd: JsonDynamicResourceDoc.exampleRequestBody : Option[JValue] <---vs---> ResourceDoc.exampleRequestBody: scala.Product
   * 
   * 3rd: JsonDynamicResourceDoc no partialFunction <---vs---> partialFunction: OBPEndpoint
   * 
   * ....
   * 
   * We need to prepare the ResourceDoc fields from JsonDynamicResourceDoc.
   * @CompiledObjects also see this class,
   * 
   */
  private val toResourceDoc: JsonDynamicResourceDoc => ResourceDoc = { dynamicDoc =>
    val compiledObjects = CompiledObjects(dynamicDoc.exampleRequestBody, dynamicDoc.successResponseBody, dynamicDoc.methodBody)
    ResourceDoc(
      partialFunction = compiledObjects.partialFunction, //connectorMethodBody
      implementedInApiVersion = apiVersion,
      partialFunctionName = dynamicDoc.partialFunctionName + "_" + (dynamicDoc.requestVerb + dynamicDoc.requestUrl).hashCode,
      requestVerb = dynamicDoc.requestVerb,
      requestUrl = dynamicDoc.requestUrl,
      summary = dynamicDoc.summary,
      description = dynamicDoc.description,
      exampleRequestBody = compiledObjects.requestBody,// compiled case object
      successResponseBody = compiledObjects.successResponse, //compiled case object
      errorResponseBodies = StringUtils.split(dynamicDoc.errorResponseBodies,",").toList,
      tags = dynamicDoc.tags.split(",").map(ApiTag(_)).toList,
      roles = Option(dynamicDoc.roles)
        .filter(StringUtils.isNoneBlank(_))
        .map { it =>
            StringUtils.split(it, ",")
              .map(ApiRole.getOrCreateDynamicApiRole(_))
              .toList
        }
    )
  }
}
