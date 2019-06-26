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
package code.api.berlin.group.v1_3_1

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.{ScannedApiVersion, ScannedApis}
import code.util.Helper.MdcLoggable

//import code.api.berlin.group.v1_3_1.APIMethods_AccountInformationServiceAISApi
//import code.api.berlin.group.v1_3_1.APIMethods_CommonServicesApi
import code.api.berlin.group.v1_3_1.APIMethods_ConfirmationOfFundsServicePIISApi
//import code.api.berlin.group.v1_3_1.APIMethods_PaymentInitiationServicePISApi
//import code.api.berlin.group.v1_3_1.APIMethods_SigningBasketsSBSApi

import scala.collection.mutable.ArrayBuffer




/*
This file defines which endpoints from all the versions are available in v1
 */
object JSONFactory_BERLIN_GROUP_1_3_3 extends OBPRestHelper with MdcLoggable with ScannedApis {
  //please modify these three parameter if it is not correct.
  override val apiVersion = ScannedApiVersion("berlin-group", "BG", "v1.3.1")
  val versionStatus = "DRAFT"

  private[this] val endpoints =
    APIMethods_AccountInformationServiceAISApi.endpoints ++
    APIMethods_CommonServicesApi.endpoints ++
    APIMethods_ConfirmationOfFundsServicePIISApi.endpoints ++
    APIMethods_PaymentInitiationServicePISApi.endpoints ++
    APIMethods_SigningBasketsSBSApi.endpoints 

  override val allResourceDocs: ArrayBuffer[ResourceDoc]  =
    APIMethods_AccountInformationServiceAISApi.resourceDocs ++
    APIMethods_CommonServicesApi.resourceDocs ++
    APIMethods_ConfirmationOfFundsServicePIISApi.resourceDocs ++
    APIMethods_PaymentInitiationServicePISApi.resourceDocs ++
    APIMethods_SigningBasketsSBSApi.resourceDocs 

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
