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
package code.api.UKOpenBanking.v3_1_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ScannedApiVersion

import scala.collection.mutable.ArrayBuffer




/*
This file defines which endpoints from all the versions are available in v1
 */
object OBP_UKOpenBanking_310 extends OBPRestHelper with MdcLoggable with ScannedApis {
  //please modify these three parameter if it is not correct.
  override val apiVersion = ScannedApiVersion("open-banking", "UK", "v3.1")
  val versionStatus = "DRAFT"

  private[this] val endpoints =
    APIMethods_AccountAccessApi.endpoints ++
    APIMethods_AccountsApi.endpoints ++
    APIMethods_BalancesApi.endpoints ++
    APIMethods_BeneficiariesApi.endpoints ++
    APIMethods_DirectDebitsApi.endpoints ++
    APIMethods_DomesticPaymentsApi.endpoints ++
    APIMethods_DomesticScheduledPaymentsApi.endpoints ++
    APIMethods_DomesticStandingOrdersApi.endpoints ++
    APIMethods_FilePaymentsApi.endpoints ++
    APIMethods_FundsConfirmationsApi.endpoints ++
    APIMethods_InternationalPaymentsApi.endpoints ++
    APIMethods_InternationalScheduledPaymentsApi.endpoints ++
    APIMethods_InternationalStandingOrdersApi.endpoints ++
    APIMethods_OffersApi.endpoints ++
    APIMethods_PartysApi.endpoints ++
    APIMethods_ProductsApi.endpoints ++
    APIMethods_ScheduledPaymentsApi.endpoints ++
    APIMethods_StandingOrdersApi.endpoints ++
    APIMethods_StatementsApi.endpoints ++
    APIMethods_TransactionsApi.endpoints

  override val allResourceDocs: ArrayBuffer[ResourceDoc] =
    APIMethods_AccountAccessApi.resourceDocs ++
    APIMethods_AccountsApi.resourceDocs ++
    APIMethods_BalancesApi.resourceDocs ++
    APIMethods_BeneficiariesApi.resourceDocs ++
    APIMethods_DirectDebitsApi.resourceDocs ++
    APIMethods_DomesticPaymentsApi.resourceDocs ++
    APIMethods_DomesticScheduledPaymentsApi.resourceDocs ++
    APIMethods_DomesticStandingOrdersApi.resourceDocs ++
    APIMethods_FilePaymentsApi.resourceDocs ++
    APIMethods_FundsConfirmationsApi.resourceDocs ++
    APIMethods_InternationalPaymentsApi.resourceDocs ++
    APIMethods_InternationalScheduledPaymentsApi.resourceDocs ++
    APIMethods_InternationalStandingOrdersApi.resourceDocs ++
    APIMethods_OffersApi.resourceDocs ++
    APIMethods_PartysApi.resourceDocs ++
    APIMethods_ProductsApi.resourceDocs ++
    APIMethods_ScheduledPaymentsApi.resourceDocs ++
    APIMethods_StandingOrdersApi.resourceDocs ++
    APIMethods_StatementsApi.resourceDocs ++
    APIMethods_TransactionsApi.resourceDocs

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  override val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, allResourceDocs)

  // Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix)

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
}
