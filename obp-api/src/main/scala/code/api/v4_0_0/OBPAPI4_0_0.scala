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

import scala.language.reflectiveCalls
import code.api.OBPRestHelper
import code.api.util.APIUtil.OBPEndpoint
import code.api.util.VersionedOBPApis
import code.api.v3_1_0.OBPAPI3_1_0
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v4.0.0.
All v4.0.0 endpoints have been migrated to Http4s400 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI4_0_0 extends OBPRestHelper with MdcLoggable with VersionedOBPApis {

  val version: ApiVersion = ApiVersion.v4_0_0
  lazy val versionStatus  = ApiVersionStatus.STABLE.toString

  // Re-export so any caller that still imports OBPAPI4_0_0.Implementations4_0_0 keeps compiling.
  val Implementations4_0_0 = Http4s400.Implementations4_0_0

  // nameOf macro replaced with literal strings since the APIMethods121/310 trait mixins
  // have been removed — `nameOf(Implementations1_2_1.foo)` would no longer resolve.
  lazy val excludeEndpoints: List[String] = List(
    "addPermissionForUserForBankAccountForMultipleViews",
    "removePermissionForUserForBankAccountForAllViews",
    "addPermissionForUserForBankAccountForOneView",
    "removePermissionForUserForBankAccountForOneView",
    "createAccount",
    "revokeConsent"
  )

  def allResourceDocs = collectResourceDocs(
    OBPAPI3_1_0.allResourceDocs,
    Http4s400.resourceDocs
  ).filterNot(it => it.partialFunctionName.matches(excludeEndpoints.mkString("|")))

  val routes: List[OBPEndpoint] = Nil

  registerRoutes(routes, allResourceDocs, apiPrefix, true)

  logger.info(s"version $version has been run! There are ${routes.length} routes, ${allResourceDocs.length} allResourceDocs.")
  // CORS for OPTIONS is handled by the http4s corsHandler layer — no Lift serve needed here.
}
