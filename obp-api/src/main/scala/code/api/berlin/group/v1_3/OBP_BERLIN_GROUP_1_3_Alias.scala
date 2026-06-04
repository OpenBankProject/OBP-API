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
import code.api.util.APIUtil.{ResourceDoc, berlinGroupV13AliasPath}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersionStatus, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/*
 * All BG v1.3 alias endpoints are now served natively by Http4sBGv13Alias.wrappedRoutes.
 *
 * This aggregator is retained for ScannedApis registration (class-path scanning).
 * Routes are served by Http4sBGv13Alias.wrappedRoutes in Http4sApp.
 */
object OBP_BERLIN_GROUP_1_3_Alias extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion: ScannedApiVersion =
    ScannedApiVersion(berlinGroupV13AliasPath.head, berlinGroupV13AliasPath.head, berlinGroupV13AliasPath.last)

  val versionStatus: String = ApiVersionStatus.DRAFT.toString

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = Http4sBGv13Alias.resourceDocs
}

// ─── Original Lift aggregator (commented out) ────────────────────────────────
//  override val allResourceDocs: ArrayBuffer[ResourceDoc] = if(berlinGroupV13AliasPath.nonEmpty){
//    OBP_BERLIN_GROUP_1_3.allResourceDocs.map(resourceDoc => resourceDoc.copy(
//      implementedInApiVersion = apiVersion.copy(apiStandard = resourceDoc.implementedInApiVersion.apiStandard),
//    ))
//  } else ArrayBuffer.empty[ResourceDoc]
//
//  override val routes: List[OBPEndpoint] = if(berlinGroupV13AliasPath.nonEmpty){
//    getAllowedEndpoints(OBP_BERLIN_GROUP_1_3.endpoints, allResourceDocs)
//  } else List.empty[OBPEndpoint]
//
//  if(berlinGroupV13AliasPath.nonEmpty){
//    registerRoutes(routes, allResourceDocs, apiPrefix)
//    logger.info(s"version $apiVersion has been run! There are ${routes.length} routes.")
//  }
