package code.api.v4_0_0.dynamic.practise

import code.api.util.APIUtil
import code.api.util.APIUtil.{ResourceDoc, StringBody}
import code.api.util.ApiTag.{apiTagDynamicResourceDoc, apiTagNewStyle}
import code.api.util.ErrorMessages.UnknownError
import code.api.v4_0_0.dynamic.EndpointGroup
import com.openbankproject.commons.util.ApiVersion

import scala.collection.immutable.List

/**
 * this is just for developer to create new dynamic endpoint, and debug it
 */
object PractiseEndpointGroup extends EndpointGroup{

  override protected lazy val urlPrefix: String = "test-dynamic-resource-doc"

  override protected def resourceDocs: List[APIUtil.ResourceDoc] = ResourceDoc(
    PractiseEndpoint.endpoint,
    ApiVersion.v4_0_0,
    "test-dynamic-resource-doc",
    PractiseEndpoint.requestMethod,
    PractiseEndpoint.requestUrl,
    "A test endpoint",
    s"""A test endpoint.
       |Just for debug method body of dynamic resource doc
       |""",
    StringBody("Any request body"),
    StringBody("Any response body"),
    List(
      UnknownError
    ),
    List(apiTagDynamicResourceDoc, apiTagNewStyle)) :: Nil
}
