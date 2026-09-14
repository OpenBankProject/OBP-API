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

package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

// All request dispatch migrated to Http4sResourceDocs (wired into Http4sApp.baseServices).
// These objects are retained solely as accessors for ImplementationsResourceDocs —
// the business-logic entry point delegated to by the centralised http4s service.
// They are NOT registered in LiftRules.statelessDispatch.

object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version      = ApiVersion.v1_4_0
  val versionStatus = ApiVersionStatus.STABLE.toString
  // routes intentionally empty — all traffic served by Http4sResourceDocs
}

// Kept so Http4sResourceDocs can reference ResourceDocs300.ResourceDocs600.
object ResourceDocs300 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version      : ApiVersion = ApiVersion.v3_0_0
  val versionStatus              = ApiVersionStatus.STABLE.toString
  // routes intentionally empty — all traffic served by Http4sResourceDocs

  // Retained to provide ImplementationsResourceDocs with includeTechnologyInResponse=true.
  // v6.0.0 resource-docs responses include the `technology` field; all other versions
  // leave it as None.  Http4sResourceDocs picks this instance for v6.0.0 URLs.
  object ResourceDocs600 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version      : ApiVersion = ApiVersion.v6_0_0
    val versionStatus              = ApiVersionStatus.BLEEDING_EDGE.toString
    override def includeTechnologyInResponse: Boolean = true
    // routes intentionally empty — all traffic served by Http4sResourceDocs
  }
}
