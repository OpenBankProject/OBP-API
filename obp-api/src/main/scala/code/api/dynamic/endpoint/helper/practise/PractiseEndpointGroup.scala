/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.dynamic.endpoint.helper.practise

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.requestRootJsonClass
import code.api.dynamic.endpoint.helper.EndpointGroup
import code.api.util.APIUtil
import code.api.util.APIUtil.{ResourceDoc, StringBody}
import code.api.util.ApiTag.{apiTagDynamicResourceDoc}
import code.api.util.ErrorMessages.UnknownError
import com.openbankproject.commons.util.ApiVersion

import scala.collection.immutable.List

/**
 * this is just for developer to create new dynamic endpoint, and debug it
 */
object PractiseEndpointGroup extends EndpointGroup{

  override protected lazy val urlPrefix: String = "test-dynamic-resource-doc"

  override protected def resourceDocs: List[APIUtil.ResourceDoc] = ResourceDoc(
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
    List(apiTagDynamicResourceDoc),
    dynamicHttp4sFunction = Some(PractiseEndpoint.endpoint)) :: Nil
}
