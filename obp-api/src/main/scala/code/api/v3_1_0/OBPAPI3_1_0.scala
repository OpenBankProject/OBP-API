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
package code.api.v3_1_0

import scala.language.reflectiveCalls
import code.api.OBPRestHelper
import code.api.util.VersionedOBPApis
import code.api.v1_2_1.Http4s121
import code.api.v2_2_0.Http4s220
import code.api.v3_0_0.OBPAPI3_0_0
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v3.1.0.
All v3.1.0 endpoints have been migrated to Http4s310 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI3_1_0 extends OBPRestHelper with MdcLoggable with VersionedOBPApis {

  lazy val version: ApiVersion = ApiVersion.v3_1_0
  lazy val versionStatus  = ApiVersionStatus.STABLE.toString

  // Re-exports so callers that still import OBPAPI3_1_0.ImplementationsX keep compiling.
  val Implementations3_1_0 = Http4s310.Implementations3_1_0
  val Implementations1_2_1 = Http4s121.Implementations1_2_1
  val Implementations2_2_0 = Http4s220.Implementations2_2_0

  def allResourceDocs = collectResourceDocs(
    OBPAPI3_0_0.allResourceDocs,
    Http4s310.resourceDocs
  )

  logger.info(s"version $version has been run! ${allResourceDocs.length} allResourceDocs.")
  // CORS for OPTIONS is handled by the http4s corsHandler layer — no Lift serve needed here.
}
