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
package code.api.v5_1_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, getAllowedEndpoints}
import code.api.util.{APIUtil, VersionedOBPApis}
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.APIMethods140
import code.api.v2_0_0.APIMethods200
import code.api.v2_1_0.APIMethods210
import code.api.v2_2_0.APIMethods220
import code.api.v3_0_0.APIMethods300
import code.api.v3_0_0.custom.CustomAPIMethods300
import code.api.v3_1_0.APIMethods310
import code.api.v4_0_0.APIMethods400
import code.api.v5_0_0.{APIMethods500, OBPAPI5_0_0}
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import net.liftweb.common.{Box, Full}
import net.liftweb.http.{LiftResponse, PlainTextResponse}
import org.apache.http.HttpStatus

/*
This file defines which endpoints from all the versions are available in v5.0.0
 */
object OBPAPI5_1_0 extends OBPRestHelper 
  with APIMethods130 
  with APIMethods140 
  with APIMethods200 
  with APIMethods210 
  with APIMethods220 
  with APIMethods300 
  with CustomAPIMethods300 
  with APIMethods310 
  with APIMethods400 
  with APIMethods500 
  with APIMethods510 
  with MdcLoggable 
  with VersionedOBPApis{

  val version : ApiVersion = ApiVersion.v5_1_0

  val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString

  // Possible Endpoints from 5.1.0, exclude one endpoint use - method,exclude multiple endpoints use -- method,
  // e.g getEndpoints(Implementations5_0_0) -- List(Implementations5_0_0.genericEndpoint, Implementations5_0_0.root)
  lazy val endpointsOf5_1_0 = getEndpoints(Implementations5_1_0)

  lazy val excludeEndpoints = 
    nameOf(Implementations3_0_0.getUserByUsername) ::  // following 4 endpoints miss Provider parameter in the URL, we introduce new ones in V510.
      nameOf(Implementations3_1_0.getBadLoginStatus) ::
      nameOf(Implementations3_1_0.unlockUser) ::
      nameOf(Implementations4_0_0.lockUser) ::
      nameOf(Implementations4_0_0.createUserWithAccountAccess) ::  // following 3 endpoints miss ViewId parameter in the URL, we introduce new ones in V510.
      nameOf(Implementations4_0_0.grantUserAccessToView) ::
      nameOf(Implementations4_0_0.revokeUserAccessToView) ::
      nameOf(Implementations4_0_0.revokeGrantUserAccessToViews) ::// this endpoint is forbidden in V510, we do not support multi views in one endpoint from V510.
      Nil
      
  // if old version ResourceDoc objects have the same name endpoint with new version, omit old version ResourceDoc.
  def allResourceDocs = collectResourceDocs(
    OBPAPI5_0_0.allResourceDocs,
    Implementations5_1_0.resourceDocs
  ).filterNot(it => it.partialFunctionName.matches(excludeEndpoints.mkString("|")))

  // all endpoints
  private val endpoints: List[OBPEndpoint] = OBPAPI5_0_0.routes ++ endpointsOf5_1_0

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] = Implementations5_1_0.root :: // For now we make this mandatory 
    getAllowedEndpoints(endpoints, allResourceDocs)

  // register v5.1.0 apis first, Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix, true)


  logger.info(s"version $version has been run! There are ${routes.length} routes, ${allResourceDocs.length} allResourceDocs.")

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
