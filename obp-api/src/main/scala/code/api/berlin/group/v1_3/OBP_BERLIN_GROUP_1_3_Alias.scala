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
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, berlinGroupV13AliasPath, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersionStatus, ScannedApiVersion}
import scala.collection.mutable.ArrayBuffer

/*
This file defines which endpoints from all the versions are available in v1
 */
object OBP_BERLIN_GROUP_1_3_Alias extends OBPRestHelper with MdcLoggable with ScannedApis {
  override val apiVersion = ScannedApiVersion(berlinGroupV13AliasPath.head, berlinGroupV13AliasPath.head, berlinGroupV13AliasPath.last)
  val versionStatus = ApiVersionStatus.DRAFT.toString

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = if(berlinGroupV13AliasPath.nonEmpty){
    OBP_BERLIN_GROUP_1_3.allResourceDocs.map(resourceDoc => resourceDoc.copy(
      implementedInApiVersion = apiVersion.copy(apiStandard = resourceDoc.implementedInApiVersion.apiStandard),
    ))
  } else ArrayBuffer.empty[ResourceDoc]

  // All BG v1.3 alias routes are now served natively by Http4sBGv13Alias.wrappedRoutes.
  // Lift dispatch is retired: routes is Nil so registerRoutes is not called.
  // allResourceDocs is retained for ScannedApis version-discovery.
  override val routes: List[OBPEndpoint] = Nil
}
