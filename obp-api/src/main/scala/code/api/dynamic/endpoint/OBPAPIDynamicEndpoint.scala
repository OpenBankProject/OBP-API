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
package code.api.dynamic.endpoint

import APIMethodsDynamicEndpoint.ImplementationsDynamicEndpoint
import code.api.OBPRestHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion,ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v4.0.0
 */
object OBPAPIDynamicEndpoint extends OBPRestHelper with MdcLoggable {

  val version : ApiVersion = ApiVersion.`dynamic-endpoint`

  val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString

  // if old version ResourceDoc objects have the same name endpoint with new version, omit old version ResourceDoc.
  def allResourceDocs = collectResourceDocs(ImplementationsDynamicEndpoint.resourceDocs)

  logger.info(s"version $version has been run!")
  // OPTIONS / CORS for dynamic-endpoint is now handled globally by Http4sApp.corsHandler (which
  // short-circuits all OPTIONS ahead of the version routes). The Lift OPTIONS serve below became
  // dead once dynamic-endpoint left statelessDispatch — kept commented for reference.
  // // specified response for OPTIONS request.
  // private val corsResponse: Box[LiftResponse] = Full{
  //   val corsHeaders = List(
  //     "Access-Control-Allow-Origin" -> "*",
  //     "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS, PUT, PATCH, DELETE",
  //     "Access-Control-Allow-Headers" -> "*",
  //     "Access-Control-Allow-Credentials" -> "true",
  //     "Access-Control-Max-Age" -> "1728000" //Tell client that this pre-flight info is valid for 20 days
  //   )
  //   PlainTextResponse("", corsHeaders, HttpStatus.SC_NO_CONTENT)
  // }
  // /*
  //  * process OPTIONS http request, just return no content and status is 204
  //  */
  // this.serve({
  //   case req if req.requestType.method == "OPTIONS" => corsResponse
  // })
}
