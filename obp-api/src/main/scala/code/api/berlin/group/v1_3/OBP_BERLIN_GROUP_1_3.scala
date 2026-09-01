/**
  * Open Bank Project - API
  * Copyright (C) 2011-2019, TESOBE GmbH
  **
  *This program is free software: you can redistribute it and/or modify
  *it under the terms of the GNU Affero General Public License as published by
  *the Free Software Foundation, either version 3 of the License, or
  *(at your option) any later version.
  **
  *This program is distributed in the hope that it will be useful,
  *but WITHOUT ANY WARRANTY; without even the implied warranty of
  *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *GNU Affero General Public License for more details.
  **
  *You should have received a copy of the GNU Affero General Public License
  *along with this program.  If not, see <http://www.gnu.org/licenses/>.
  **
  *Email: contact@tesobe.com
  *TESOBE GmbH
  *Osloerstrasse 16/17
  *Berlin 13359, Germany
  **
  *This product includes software developed at
  *TESOBE (http://www.tesobe.com/)
  * by
  *Simon Redfern : simon AT tesobe DOT com
  *Stefan Bethge : stefan AT tesobe DOT com
  *Everett Sochowski : everett AT tesobe DOT com
  *Ayoub Benali: ayoub AT tesobe DOT com
  *
  */
package code.api.berlin.group.v1_3

import code.api.OBPRestHelper
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersionStatus, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/*
 * All BG v1.3 endpoints have been migrated to their respective
 * Http4sBGv13* objects (AIS/PIS/PIIS/SigningBaskets — 55 endpoints).
 *
 * This aggregator is retained for ScannedApis registration (class-path scanning)
 * and so that external callers that access OBP_BERLIN_GROUP_1_3.apiVersion /
 * .allResourceDocs continue to compile.
 * Routes are served by Http4sBGv13.wrappedRoutes in Http4sApp (ahead of the
 * Lift bridge).
 */
object OBP_BERLIN_GROUP_1_3 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion: ScannedApiVersion = ConstantsBG.berlinGroupVersion1
  val versionStatus: String = ApiVersionStatus.DRAFT.toString

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = Http4sBGv13.resourceDocs
}
