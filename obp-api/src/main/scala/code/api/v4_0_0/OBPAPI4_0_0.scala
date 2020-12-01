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
package code.api.v4_0_0

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
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.api.v3_1_0.{APIMethods310, OBPAPI3_1_0}
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Full}
import net.liftweb.http.{LiftResponse, PlainTextResponse}
import org.apache.http.HttpStatus

/*
This file defines which endpoints from all the versions are available in v4.0.0
 */
object OBPAPI4_0_0 extends OBPRestHelper with APIMethods130 with APIMethods140 with APIMethods200 with APIMethods210 with APIMethods220 with APIMethods300 with CustomAPIMethods300 with APIMethods310 with APIMethods400 with MdcLoggable with VersionedOBPApis{

  val version : ApiVersion = ApiVersion.v4_0_0

  val versionStatus = "BLEEDING-EDGE" // TODO this should be a property of ApiVersion.

  // Possible Endpoints from 4.0.0, exclude one endpoint use - method,exclude multiple endpoints use -- method,
  // e.g getEndpoints(Implementations4_0_0) -- List(Implementations4_0_0.genericEndpoint, Implementations4_0_0.root)
  val endpointsOf4_0_0 = getEndpoints(Implementations4_0_0) - Implementations4_0_0.genericEndpoint - Implementations4_0_0.dynamicEndpoint
  
  lazy val excludeEndpoints =
    nameOf(Implementations1_2_1.addPermissionForUserForBankAccountForMultipleViews) ::
      nameOf(Implementations1_2_1.removePermissionForUserForBankAccountForAllViews) ::
      nameOf(Implementations1_2_1.addPermissionForUserForBankAccountForOneView) ::
      nameOf(Implementations1_2_1.removePermissionForUserForBankAccountForOneView) ::
      nameOf(Implementations3_1_0.createAccount) ::
      Nil

  // if old version ResourceDoc objects have the same name endpoint with new version, omit old version ResourceDoc.
  def allResourceDocs = collectResourceDocs(OBPAPI3_1_0.allResourceDocs,
                                            Implementations4_0_0.resourceDocs,
                                            DynamicEntityHelper.doc, DynamicEndpointHelper.doc)
     .filterNot(it => it.partialFunctionName.matches(excludeEndpoints.mkString("|")))
    //TODO exclude two endpoints, after training we need add logic to exclude endpoints

  // all endpoints
  private val endpoints: List[OBPEndpoint] = OBPAPI3_1_0.routes ++ endpointsOf4_0_0

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] =
      APIUtil.dynamicEndpointStub ::  // corresponding all dynamic generated endpoints's OBPEndpoint
      Implementations4_0_0.root :: // For now we make this mandatory
      getAllowedEndpoints(endpoints, allResourceDocs)

  // register v4.0.0 apis first, Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix, true)

  oauthServe(apiPrefix{Implementations4_0_0.genericEndpoint}, None)
  oauthServe(apiPrefix{Implementations4_0_0.dynamicEndpoint}, None)

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
