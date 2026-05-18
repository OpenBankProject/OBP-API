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
package code.api.v6_0_0


import scala.language.reflectiveCalls
import code.api.OBPRestHelper
import code.api.util.APIUtil.OBPEndpoint
import code.api.util.VersionedOBPApis
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.APIMethods140
import code.api.v2_0_0.APIMethods200
import code.api.v2_1_0.APIMethods210
import code.api.v2_2_0.APIMethods220
import code.api.v3_0_0.APIMethods300
import code.api.v3_0_0.custom.CustomAPIMethods300
import code.api.v3_1_0.APIMethods310
import code.api.v4_0_0.APIMethods400
import code.api.v5_0_0.APIMethods500
import code.api.v5_1_0.{APIMethods510, OBPAPI5_1_0}
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v6.0.0.
All v6.0.0 endpoints have been migrated to Http4s600 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI6_0_0 extends OBPRestHelper
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

  val version : ApiVersion = ApiVersion.v6_0_0

  val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString

  // Re-export so tests that import OBPAPI6_0_0.Implementations6_0_0 still compile.
  val Implementations6_0_0 = Http4s600.Implementations6_0_0

  lazy val excludeEndpoints =
    nameOf(Implementations3_0_0.getUserByUsername) ::
      nameOf(Implementations3_1_0.getBadLoginStatus) ::
      nameOf(Implementations3_1_0.unlockUser) ::
      nameOf(Implementations4_0_0.lockUser) ::
      nameOf(Implementations4_0_0.createUserWithAccountAccess) ::
      nameOf(Implementations4_0_0.grantUserAccessToView) ::
      nameOf(Implementations4_0_0.revokeUserAccessToView) ::
      nameOf(Implementations4_0_0.revokeGrantUserAccessToViews) ::
      nameOf(Implementations4_0_0.getMyPersonalUserAttributes) ::
      nameOf(Implementations4_0_0.createMyPersonalUserAttribute) ::
      nameOf(Implementations4_0_0.updateMyPersonalUserAttribute) ::
      nameOf(Implementations5_1_0.createNonPersonalUserAttribute) ::
      nameOf(Implementations5_1_0.getNonPersonalUserAttributes) ::
      nameOf(Implementations5_1_0.deleteNonPersonalUserAttribute) ::
      Nil

  // All v6.0.0 endpoints live in Http4s600 — aggregate Http4s600.resourceDocs on top of v5.1.0.
  def allResourceDocs = collectResourceDocs(
    OBPAPI5_1_0.allResourceDocs,
    Http4s600.resourceDocs
  ).filterNot(it => it.partialFunctionName.matches(excludeEndpoints.mkString("|")))

  // No Lift routes — all v6.0.0 endpoints are served by Http4s600.
  val routes: List[OBPEndpoint] = Nil

  registerRoutes(routes, allResourceDocs, apiPrefix, true)

  logger.info(s"version $version has been run! There are ${routes.length} routes, ${allResourceDocs.length} allResourceDocs.")
  // CORS for OPTIONS is handled by the http4s corsHandler layer — no Lift serve needed here.
}
