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
import code.model.User
import net.liftweb.common.{Box, Loggable}
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.util.Props
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.util.{Helpers, Schedule, _}

import scala.collection.immutable.Nil
import scala.collection.mutable

object OBPAPI2_1_0 extends OBPRestHelper with APIMethods130 with APIMethods140 with APIMethods200 with APIMethods210 with Loggable {


  val VERSION = "2.1.0"

  // Get disbled API versions from props
  val disabledVersions = Props.get("api_disabled_versions").getOrElse("").replace("[", "").replace("]", "").split(",")
  // Get disbled API endpoints from props
  val disabledEndpoints = Props.get("api_disabled_endpoints").getOrElse("").replace("[", "").replace("]", "").split(",")

  // Note: Since we pattern match on these routes, if two implementations match a given url the first will match

  var routes = List(Implementations1_2_1.root(VERSION))

  // ### VERSION 1.2.1 - BEGIN ###
  if (!disabledVersions.contains("v1_2_1")){
    if (!disabledEndpoints.contains("getBanks")) routes = routes:::List(Implementations1_2_1.getBanks)
    if (!disabledEndpoints.contains("bankById")) routes = routes:::List(Implementations1_2_1.bankById)
    // Now in 2_0_0
    //  if (!disabledEndpoints.contains("allAccountsAllBanks")) routes = routes:::List(Implementations1_2_1.allAccountsAllBanks)
    //  if (!disabledEndpoints.contains("privateAccountsAllBanks")) routes = routes:::List(Implementations1_2_1.privateAccountsAllBanks)
    //  if (!disabledEndpoints.contains("publicAccountsAllBanks")) routes = routes:::List(Implementations1_2_1.publicAccountsAllBanks)
    //  if (!disabledEndpoints.contains("allAccountsAtOneBank")) routes = routes:::List(Implementations1_2_1.allAccountsAtOneBank)
    //  if (!disabledEndpoints.contains("privateAccountsAtOneBank")) routes = routes:::List(Implementations1_2_1.privateAccountsAtOneBank)
    //  if (!disabledEndpoints.contains("publicAccountsAtOneBank")) routes = routes:::List(Implementations1_2_1.publicAccountsAtOneBank)
    //  if (!disabledEndpoints.contains("accountById")) routes = routes:::List(Implementations1_2_1.accountById)
    if (!disabledEndpoints.contains("updateAccountLabel")) routes = routes:::List(Implementations1_2_1.updateAccountLabel)
    if (!disabledEndpoints.contains("getViewsForBankAccount")) routes = routes:::List(Implementations1_2_1.getViewsForBankAccount)
    if (!disabledEndpoints.contains("createViewForBankAccount")) routes = routes:::List(Implementations1_2_1.createViewForBankAccount)
    if (!disabledEndpoints.contains("updateViewForBankAccount")) routes = routes:::List(Implementations1_2_1.updateViewForBankAccount)
    if (!disabledEndpoints.contains("deleteViewForBankAccount")) routes = routes:::List(Implementations1_2_1.deleteViewForBankAccount)
    //    if (!disabledEndpoints.contains("getPermissionsForBankAccount")) routes = routes:::List(Implementations1_2_1.getPermissionsForBankAccount)
    //    if (!disabledEndpoints.contains("getPermissionForUserForBankAccount")) routes = routes:::List(Implementations1_2_1.getPermissionForUserForBankAccount)
    if (!disabledEndpoints.contains("addPermissionForUserForBankAccountForMultipleViews")) routes = routes:::List(Implementations1_2_1.addPermissionForUserForBankAccountForMultipleViews)
    if (!disabledEndpoints.contains("addPermissionForUserForBankAccountForOneView")) routes = routes:::List(Implementations1_2_1.addPermissionForUserForBankAccountForOneView)
    if (!disabledEndpoints.contains("removePermissionForUserForBankAccountForOneView")) routes = routes:::List(Implementations1_2_1.removePermissionForUserForBankAccountForOneView)
    if (!disabledEndpoints.contains("removePermissionForUserForBankAccountForAllViews")) routes = routes:::List(Implementations1_2_1.removePermissionForUserForBankAccountForAllViews)
    if (!disabledEndpoints.contains("getCounterpartiesForBankAccount")) routes = routes:::List(Implementations1_2_1.getCounterpartiesForBankAccount)
    if (!disabledEndpoints.contains("getCounterpartyByIdForBankAccount")) routes = routes:::List(Implementations1_2_1.getCounterpartyByIdForBankAccount)
    if (!disabledEndpoints.contains("getCounterpartyMetadata")) routes = routes:::List(Implementations1_2_1.getCounterpartyMetadata)
    if (!disabledEndpoints.contains("getCounterpartyPublicAlias")) routes = routes:::List(Implementations1_2_1.getCounterpartyPublicAlias)
    if (!disabledEndpoints.contains("addCounterpartyPublicAlias")) routes = routes:::List(Implementations1_2_1.addCounterpartyPublicAlias)
    if (!disabledEndpoints.contains("updateCounterpartyPublicAlias")) routes = routes:::List(Implementations1_2_1.updateCounterpartyPublicAlias)
    if (!disabledEndpoints.contains("deleteCounterpartyPublicAlias")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyPublicAlias)
    if (!disabledEndpoints.contains("getCounterpartyPrivateAlias")) routes = routes:::List(Implementations1_2_1.getCounterpartyPrivateAlias)
    if (!disabledEndpoints.contains("addCounterpartyPrivateAlias")) routes = routes:::List(Implementations1_2_1.addCounterpartyPrivateAlias)
    if (!disabledEndpoints.contains("updateCounterpartyPrivateAlias")) routes = routes:::List(Implementations1_2_1.updateCounterpartyPrivateAlias)
    if (!disabledEndpoints.contains("deleteCounterpartyPrivateAlias")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyPrivateAlias)
    if (!disabledEndpoints.contains("addCounterpartyMoreInfo")) routes = routes:::List(Implementations1_2_1.addCounterpartyMoreInfo)
    if (!disabledEndpoints.contains("updateCounterpartyMoreInfo")) routes = routes:::List(Implementations1_2_1.updateCounterpartyMoreInfo)
    if (!disabledEndpoints.contains("deleteCounterpartyMoreInfo")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyMoreInfo)
    if (!disabledEndpoints.contains("addCounterpartyUrl")) routes = routes:::List(Implementations1_2_1.addCounterpartyUrl)
    if (!disabledEndpoints.contains("updateCounterpartyUrl")) routes = routes:::List(Implementations1_2_1.updateCounterpartyUrl)
    if (!disabledEndpoints.contains("deleteCounterpartyUrl")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyUrl)
    if (!disabledEndpoints.contains("addCounterpartyImageUrl")) routes = routes:::List(Implementations1_2_1.addCounterpartyImageUrl)
    if (!disabledEndpoints.contains("updateCounterpartyImageUrl")) routes = routes:::List(Implementations1_2_1.updateCounterpartyImageUrl)
    if (!disabledEndpoints.contains("deleteCounterpartyImageUrl")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyImageUrl)
    if (!disabledEndpoints.contains("addCounterpartyOpenCorporatesUrl")) routes = routes:::List(Implementations1_2_1.addCounterpartyOpenCorporatesUrl)
    if (!disabledEndpoints.contains("updateCounterpartyOpenCorporatesUrl")) routes = routes:::List(Implementations1_2_1.updateCounterpartyOpenCorporatesUrl)
    if (!disabledEndpoints.contains("deleteCounterpartyOpenCorporatesUrl")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyOpenCorporatesUrl)
    if (!disabledEndpoints.contains("addCounterpartyCorporateLocation")) routes = routes:::List(Implementations1_2_1.addCounterpartyCorporateLocation)
    if (!disabledEndpoints.contains("updateCounterpartyCorporateLocation")) routes = routes:::List(Implementations1_2_1.updateCounterpartyCorporateLocation)
    if (!disabledEndpoints.contains("deleteCounterpartyCorporateLocation")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyCorporateLocation)
    if (!disabledEndpoints.contains("addCounterpartyPhysicalLocation")) routes = routes:::List(Implementations1_2_1.addCounterpartyPhysicalLocation)
    if (!disabledEndpoints.contains("updateCounterpartyPhysicalLocation")) routes = routes:::List(Implementations1_2_1.updateCounterpartyPhysicalLocation)
    if (!disabledEndpoints.contains("deleteCounterpartyPhysicalLocation")) routes = routes:::List(Implementations1_2_1.deleteCounterpartyPhysicalLocation)
    if (!disabledEndpoints.contains("getTransactionsForBankAccount")) routes = routes:::List(Implementations1_2_1.getTransactionsForBankAccount)
    if (!disabledEndpoints.contains("getTransactionByIdForBankAccount")) routes = routes:::List(Implementations1_2_1.getTransactionByIdForBankAccount)
    if (!disabledEndpoints.contains("getTransactionNarrative")) routes = routes:::List(Implementations1_2_1.getTransactionNarrative)
    if (!disabledEndpoints.contains("addTransactionNarrative")) routes = routes:::List(Implementations1_2_1.addTransactionNarrative)
    if (!disabledEndpoints.contains("updateTransactionNarrative")) routes = routes:::List(Implementations1_2_1.updateTransactionNarrative)
    if (!disabledEndpoints.contains("deleteTransactionNarrative")) routes = routes:::List(Implementations1_2_1.deleteTransactionNarrative)
    if (!disabledEndpoints.contains("getCommentsForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.getCommentsForViewOnTransaction)
    if (!disabledEndpoints.contains("addCommentForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.addCommentForViewOnTransaction)
    if (!disabledEndpoints.contains("deleteCommentForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.deleteCommentForViewOnTransaction)
    if (!disabledEndpoints.contains("getTagsForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.getTagsForViewOnTransaction)
    if (!disabledEndpoints.contains("addTagForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.addTagForViewOnTransaction)
    if (!disabledEndpoints.contains("deleteTagForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.deleteTagForViewOnTransaction)
    if (!disabledEndpoints.contains("getImagesForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.getImagesForViewOnTransaction)
    if (!disabledEndpoints.contains("addImageForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.addImageForViewOnTransaction)
    if (!disabledEndpoints.contains("deleteImageForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.deleteImageForViewOnTransaction)
    if (!disabledEndpoints.contains("getWhereTagForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.getWhereTagForViewOnTransaction)
    if (!disabledEndpoints.contains("addWhereTagForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.addWhereTagForViewOnTransaction)
    if (!disabledEndpoints.contains("updateWhereTagForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.updateWhereTagForViewOnTransaction)
    if (!disabledEndpoints.contains("deleteWhereTagForViewOnTransaction")) routes = routes:::List(Implementations1_2_1.deleteWhereTagForViewOnTransaction)
    if (!disabledEndpoints.contains("getCounterpartyForTransaction")) routes = routes:::List(Implementations1_2_1.getCounterpartyForTransaction)
    if (!disabledEndpoints.contains("makePayment")) routes = routes:::List(Implementations1_2_1.makePayment)
  }
  // ### VERSION 1.2.1 - END ###



  // ### VERSION 1.3.0 - BEGIN ###
  // New in 1.3.0
  if (!disabledVersions.contains("v1_3_0")){
    if (!disabledEndpoints.contains("getCards")) routes = routes:::List(Implementations1_3_0.getCards)
    if (!disabledEndpoints.contains("getCardsForBank")) routes = routes:::List(Implementations1_3_0.getCardsForBank)
  }
  // ### VERSION 1.3.0 - END ###



  // ### VERSION 1.4.0 - BEGIN ###
  // New in 1.4.0
  if (!disabledVersions.contains("v1_4_0")){
    if (!disabledEndpoints.contains("getCustomer")) routes = routes:::List(Implementations1_4_0.getCustomer)
    //  Now in 2.0.0 if (!disabledEndpoints.contains("addCustomer")) routes = routes:::List(Implementations1_4_0.addCustomer)
    if (!disabledEndpoints.contains("getCustomerMessages")) routes = routes:::List(Implementations1_4_0.getCustomerMessages)
    if (!disabledEndpoints.contains("addCustomerMessage")) routes = routes:::List(Implementations1_4_0.addCustomerMessage)
    if (!disabledEndpoints.contains("getBranches")) routes = routes:::List(Implementations1_4_0.getBranches)
    if (!disabledEndpoints.contains("getAtms")) routes = routes:::List(Implementations1_4_0.getAtms)
    if (!disabledEndpoints.contains("getProducts")) routes = routes:::List(Implementations1_4_0.getProducts)
    if (!disabledEndpoints.contains("getCrmEvents")) routes = routes:::List(Implementations1_4_0.getCrmEvents)
    // Now in 2.0.0 if (!disabledEndpoints.contains("createTransactionRequest")) routes = routes:::List(Implementations1_4_0.createTransactionRequest)
    // Now in 2.0.0 if (!disabledEndpoints.contains("getTransactionRequests")) routes = routes:::List(Implementations1_4_0.getTransactionRequests)
    if (!disabledEndpoints.contains("getTransactionRequestTypes")) routes = routes:::List(Implementations1_4_0.getTransactionRequestTypes)
  }
  // ### VERSION 1.4.0 - END ###



  // ### VERSION 2.0.0 - BEGIN ###
  // Updated in 2.0.0 (less info about the views)
  if (!disabledVersions.contains("v2_0_0")){
    if (!disabledEndpoints.contains("allAccountsAllBanks")) routes = routes:::List(Implementations2_0_0.allAccountsAllBanks)
    if (!disabledEndpoints.contains("privateAccountsAllBanks")) routes = routes:::List(Implementations2_0_0.privateAccountsAllBanks)
    if (!disabledEndpoints.contains("publicAccountsAllBanks")) routes = routes:::List(Implementations2_0_0.publicAccountsAllBanks)
    if (!disabledEndpoints.contains("allAccountsAtOneBank")) routes = routes:::List(Implementations2_0_0.allAccountsAtOneBank)
    if (!disabledEndpoints.contains("privateAccountsAtOneBank")) routes = routes:::List(Implementations2_0_0.privateAccountsAtOneBank)
    if (!disabledEndpoints.contains("publicAccountsAtOneBank")) routes = routes:::List(Implementations2_0_0.publicAccountsAtOneBank)
    // Now in 2.1.0 if (!disabledEndpoints.contains("createTransactionRequest")) routes = routes:::List(Implementations2_0_0.createTransactionRequest)
    if (!disabledEndpoints.contains("answerTransactionRequestChallenge")) routes = routes:::List(Implementations2_0_0.answerTransactionRequestChallenge)
    // Now in 2.1.0 if (!disabledEndpoints.contains("getTransactionRequests")) routes = routes:::List(Implementations2_0_0.getTransactionRequests) // Now has charges information
    // Updated in 2.0.0 (added sorting and better guards / error messages)
    if (!disabledEndpoints.contains("accountById")) routes = routes:::List(Implementations2_0_0.accountById)
    if (!disabledEndpoints.contains("getPermissionsForBankAccount")) routes = routes:::List(Implementations2_0_0.getPermissionsForBankAccount)
    if (!disabledEndpoints.contains("getPermissionForUserForBankAccount")) routes = routes:::List(Implementations2_0_0.getPermissionForUserForBankAccount)
    // New in 2.0.0
    if (!disabledEndpoints.contains("getKycDocuments")) routes = routes:::List(Implementations2_0_0.getKycDocuments)
    if (!disabledEndpoints.contains("getKycMedia")) routes = routes:::List(Implementations2_0_0.getKycMedia)
    if (!disabledEndpoints.contains("getKycStatuses")) routes = routes:::List(Implementations2_0_0.getKycStatuses)
    if (!disabledEndpoints.contains("getKycChecks")) routes = routes:::List(Implementations2_0_0.getKycChecks)
    if (!disabledEndpoints.contains("getSocialMediaHandles")) routes = routes:::List(Implementations2_0_0.getSocialMediaHandles)
    if (!disabledEndpoints.contains("addKycDocument")) routes = routes:::List(Implementations2_0_0.addKycDocument)
    if (!disabledEndpoints.contains("addKycMedia")) routes = routes:::List(Implementations2_0_0.addKycMedia)
    if (!disabledEndpoints.contains("addKycStatus")) routes = routes:::List(Implementations2_0_0.addKycStatus)
    if (!disabledEndpoints.contains("addKycCheck")) routes = routes:::List(Implementations2_0_0.addKycCheck)
    if (!disabledEndpoints.contains("addSocialMediaHandle")) routes = routes:::List(Implementations2_0_0.addSocialMediaHandle)
    if (!disabledEndpoints.contains("getCoreAccountById")) routes = routes:::List(Implementations2_0_0.getCoreAccountById)
    if (!disabledEndpoints.contains("getCoreTransactionsForBankAccount")) routes = routes:::List(Implementations2_0_0.getCoreTransactionsForBankAccount)
    if (!disabledEndpoints.contains("createAccount")) routes = routes:::List(Implementations2_0_0.createAccount)
    if (!disabledEndpoints.contains("getTransactionTypes")) routes = routes:::List(Implementations2_0_0.getTransactionTypes)
    if (!disabledEndpoints.contains("createUser")) routes = routes:::List(Implementations2_0_0.createUser)
    if (!disabledEndpoints.contains("createMeeting")) routes = routes:::List(Implementations2_0_0.createMeeting)
    if (!disabledEndpoints.contains("getMeetings")) routes = routes:::List(Implementations2_0_0.getMeetings)
    if (!disabledEndpoints.contains("getMeeting")) routes = routes:::List(Implementations2_0_0.getMeeting)
    if (!disabledEndpoints.contains("createCustomer")) routes = routes:::List(Implementations2_0_0.createCustomer)
    if (!disabledEndpoints.contains("getCurrentUser")) routes = routes:::List(Implementations2_0_0.getCurrentUser)
    if (!disabledEndpoints.contains("getUser")) routes = routes:::List(Implementations2_0_0.getUser)
    if (!disabledEndpoints.contains("createUserCustomerLinks")) routes = routes:::List(Implementations2_0_0.createUserCustomerLinks)
    if (!disabledEndpoints.contains("addEntitlement")) routes = routes:::List(Implementations2_0_0.addEntitlement)
    if (!disabledEndpoints.contains("getEntitlements")) routes = routes:::List(Implementations2_0_0.getEntitlements)
    if (!disabledEndpoints.contains("deleteEntitlement")) routes = routes:::List(Implementations2_0_0.deleteEntitlement)
    if (!disabledEndpoints.contains("getAllEntitlements")) routes = routes:::List(Implementations2_0_0.getAllEntitlements)
    if (!disabledEndpoints.contains("elasticSearchWarehouse")) routes = routes:::List(Implementations2_0_0.elasticSearchWarehouse)
    if (!disabledEndpoints.contains("elasticSearchMetrics")) routes = routes:::List(Implementations2_0_0.elasticSearchMetrics)
    if (!disabledEndpoints.contains("getCustomers")) routes = routes:::List(Implementations2_0_0.getCustomers)
  }
  // ### VERSION 2.0.0 - END ###



  // ### VERSION 2.1.0 - BEGIN ###
  // New in 2.1.0
  if (!disabledVersions.contains("v2_1_0")){
    if (!disabledEndpoints.contains("sandboxDataImport")) routes = routes:::List(Implementations2_1_0.sandboxDataImport)
    if (!disabledEndpoints.contains("getTransactionRequestTypesSupportedByBank")) routes = routes:::List(Implementations2_1_0.getTransactionRequestTypesSupportedByBank)
    if (!disabledEndpoints.contains("createTransactionRequest")) routes = routes:::List(Implementations2_1_0.createTransactionRequest)
    if (!disabledEndpoints.contains("getTransactionRequests")) routes = routes:::List(Implementations2_1_0.getTransactionRequests)
    if (!disabledEndpoints.contains("createBranch")) routes = routes:::List(Implementations2_1_0.createBranch)
    if (!disabledEndpoints.contains("getBranch")) routes = routes:::List(Implementations2_1_0.getBranch)
  }
  // ### VERSION 2.1.0 - END ###

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

}
