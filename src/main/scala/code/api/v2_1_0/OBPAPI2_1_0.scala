/**
  * Open Bank Project - API
  * Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd
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
  *TESOBE / Music Pictures Ltd
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
package code.api.v2_1_0

import code.api.OBPRestHelper
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.APIMethods140
import code.api.v2_0_0.APIMethods200
import net.liftweb.common.Loggable
import net.liftweb.util.Props

import scala.collection.immutable.Nil

object OBPAPI2_1_0 extends OBPRestHelper with APIMethods130 with APIMethods140 with APIMethods200 with APIMethods210 with Loggable {


  val VERSION = "2.1.0"

  // Get disbled API versions from props
  val disabledVersions = Props.get("api_disabled_versions").getOrElse("").replace("[", "").replace("]", "").split(",")
  // Get disbled API endpoints from props
  val disabledEndpoints = Props.get("api_disabled_endpoints").getOrElse("").replace("[", "").replace("]", "").split(",")

  // Note: Since we pattern match on these routes, if two implementations match a given url the first will match

  var routes = List(Implementations1_2_1.root(VERSION))


  // ### VERSION 1.2.1 - BEGIN ###
  val endpointsOf1_2_1 = "getBanks" :: "bankById" :: "allAccountsAllBanks" ::
                         "updateAccountLabel" :: "getViewsForBankAccount" :: "createViewForBankAccount" ::
                         "updateViewForBankAccount" :: "deleteViewForBankAccount" :: "addPermissionForUserForBankAccountForMultipleViews" ::
                         "addPermissionForUserForBankAccountForOneView" :: "removePermissionForUserForBankAccountForOneView" :: "removePermissionForUserForBankAccountForAllViews" ::
                         "getCounterpartiesForBankAccount" :: "getCounterpartyByIdForBankAccount" :: "getCounterpartyMetadata" ::
                         "getCounterpartyPublicAlias" :: "addCounterpartyPublicAlias" :: "updateCounterpartyPublicAlias" ::
                         "deleteCounterpartyPublicAlias" :: "getCounterpartyPrivateAlias" :: "addCounterpartyPrivateAlias" ::
                         "updateCounterpartyPrivateAlias" :: "deleteCounterpartyPrivateAlias" :: "addCounterpartyMoreInfo" ::
                         "updateCounterpartyMoreInfo" :: "deleteCounterpartyMoreInfo" :: "addCounterpartyUrl" ::
                         "updateCounterpartyUrl" :: "deleteCounterpartyUrl" :: "addCounterpartyImageUrl" ::
                         "updateCounterpartyImageUrl" :: "deleteCounterpartyImageUrl" :: "addCounterpartyOpenCorporatesUrl" ::
                         "updateCounterpartyOpenCorporatesUrl" :: "deleteCounterpartyOpenCorporatesUrl" :: "addCounterpartyCorporateLocation" ::
                         "updateCounterpartyCorporateLocation" :: "deleteCounterpartyCorporateLocation" :: "addCounterpartyPhysicalLocation" ::
                         "updateCounterpartyPhysicalLocation" :: "deleteCounterpartyPhysicalLocation" :: "getTransactionsForBankAccount" ::
                         "getTransactionByIdForBankAccount" :: "getTransactionNarrative" :: "addTransactionNarrative" ::
                         "updateTransactionNarrative" :: "deleteTransactionNarrative" :: "getCommentsForViewOnTransaction" ::
                         "addCommentForViewOnTransaction" :: "deleteCommentForViewOnTransaction" :: "deleteCommentForViewOnTransaction" ::
                         "getTagsForViewOnTransaction" :: "addTagForViewOnTransaction" :: "deleteTagForViewOnTransaction" ::
                         "getImagesForViewOnTransaction" :: "addImageForViewOnTransaction" :: "deleteImageForViewOnTransaction" ::
                         "getWhereTagForViewOnTransaction" :: "addWhereTagForViewOnTransaction" :: "updateWhereTagForViewOnTransaction" ::
                         "deleteWhereTagForViewOnTransaction" :: "getCounterpartyForTransaction" :: "makePayment" ::
                          Nil

  for ( item <- Implementations1_2_1.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf1_2_1.exists(_ == item.apiFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 1.2.1 - END ###


  // ### VERSION 1.3.0 - BEGIN ###
  // New in 1.3.0
  val endpointsOf1_3_0 = "getCards" :: "getCardsForBank" :: Nil
  for ( item <- Implementations1_3_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf1_3_0.exists(_ == item.apiFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 1.3.0 - END ###



  // ### VERSION 1.4.0 - BEGIN ###
  // New in 1.4.0
  val endpointsOf1_4_0 = "getCustomer" :: "getCustomerMessages" :: "addCustomerMessage" :: "getBranches" ::
                         "getAtms" :: "getProducts" :: "getCrmEvents" :: "getTransactionRequestTypes" ::
                         Nil
  for ( item <- Implementations1_4_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf1_4_0.exists(_ == item.apiFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 1.4.0 - END ###



  // ### VERSION 2.0.0 - BEGIN ###
  // Updated in 2.0.0 (less info about the views)
  val endpointsOf2_0_0 = "allAccountsAllBanks" :: "privateAccountsAllBanks" :: "publicAccountsAllBanks" :: "allAccountsAtOneBank" ::
    "privateAccountsAtOneBank" :: "publicAccountsAtOneBank" :: "answerTransactionRequestChallenge" :: "accountById" ::
    "getPermissionsForBankAccount" :: "getPermissionForUserForBankAccount" :: "getKycDocuments" :: "getKycMedia" ::
    "getKycStatuses" :: "getKycChecks" :: "getSocialMediaHandles" :: "addKycDocument" ::
    "addKycMedia" :: "addKycStatus" :: "addKycCheck" :: "addSocialMediaHandle" ::
    "getCoreAccountById" :: "getCoreTransactionsForBankAccount" :: "createAccount" :: "getTransactionTypes" ::
    "createUser" :: "createMeeting" :: "getMeetings" :: "getMeeting" ::
    "createCustomer" :: "getCurrentUser" :: "getUser" :: "createUserCustomerLinks" ::
    "addEntitlement" :: "getEntitlements" :: "deleteEntitlement" :: "getAllEntitlements" ::
    "elasticSearchWarehouse" :: "elasticSearchMetrics" :: "getCustomers" ::
    Nil
    for ( item <- Implementations2_0_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf2_0_0.exists(_ == item.apiFunction)) {
      routes = routes:::List(item.partialFunction)
    }
  // ### VERSION 2.0.0 - END ###



  // ### VERSION 2.1.0 - BEGIN ###
  // New in 2.1.0
  val endpointsOf2_1_0 = "sandboxDataImport" :: "getTransactionRequestTypesSupportedByBank" :: "createTransactionRequest" :: "getTransactionRequests" :: Nil
  for ( item <- Implementations2_1_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf2_1_0.exists(_ == item.apiFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 2.1.0 - END ###

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

}
