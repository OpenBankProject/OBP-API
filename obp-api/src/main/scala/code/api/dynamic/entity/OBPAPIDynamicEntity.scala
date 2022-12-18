/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.api.dynamic.entity

import APIMethodsDynamicEntity.ImplementationsDynamicEntity
import code.api.OBPRestHelper
import code.api.dynamic.endpoint.helper.DynamicEndpoints
import code.api.util.APIUtil.OBPEndpoint
import code.api.util.{APIUtil, VersionedOBPApis}
import code.api.v5_0_0.OBPAPI5_0_0.{allResourceDocs, apiPrefix, registerRoutes, routes}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion,ApiVersionStatus}
import net.liftweb.common.{Box, Full}
import net.liftweb.http.{LiftResponse, PlainTextResponse}
import org.apache.http.HttpStatus

/*
This file defines which endpoints from all the versions are available in v4.0.0
 */
object OBPAPIDynamicEntity extends OBPRestHelper with MdcLoggable with VersionedOBPApis{

  val version : ApiVersion = ApiVersion.`dynamic-entity`

  val versionStatus = ApiVersionStatus.`BLEEDING-EDGE`.toString

  // if old version ResourceDoc objects have the same name endpoint with new version, omit old version ResourceDoc.
  def allResourceDocs = collectResourceDocs(ImplementationsDynamicEntity.resourceDocs)

  val routes : List[OBPEndpoint] = List(ImplementationsDynamicEntity.genericEndpoint) 

  routes.map(endpoint => oauthServe(apiPrefix{endpoint}, None))
  
  logger.info(s"version $version has been run! There are ${routes.length} routes.")
  // specified response for OPTIONS request.
  private val corsResponse: Box[LiftResponse] = Full{
    val corsHeaders = List(
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS, PUT, PATCH, DELETE",
      "Access-Control-Allow-Headers" -> "*",
      "Access-Control-Allow-Credentials" -> "true",
      "Access-Control-Max-Age" -> "1728000" //Tell client that this pre-flight info is valid for 20 days
    )
    PlainTextResponse("", corsHeaders, HttpStatus.SC_NO_CONTENT)
  }
  /*
   * process OPTIONS http request, just return no content and status is 204
   */
  this.serve({
    case req if req.requestType.method == "OPTIONS" => corsResponse
  })
}
