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
package code.api.UKOpenBanking.v3_1_0

import code.api.OBPRestHelper
import code.api.builder.DomesticPaymentsApi.APIMethods_DomesticPaymentsApi
import code.api.builder.DomesticScheduledPaymentsApi.APIMethods_DomesticScheduledPaymentsApi
import code.api.builder.DomesticStandingOrdersApi.APIMethods_DomesticStandingOrdersApi
import code.api.builder.FilePaymentsApi.APIMethods_FilePaymentsApi
import code.api.builder.FundsConfirmationsApi.APIMethods_FundsConfirmationsApi
import code.api.builder.InternationalPaymentsApi.APIMethods_InternationalPaymentsApi
import code.api.builder.InternationalScheduledPaymentsApi.APIMethods_InternationalScheduledPaymentsApi
import code.api.builder.InternationalStandingOrdersApi.APIMethods_InternationalStandingOrdersApi
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ApiVersion
import code.util.Helper.MdcLoggable

import scala.collection.immutable.Nil



/*
This file defines which endpoints from all the versions are available in v1
 */


object OBP_UKOpenBanking_310 extends OBPRestHelper with MdcLoggable 
  with APIMethods_AccountAccessApi
  with APIMethods_AccountsApi
  with APIMethods_BalancesApi
  with APIMethods_BeneficiariesApi
  with APIMethods_DirectDebitsApi
  with APIMethods_OffersApi
  with APIMethods_PartysApi
  with APIMethods_ProductsApi
  with APIMethods_ScheduledPaymentsApi
  with APIMethods_StandingOrdersApi
  with APIMethods_StatementsApi
  with APIMethods_TransactionsApi
  with APIMethods_DomesticPaymentsApi
  with APIMethods_DomesticScheduledPaymentsApi
  with APIMethods_DomesticStandingOrdersApi
  with APIMethods_FilePaymentsApi
  with APIMethods_InternationalPaymentsApi
  with APIMethods_InternationalScheduledPaymentsApi
  with APIMethods_InternationalStandingOrdersApi
  with APIMethods_FundsConfirmationsApi{

  val version = ApiVersion.ukOpenBankingV310
  val versionStatus = "DRAFT"

  val endpointsOf1_3 = 
    ImplementationsAccountAccessApi.endpoints ++
    ImplementationsAccountsApi.endpoints ++
    ImplementationsBalancesApi.endpoints ++
    ImplementationsBeneficiariesApi.endpoints ++
    ImplementationsDirectDebitsApi.endpoints ++
    ImplementationsOffersApi.endpoints ++
    ImplementationsPartysApi.endpoints ++
    ImplementationsProductsApi.endpoints ++
    ImplementationsDomesticPaymentsApi.endpoints ++
    ImplementationsDomesticScheduledPaymentsApi.endpoints ++
    ImplementationsDomesticStandingOrdersApi.endpoints ++
    ImplementationsFilePaymentsApi.endpoints ++
    ImplementationsInternationalPaymentsApi.endpoints ++
    ImplementationsInternationalScheduledPaymentsApi.endpoints ++
    ImplementationsInternationalStandingOrdersApi.endpoints ++
    ImplementationsFundsConfirmationsApi.endpoints 
  
  val allResourceDocs =
    ImplementationsAccountAccessApi.resourceDocs ++
    ImplementationsAccountsApi.resourceDocs ++
    ImplementationsBalancesApi.resourceDocs ++
    ImplementationsBeneficiariesApi.resourceDocs ++
    ImplementationsDirectDebitsApi.resourceDocs ++
    ImplementationsOffersApi.resourceDocs ++
    ImplementationsPartysApi.resourceDocs ++
    ImplementationsProductsApi.resourceDocs ++
    ImplementationsScheduledPaymentsApi.resourceDocs ++ 
    ImplementationsDomesticPaymentsApi.resourceDocs ++           
    ImplementationsDomesticScheduledPaymentsApi.resourceDocs ++  
    ImplementationsDomesticStandingOrdersApi.resourceDocs ++     
    ImplementationsFilePaymentsApi.resourceDocs ++               
    ImplementationsInternationalPaymentsApi.resourceDocs ++      
    ImplementationsInternationalScheduledPaymentsApi.resourceDocs ++
    ImplementationsInternationalStandingOrdersApi.resourceDocs ++ 
    ImplementationsFundsConfirmationsApi.resourceDocs   
  
  def findResourceDoc(pf: OBPEndpoint): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction==pf)
  }

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] = getAllowedEndpoints(endpointsOf1_3, allResourceDocs)

  // Make them available for use!
  routes.foreach(route => {
    oauthServe(("open-banking" / version.vDottedApiVersion()).oPrefix{route}, findResourceDoc(route))
  })

  logger.info(s"version $version has been run! There are ${routes.length} routes.")

}
