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

package code.api.UKOpenBanking.v2_0_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/*
 * All v2.0 UK Open Banking endpoints have been migrated to Http4sUKOBv200AIS.
 * This stub is retained for ScannedApis registration (class-path scanning) and
 * so that external callers (APIUtil, SwaggerJSONFactory) that access
 * OBP_UKOpenBanking_200.apiVersion / .allResourceDocs continue to compile.
 * Routes are served by Http4sUKOBv200.wrappedRoutes in Http4sApp (ahead of the Lift bridge).
 */
object OBP_UKOpenBanking_200 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV20
  val versionStatus: String = ApiVersionStatus.DRAFT.toString

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = Http4sUKOBv200.resourceDocs
}
