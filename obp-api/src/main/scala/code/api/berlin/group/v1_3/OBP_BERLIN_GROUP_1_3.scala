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
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.builder.CommonServicesApi.APIMethods_CommonServicesApi
import code.api.builder.ConfirmationOfFundsServicePIISApi.APIMethods_ConfirmationOfFundsServicePIISApi
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.builder.SigningBasketsApi.APIMethods_SigningBasketsApi
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ScannedApiVersion

import scala.collection.mutable.ArrayBuffer




/*
This file defines which endpoints from all the versions are available in v1
 */
object OBP_BERLIN_GROUP_1_3 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion = ScannedApiVersion("berlin-group", "BG", "v1.3")
  val versionStatus = "DRAFT"

  private[this] val endpoints =
    APIMethods_AccountInformationServiceAISApi.endpoints ++
    APIMethods_ConfirmationOfFundsServicePIISApi.endpoints ++
    APIMethods_PaymentInitiationServicePISApi.endpoints ++
    APIMethods_SigningBasketsApi.endpoints ++
    APIMethods_CommonServicesApi.endpoints

  override val allResourceDocs: ArrayBuffer[ResourceDoc]  =
    APIMethods_AccountInformationServiceAISApi.resourceDocs ++
    APIMethods_ConfirmationOfFundsServicePIISApi.resourceDocs ++
    APIMethods_PaymentInitiationServicePISApi.resourceDocs ++
    APIMethods_SigningBasketsApi.resourceDocs ++
    APIMethods_CommonServicesApi.resourceDocs

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  override val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, allResourceDocs)

  // Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix)

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
}
