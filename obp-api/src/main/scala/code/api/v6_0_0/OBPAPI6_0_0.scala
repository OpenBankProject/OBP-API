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
import code.api.util.VersionedOBPApis
import code.api.v3_0_0.Http4s300
import code.api.v3_1_0.{APIMethods310, Http4s310}
import code.api.v4_0_0.{APIMethods400, Http4s400}
import code.api.v5_0_0.APIMethods500
import code.api.v5_1_0.{APIMethods510, Http4s510, OBPAPI5_1_0}
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v6.0.0.
All v6.0.0 endpoints have been migrated to Http4s600 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI6_0_0 extends OBPRestHelper
  with APIMethods310
  with APIMethods400
  with APIMethods500
  with APIMethods510
  with MdcLoggable
  with VersionedOBPApis{

  lazy val version : ApiVersion = ApiVersion.v6_0_0

  lazy val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString

  // Re-export so tests that import OBPAPI6_0_0.Implementations6_0_0 still compile.
  val Implementations6_0_0 = Http4s600.Implementations6_0_0
  // Re-export so nameOf references below (in excludeEndpoints) continue to compile
  // after APIMethods510 was replaced with an empty stub.
  val Implementations5_1_0 = Http4s510.Implementations5_1_0
  // Re-export so nameOf references below (in excludeEndpoints) continue to compile
  // after APIMethods400 was replaced with an empty stub.
  val Implementations4_0_0 = Http4s400.Implementations4_0_0
  // Re-export so nameOf(Implementations3_1_0.xxx) in excludeEndpoints continues to compile
  // after APIMethods310 was replaced with an empty stub.
  val Implementations3_1_0 = Http4s310.Implementations3_1_0
  // Re-export so nameOf(Implementations3_0_0.xxx) in excludeEndpoints continues to compile
  // after APIMethods300 was replaced with an empty stub.
  val Implementations3_0_0 = Http4s300.Implementations3_0_0

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

  logger.info(s"version $version has been run! ${allResourceDocs.length} allResourceDocs.")
  // CORS for OPTIONS is handled by the http4s corsHandler layer — no Lift serve needed here.
}
