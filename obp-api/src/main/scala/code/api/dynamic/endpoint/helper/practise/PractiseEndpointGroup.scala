package code.api.dynamic.endpoint.helper.practise

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.requestRootJsonClass
import code.api.dynamic.endpoint.helper.EndpointGroup
import code.api.util.APIUtil
import code.api.util.APIUtil.{ResourceDoc, StringBody}
import code.api.util.ApiTag.{apiTagDynamicResourceDoc, apiTagNewStyle}
import code.api.util.ErrorMessages.UnknownError
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
       |
       |Just for debug method body of dynamic resource doc.
       |better watch the following introduction video first 
       |* [Dynamic resourceDoc version1](https://vimeo.com/623381607)
       |
       |The endpoint return the response from PractiseEndpoint code.
       |Here, code.api.DynamicEndpoints.dynamic.practise.PractiseEndpoint.process
       |You can test the method body grammar, and try the business logic, but need to restart the OBP-API code .
       |
       |""",
    requestRootJsonClass,
    requestRootJsonClass,
    List(
      UnknownError
    ),
    List(apiTagDynamicResourceDoc, apiTagNewStyle)) :: Nil
}
