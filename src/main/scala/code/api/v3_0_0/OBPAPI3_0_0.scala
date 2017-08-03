/**
  * Open Bank Project - API
  * Copyright (C) 2011-2016, TESOBE Ltd
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
package code.api.v3_0_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.ResourceDoc
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.APIMethods140
import code.api.v2_0_0.APIMethods200
import code.api.v2_1_0.APIMethods210
import code.api.v2_2_0.APIMethods220
import code.api.v2_2_0.OBPAPI2_2_0._
import code.model.User
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.util.Props

import scala.collection.immutable.Nil



/*
This file defines which endpoints from all the versions are available in v3.0.0
 */


object OBPAPI3_0_0 extends OBPRestHelper with APIMethods130 with APIMethods140 with APIMethods200 with APIMethods210 with APIMethods220 with APIMethods300 with MdcLoggable {
  
  
  val version = "3.0.0"
  val versionStatus = "BLEEDING-EDGE"

  // Get disabled API versions from props
  val disabledVersions = Props.get("api_disabled_versions").getOrElse("").replace("[", "").replace("]", "").split(",")
  // Get disabled API endpoints from props
  val disabledEndpoints = Props.get("api_disabled_endpoints").getOrElse("").replace("[", "").replace("]", "").split(",")

  // Note: Since we pattern match on these routes, if two implementations match a given url the first will match

  var routes = List(Implementations1_2_1.root(version, versionStatus))


  // ### VERSION 1.2.1 - BEGIN ###
  //First step - make a list of allowed endpoints
  val endpointsOf1_2_1 = Implementations1_2_1.addCommentForViewOnTransaction ::
                          Implementations1_2_1.addCounterpartyCorporateLocation::
                          Implementations1_2_1.addCounterpartyImageUrl ::
                          Implementations1_2_1.addCounterpartyMoreInfo ::
                          Implementations1_2_1.addCounterpartyOpenCorporatesUrl ::
                          Implementations1_2_1.addCounterpartyPhysicalLocation ::
                          Implementations1_2_1.addOtherAccountPrivateAlias ::
                          Implementations1_2_1.addCounterpartyPublicAlias ::
                          Implementations1_2_1.addCounterpartyUrl ::
                          Implementations1_2_1.addImageForViewOnTransaction ::
                          Implementations1_2_1.addPermissionForUserForBankAccountForMultipleViews ::
                          Implementations1_2_1.addPermissionForUserForBankAccountForOneView ::
                          Implementations1_2_1.addTagForViewOnTransaction ::
                          Implementations1_2_1.addTransactionNarrative ::
                          Implementations1_2_1.addWhereTagForViewOnTransaction ::
                          // Now in 3.0.0 "allAccountsAllBanks"::
                          Implementations1_2_1.bankById ::
                          // Implementations1_2_1.createViewForBankAccount ::
                          Implementations1_2_1.deleteCommentForViewOnTransaction ::
                          Implementations1_2_1.deleteCommentForViewOnTransaction ::
                          Implementations1_2_1.deleteCounterpartyCorporateLocation ::
                          Implementations1_2_1.deleteCounterpartyImageUrl ::
                          Implementations1_2_1.deleteCounterpartyMoreInfo ::
                          Implementations1_2_1.deleteCounterpartyOpenCorporatesUrl ::
                          Implementations1_2_1.deleteCounterpartyPhysicalLocation ::
                          Implementations1_2_1.deleteCounterpartyPrivateAlias ::
                          Implementations1_2_1.deleteCounterpartyPublicAlias ::
                          Implementations1_2_1.deleteCounterpartyUrl ::
                          Implementations1_2_1.deleteImageForViewOnTransaction ::
                          Implementations1_2_1.deleteTagForViewOnTransaction ::
                          Implementations1_2_1.deleteTransactionNarrative ::
                          Implementations1_2_1.deleteViewForBankAccount::
                          Implementations1_2_1.deleteWhereTagForViewOnTransaction ::
                          Implementations1_2_1.getBanks ::
                          Implementations1_2_1.getCommentsForViewOnTransaction ::
                          Implementations1_2_1.getOtherAccountsForBankAccount ::
                          Implementations1_2_1.getOtherAccountByIdForBankAccount ::
                          Implementations1_2_1.getOtherAccountForTransaction ::
                          Implementations1_2_1.getOtherAccountMetadata ::
                          Implementations1_2_1.getOtherAccountPrivateAlias ::
                          Implementations1_2_1.getCounterpartyPublicAlias ::
                          Implementations1_2_1.getImagesForViewOnTransaction ::
                          Implementations1_2_1.getTagsForViewOnTransaction ::
                          Implementations1_2_1.getTransactionByIdForBankAccount ::
                          Implementations1_2_1.getTransactionNarrative ::
                          //now in V300      
                          //Implementations1_2_1.getTransactionsForBankAccount ::
                          //Implementations1_2_1.getViewsForBankAccount ::
                          Implementations1_2_1.getWhereTagForViewOnTransaction ::
                          Implementations1_2_1.removePermissionForUserForBankAccountForAllViews ::
                          Implementations1_2_1.removePermissionForUserForBankAccountForOneView ::
                          Implementations1_2_1.updateAccountLabel ::
                          Implementations1_2_1.updateCounterpartyCorporateLocation ::
                          Implementations1_2_1.updateCounterpartyImageUrl ::
                          Implementations1_2_1.updateCounterpartyMoreInfo ::
                          Implementations1_2_1.updateCounterpartyOpenCorporatesUrl ::
                          Implementations1_2_1.updateCounterpartyPhysicalLocation ::
                          Implementations1_2_1.updateCounterpartyPrivateAlias ::
                          Implementations1_2_1.updateCounterpartyPublicAlias ::
                          Implementations1_2_1.updateCounterpartyUrl ::
                          Implementations1_2_1.updateTransactionNarrative ::
                          //Implementations1_2_1.updateViewForBankAccount ::
                          Implementations1_2_1.updateWhereTagForViewOnTransaction ::
                          Nil

  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations1_2_1.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf1_2_1.exists(_ == item.partialFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 1.2.1 - END ###


  // ### VERSION 1.3.0 - BEGIN ###
  // New in 1.3.0
  //First step - make a list of allowed endpoints
  val endpointsOf1_3_0 = Implementations1_3_0.getCards ::
                         Implementations1_3_0.getCardsForBank ::
                         Nil
  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations1_3_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf1_3_0.exists(_ == item.partialFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 1.3.0 - END ###



  // ### VERSION 1.4.0 - BEGIN ###
  // New in 1.4.0
  //First step - make a list of allowed endpoints
  val endpointsOf1_4_0 = Implementations1_4_0.getCustomerMessages ::
                          Implementations1_4_0.addCustomerMessage ::
                          // Implementations1_4_0.getBranches ::
                          Implementations1_4_0.getAtms ::
                          Implementations1_4_0.getCrmEvents ::
                          Implementations1_4_0.getTransactionRequestTypes ::
                         Nil
  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations1_4_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf1_4_0.exists(_ == item.partialFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 1.4.0 - END ###



  // ### VERSION 2.0.0 - BEGIN ###
  // Updated in 2.0.0 (less info about the views)
  //First step - make a list of allowed endpoints
  val endpointsOf2_0_0 = //Now in V3.0.0 Implementations2_0_0.allAccountsAllBanks ::
                         //Now in V3.0.0 Implementations2_0_0.accountById ::
                          Implementations2_0_0.addEntitlement ::
                          Implementations2_0_0.addKycCheck ::
                          Implementations2_0_0.addKycDocument ::
                          Implementations2_0_0.addKycMedia ::
                          Implementations2_0_0.addKycStatus ::
                          Implementations2_0_0.addSocialMediaHandle ::
                          Implementations2_0_0.allAccountsAtOneBank ::
                          //now in V220
                          //Implementations2_0_0.createAccount ::
                          Implementations2_0_0.createMeeting ::
                          Implementations2_0_0.createUser ::
                          Implementations2_0_0.createUserCustomerLinks ::
                          Implementations2_0_0.deleteEntitlement ::
                          Implementations2_0_0.elasticSearchMetrics ::
                          //Implementations2_0_0.elasticSearchWarehouse ::
                          Implementations2_0_0.getAllEntitlements ::
                          //now in V300 Implementations2_0_0.getCoreAccountById ::
                          //now in V300 Implementations2_0_0.getCoreTransactionsForBankAccount ::
                          Implementations2_0_0.getCurrentUser ::
                          Implementations2_0_0.getEntitlements ::
                          Implementations2_0_0.getKycChecks ::
                          Implementations2_0_0.getKycDocuments ::
                          Implementations2_0_0.getKycMedia ::
                          Implementations2_0_0.getKycStatuses ::
                          Implementations2_0_0.getMeeting ::
                          Implementations2_0_0.getMeetings ::
                          Implementations2_0_0.getPermissionForUserForBankAccount ::
                          Implementations2_0_0.getPermissionsForBankAccount ::
                          Implementations2_0_0.getSocialMediaHandles ::
                          Implementations2_0_0.getTransactionTypes ::
                          // Implementations2_0_0.getUser ::
                          //now in V300 Implementations2_0_0.corePrivateAccountsAllBanks ::
                          Implementations2_0_0.privateAccountsAtOneBank ::
                          Implementations2_0_0.publicAccountsAllBanks ::
                          Implementations2_0_0.publicAccountsAtOneBank ::
                          Nil
  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations2_0_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf2_0_0.exists(_ == item.partialFunction)) {
      routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 2.0.0 - END ###



  // ### VERSION 2.1.0 - BEGIN ###
  // New in 2.1.0
  //First step - make a list of allowed endpoints
  val endpointsOf2_1_0 = Implementations2_1_0.sandboxDataImport ::
                          Implementations2_1_0.getTransactionRequestTypesSupportedByBank ::
                          Implementations2_1_0.createTransactionRequest ::
                          Implementations2_1_0.answerTransactionRequestChallenge ::
                          Implementations2_1_0.getTransactionRequests ::
                          Implementations2_1_0.getRoles ::
                          Implementations2_1_0.getEntitlementsByBankAndUser ::
                          Implementations2_1_0.getConsumer ::
                          Implementations2_1_0.getConsumers ::
                          Implementations2_1_0.enableDisableConsumers ::
                          Implementations2_1_0.addCardsForBank ::
                          Implementations2_1_0.getUsers ::
                          Implementations2_1_0.createTransactionType ::
                          Implementations2_1_0.createCounterparty ::
                          Implementations2_1_0.getAtm ::
                          Implementations2_1_0.getBranch ::
                          Implementations2_1_0.updateBranch ::
                          Implementations2_1_0.getProduct ::
                          Implementations2_1_0.getProducts ::
                          Implementations2_1_0.createCustomer ::
                          Implementations2_1_0.getCustomer ::
                          Implementations2_1_0.getCustomers ::
                          Implementations2_1_0.updateConsumerRedirectUrl ::
                          Implementations2_1_0.getMetrics ::
                          Nil
  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations2_1_0.resourceDocs if !disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) &&  endpointsOf2_1_0.exists(_ == item.partialFunction)) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 2.1.0 - END ###
  
  
  // ### VERSION 2.1.0 - BEGIN ###
  //First step - make a list of allowed endpoints
  val endpointsOf2_2_0 =  Implementations2_2_0.getCurrentFxRate ::
                          Implementations2_2_0.getCounterpartiesForAccount ::
                          Implementations2_2_0.getMessageDocs ::
                          Implementations2_2_0.createBank ::
                          Implementations2_2_0.createAccount ::
                          Implementations2_2_0.createAtm ::
                          Implementations2_2_0.createProduct ::
                          Implementations2_2_0.config ::
                          Implementations2_2_0.getConnectorMetrics ::
                          Nil
  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations2_2_0.resourceDocs if (!disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) && endpointsOf2_2_0.exists(_ == item.partialFunction))) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 2.2.0 - END ###
  
  // New in 3.0.0
  //First step - make a list of allowed endpoints
  val endpointsOf3_0_0 = Implementations3_0_0.getCoreTransactionsForBankAccount ::
                          Implementations3_0_0.getTransactionsForBankAccount ::
                          Implementations3_0_0.accountById ::
                          Implementations3_0_0.getCoreAccountById ::
                          Implementations3_0_0.getViewsForBankAccount ::
                          Implementations3_0_0.createViewForBankAccount ::
                          Implementations3_0_0.updateViewForBankAccount ::
                          Implementations3_0_0.corePrivateAccountsAllBanks ::
                          Implementations3_0_0.elasticSearchWarehouseV300 ::
                          Implementations3_0_0.getUser ::
                          Implementations3_0_0.getUserByUserId ::
                          Implementations3_0_0.getUserByUsername ::
                          Implementations3_0_0.getBranches ::
                          // TODO Added getBranch here
                          Implementations3_0_0.getAdapter ::
                          Implementations3_0_0.createBranch ::
                          Nil
  //Second step - iterate through all endpoints defined in resource doc
  //       then - omit endpoints of disabled version in props file
  //       and  - omit partially disabled endpoint in props file
  //       and  - add only ones which intersect with the list defined in the first step
  for ( item <- Implementations3_0_0.resourceDocs if (!disabledVersions.contains("v" + item.apiVersion) && !disabledEndpoints.contains(item.apiFunction) && endpointsOf3_0_0.exists(_ == item.partialFunction))) {
    routes = routes:::List(item.partialFunction)
  }
  // ### VERSION 3.0.0 - END ###

  def findResourceDoc(pf: PartialFunction[Req, Box[User] => Box[JsonResponse]]): Option[ResourceDoc] = {
    val all = Implementations3_0_0.resourceDocs ++
              Implementations2_2_0.resourceDocs ++
              Implementations2_1_0.resourceDocs ++
              Implementations2_0_0.resourceDocs ++
              Implementations1_4_0.resourceDocs ++
              Implementations1_3_0.resourceDocs ++
              Implementations1_2_1.resourceDocs
    all.find(_.partialFunction==pf)
  }

  routes.foreach(route => {
    oauthServe(apiPrefix{route}, findResourceDoc(route))
  })

}
