/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
import code.api.util.APIUtil.{OBPEndpoint, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable

import scala.collection.immutable.Nil
import code.api.UKOpenBanking.v2_0_0.APIMethods_UKOpenBanking_200._
import com.openbankproject.commons.util.ScannedApiVersion


/*
This file defines which endpoints from all the versions are available in v1
 */


object OBP_UKOpenBanking_200 extends OBPRestHelper with MdcLoggable with ScannedApis{

  override val apiVersion = ScannedApiVersion("open-banking", "UK", "v2.0")
  val versionStatus = "DRAFT"

  val allEndpoints = 
    getAccountList :: 
    getAccountTransactions :: 
    getAccount :: 
    getAccountBalances :: 
    getBalances :: 
    Nil
  
  override val allResourceDocs = resourceDocs

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  override val routes : List[OBPEndpoint] = getAllowedEndpoints(allEndpoints,resourceDocs)


  // Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix)

  logger.info(s"version $version has been run! There are ${routes.length} routes.")

}
