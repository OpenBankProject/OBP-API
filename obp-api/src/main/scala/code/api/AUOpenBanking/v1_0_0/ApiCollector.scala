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
package code.api.AUOpenBanking.v1_0_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ScannedApiVersion

import scala.collection.mutable.ArrayBuffer




/*
This file defines which endpoints from all the versions are available in v1
 */
object ApiCollector extends OBPRestHelper with MdcLoggable with ScannedApis {
  //please modify these three parameter if it is not correct.
  override val apiVersion = ScannedApiVersion("cds-au", "AU", "v1.0.0")
  val versionStatus = "DRAFT"

  private[this] val endpoints =
    APIMethods_AccountsApi.endpoints ++
    APIMethods_BankingApi.endpoints ++
    APIMethods_CommonApi.endpoints ++
    APIMethods_CustomerApi.endpoints ++
    APIMethods_DirectDebitsApi.endpoints ++
    APIMethods_DiscoveryApi.endpoints ++
    APIMethods_PayeesApi.endpoints ++
    APIMethods_ProductsApi.endpoints ++
    APIMethods_ScheduledPaymentsApi.endpoints 

  override val allResourceDocs: ArrayBuffer[ResourceDoc]  =
    APIMethods_AccountsApi.resourceDocs ++
    APIMethods_BankingApi.resourceDocs ++
    APIMethods_CommonApi.resourceDocs ++
    APIMethods_CustomerApi.resourceDocs ++
    APIMethods_DirectDebitsApi.resourceDocs ++
    APIMethods_DiscoveryApi.resourceDocs ++
    APIMethods_PayeesApi.resourceDocs ++
    APIMethods_ProductsApi.resourceDocs ++
    APIMethods_ScheduledPaymentsApi.resourceDocs

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  override val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, allResourceDocs)

  // Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix)

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
}
