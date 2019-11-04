/**
  * Open Bank Project - API
  * Copyright (C) 2011-2018, TESOBE Ltd
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
  *TESOBE Ltd
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
package code.api.AUSOpenBanking.v1

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.{ScannedApiVersion, ScannedApis}
import code.util.Helper.MdcLoggable

import scala.collection.mutable.ArrayBuffer


/*
This file defines which endpoints from all the versions are available in v1
 */
object OBP_AU_OpenBanking_1_0_0 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion = ScannedApiVersion("cds-au", "AU", "v1.0.0")
  val versionStatus = "DRAFT"

  private[this] val endpoints =
    APIMethods_BankingApi.endpoints 

  override val allResourceDocs: ArrayBuffer[ResourceDoc]  =
    APIMethods_BankingApi.resourceDocs
  
  private[this] def findResourceDoc(pf: OBPEndpoint): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction==pf)
  }

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  override val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, allResourceDocs)

  // Make them available for use!
  routes.foreach(route => {
    oauthServe((apiVersion.urlPrefix / version.vDottedApiVersion()).oPrefix{route}, findResourceDoc(route))
  })

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
}
