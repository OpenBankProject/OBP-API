/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v3_1_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ApiVersion
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.APIMethods140
import code.api.v2_0_0.APIMethods200
import code.api.v2_1_0.APIMethods210
import code.api.v2_2_0.APIMethods220
import code.api.v3_0_0.APIMethods300
import code.api.v3_0_0.custom.CustomAPIMethods300
import code.util.Helper.MdcLoggable

import scala.collection.immutable.Nil



/*
This file defines which endpoints from all the versions are available in v3.0.0
 */


object OBPAPI3_1_0 extends OBPRestHelper with APIMethods130 with APIMethods140 with APIMethods200 with APIMethods210 with APIMethods220 with APIMethods300 with CustomAPIMethods300 with APIMethods310 with MdcLoggable {

  val version : ApiVersion = ApiVersion.v3_1_0

  val versionStatus = "BLEEDING-EDGE" // TODO this should be a property of ApiVersion.


  // Possible Endpoints from 1.2.1
  val endpointsOf1_2_1 =  Implementations1_2_1.addCommentForViewOnTransaction ::
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
//                          Implementations1_2_1.bankById ::
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
//                          Implementations1_2_1.getBanks ::
                          Implementations1_2_1.getCommentsForViewOnTransaction ::
//                          Implementations1_2_1.getOtherAccountsForBankAccount ::
//                          Implementations1_2_1.getOtherAccountByIdForBankAccount ::
                          Implementations1_2_1.getOtherAccountForTransaction ::
                          Implementations1_2_1.getOtherAccountMetadata ::
                          Implementations1_2_1.getOtherAccountPrivateAlias ::
                          Implementations1_2_1.getCounterpartyPublicAlias ::
                          Implementations1_2_1.getImagesForViewOnTransaction ::
                          Implementations1_2_1.getTagsForViewOnTransaction ::
                          // Implementations1_2_1.getTransactionByIdForBankAccount ::
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


  // Possible Endpoints from VERSION 1.3.0
  val endpointsOf1_3_0 = Implementations1_3_0.getCards ::
                         Implementations1_3_0.getCardsForBank ::
                         Nil


  // Possible Endpoints from 1.4.0
  val endpointsOf1_4_0 = Implementations1_4_0.getCustomerMessages ::
                          Implementations1_4_0.addCustomerMessage ::
                          // Implementations1_4_0.getBranches :: //now in V300
                          // Implementations1_4_0.getAtms :: //now in V300
                          Implementations1_4_0.getCrmEvents ::
                          Implementations1_4_0.getTransactionRequestTypes ::
                         Nil


  // Possible Endpoints from 2.0.0
  val endpointsOf2_0_0 = //Now in V3.0.0 Implementations2_0_0.allAccountsAllBanks ::
                         //Now in V3.0.0 Implementations2_0_0.accountById ::
                          Implementations2_0_0.addEntitlement ::
                          Implementations2_0_0.addKycCheck ::
                          Implementations2_0_0.addKycDocument ::
                          Implementations2_0_0.addKycMedia ::
                          Implementations2_0_0.addKycStatus ::
                          Implementations2_0_0.addSocialMediaHandle ::
                          Implementations2_0_0.getPrivateAccountsAtOneBank ::
                          //now in V220
                          //Implementations2_0_0.createAccount ::
//                          Implementations2_0_0.createMeeting ::
                          Implementations2_0_0.createUser ::
                          Implementations2_0_0.createUserCustomerLinks ::
                          Implementations2_0_0.deleteEntitlement ::
                          Implementations2_0_0.elasticSearchMetrics ::
                          //Implementations2_0_0.elasticSearchWarehouse ::
                          // Implementations2_0_0.getAllEntitlements ::
                          //now in V300 Implementations2_0_0.getCoreAccountById ::
                          //now in V300 Implementations2_0_0.getCoreTransactionsForBankAccount ::
                          // Implementations2_0_0.getCurrentUser ::
                          Implementations2_0_0.getEntitlements ::
                          Implementations2_0_0.getKycChecks ::
                          Implementations2_0_0.getKycDocuments ::
                          Implementations2_0_0.getKycMedia ::
                          Implementations2_0_0.getKycStatuses ::
//                          Implementations2_0_0.getMeeting ::
//                          Implementations2_0_0.getMeetings ::
                          Implementations2_0_0.getPermissionsForBankAccount ::
                          Implementations2_0_0.getSocialMediaHandles ::
                          Implementations2_0_0.getTransactionTypes ::
                          // Implementations2_0_0.getUser ::
                          //now in V300 Implementations2_0_0.corePrivateAccountsAllBanks ::
                          //now in V300 Implementations2_0_0.privateAccountsAtOneBank ::
                          Implementations2_0_0.publicAccountsAllBanks ::
                          Implementations2_0_0.publicAccountsAtOneBank ::
                          Nil


  // Possible Endpoints from 2.1.0
  val endpointsOf2_1_0 = Implementations2_1_0.sandboxDataImport ::
                          Implementations2_1_0.getTransactionRequestTypesSupportedByBank ::
                          Implementations2_1_0.createTransactionRequest ::
                          Implementations2_1_0.answerTransactionRequestChallenge ::
                          // Implementations2_1_0.getTransactionRequests ::
                          Implementations2_1_0.getRoles ::
                          Implementations2_1_0.getEntitlementsByBankAndUser ::
                          Implementations2_1_0.getConsumer ::
                          Implementations2_1_0.getConsumers ::
                          Implementations2_1_0.enableDisableConsumers ::
                          Implementations2_1_0.addCardForBank ::
                          // Implementations2_1_0.getUsers ::
                          Implementations2_1_0.createTransactionType ::
                          // Implementations2_1_0.getAtm :: //now in V300
                          // Implementations2_1_0.getBranch :: //now in V300
                          Implementations2_1_0.updateBranch ::
                          // Implementations2_1_0.getProduct ::
                          // Implementations2_1_0.getProducts ::
                          // Implementations2_1_0.createCustomer ::
                          Implementations2_1_0.getCustomersForCurrentUserAtBank ::
                          // Implementations2_1_0.getCustomersForUser ::
                          Implementations2_1_0.updateConsumerRedirectUrl ::
                          Implementations2_1_0.getMetrics ::
                          Nil


  // Possible Endpoints from 2.1.0
  val endpointsOf2_2_0 =  Implementations2_2_0.getCurrentFxRate ::
                          Implementations2_2_0.createFx ::
                          Implementations2_2_0.getExplictCounterpartiesForAccount ::
                          Implementations2_2_0.getExplictCounterpartyById ::
                          Implementations2_2_0.getMessageDocs ::
                          Implementations2_2_0.createBank ::
                          Implementations2_2_0.createAccount ::
                          //Implementations2_2_0.createAtm ::
                          //Implementations2_2_0.createProduct ::
//                          Implementations2_2_0.config ::
                          Implementations2_2_0.getConnectorMetrics ::
                          Implementations2_2_0.createCounterparty ::
                          //Implementations2_2_0.getCustomersForUser ::
                          //Implementations2_2_0.getCoreTransactionsForBankAccount ::
    Nil

  
  // Possible Endpoints from 3.0.0
  val endpointsOf3_0_0 = Implementations3_0_0.getCoreTransactionsForBankAccount ::
                          Implementations3_0_0.getTransactionsForBankAccount ::
                          Implementations3_0_0.getPrivateAccountById ::
                          Implementations3_0_0.getPublicAccountById ::
                          Implementations3_0_0.getCoreAccountById ::
                          Implementations3_0_0.getViewsForBankAccount ::
                          Implementations3_0_0.createViewForBankAccount ::
                          Implementations3_0_0.updateViewForBankAccount ::
                          Implementations3_0_0.corePrivateAccountsAllBanks ::
                          Implementations3_0_0.dataWarehouseSearch ::
                          Implementations3_0_0.getUser ::
                          Implementations3_0_0.getUserByUserId ::
                          Implementations3_0_0.getUserByUsername ::
                          Implementations3_0_0.getAdapter ::
                          Implementations3_0_0.createBranch ::
                          Implementations3_0_0.getBranches ::
                          Implementations3_0_0.getBranch ::
                          Implementations3_0_0.createAtm ::
                          Implementations3_0_0.getAtm ::
                          Implementations3_0_0.getAtms ::
                          Implementations3_0_0.getUsers ::
                          Implementations3_0_0.getCustomersForUser ::
                          Implementations3_0_0.getCurrentUser ::
                          Implementations3_0_0.privateAccountsAtOneBank ::
                          Implementations3_0_0.getPrivateAccountIdsbyBankId ::
                          Implementations3_0_0.getOtherAccountsForBankAccount ::
                          Implementations3_0_0.getOtherAccountByIdForBankAccount ::
                          Implementations3_0_0.addEntitlementRequest ::
                          Implementations3_0_0.getAllEntitlementRequests ::
                          Implementations3_0_0.getEntitlementRequests ::
                          Implementations3_0_0.deleteEntitlementRequest ::
                          Implementations3_0_0.dataWarehouseStatistics ::
                          Implementations3_0_0.getEntitlementRequestsForCurrentUser ::
                          Implementations3_0_0.getFirehoseAccountsAtOneBank ::
                          Implementations3_0_0.getEntitlementsForCurrentUser ::
                          Implementations3_0_0.getFirehoseTransactionsForBankAccount ::
                          Implementations3_0_0.getApiGlossary ::
                          Implementations3_0_0.getAccountsHeld ::
                          Implementations3_0_0.getAggregateMetrics ::
                          Implementations3_0_0.addScope ::
                          Implementations3_0_0.deleteScope ::
                          Implementations3_0_0.getScopes ::
                          Implementations3_0_0.getBanks ::
                          Implementations3_0_0.bankById ::
                          Implementations3_0_0.getPermissionForUserForBankAccount ::
                          Nil


  // Possible Endpoints from 3.0.0 Custom Folder
  val endpointsOfCustom3_0_0 = ImplementationsCustom3_0_0.endpointsOfCustom3_0_0
  
  // Possible Endpoints from 3.1.0
  val endpointsOf3_1_0 =  Implementations3_1_0.getCheckbookOrders :: 
                          Implementations3_1_0.getStatusOfCreditCardOrder ::
                          Implementations3_1_0.createCreditLimitRequest ::
                          Implementations3_1_0.getCreditLimitRequests ::
                          Implementations3_1_0.getCreditLimitRequestByRequestId ::
                          Implementations3_1_0.getTopAPIs ::
                          Implementations3_1_0.getMetricsTopConsumers ::
                          Implementations3_1_0.getFirehoseCustomers ::
                          Implementations3_1_0.getBadLoginStatus ::
                          Implementations3_1_0.unlockUser ::
                          Implementations3_1_0.callsLimit ::
                          Implementations3_1_0.getCallsLimit ::
                          Implementations3_1_0.checkFundsAvailable ::
                          // Implementations3_1_0.getConsumer ::
                          Implementations3_1_0.getConsumersForCurrentUser ::
                          // Implementations3_1_0.getConsumers ::
                          Implementations3_1_0.createAccountWebhook ::
                          Implementations3_1_0.enableDisableAccountWebhook ::
                          Implementations3_1_0.getAccountWebhooks ::
                          Implementations3_1_0.config ::
                          Implementations3_1_0.getAdapterInfo ::
                          Implementations3_1_0.getTransactionByIdForBankAccount ::
                          Implementations3_1_0.getTransactionRequests ::
                          Implementations3_1_0.createCustomer ::
                          Implementations3_1_0.getRateLimitingInfo ::
                          Implementations3_1_0.getCustomerByCustomerId ::
                          Implementations3_1_0.getCustomerByCustomerNumber ::
                          Implementations3_1_0.createUserAuthContext ::
                          Implementations3_1_0.getUserAuthContexts ::
                          Implementations3_1_0.deleteUserAuthContexts::
                          Implementations3_1_0.deleteUserAuthContextById::
                          Implementations3_1_0.createTaxResidence ::
                          Implementations3_1_0.getTaxResidence ::
                          Implementations3_1_0.deleteTaxResidence ::
                          Implementations3_1_0.getAllEntitlements ::
                          Implementations3_1_0.createCustomerAddress ::
                          Implementations3_1_0.getCustomerAddresses ::
                          Implementations3_1_0.deleteCustomerAddress ::
                          Implementations3_1_0.getObpApiLoopback ::
                          Implementations3_1_0.refreshUser ::
                          Implementations3_1_0.createProductAttribute ::
                          Implementations3_1_0.getProductAttribute ::
                          Implementations3_1_0.updateProductAttribute ::
                          Implementations3_1_0.deleteProductAttribute ::
                          Implementations3_1_0.createAccountApplication ::
                          Implementations3_1_0.getAccountApplications ::
                          Implementations3_1_0.getAccountApplication ::
                          Implementations3_1_0.updateAccountApplicationStatus ::
                          Implementations3_1_0.createProduct ::
                          Implementations3_1_0.updateCustomerAddress ::
                          Implementations3_1_0.getProduct ::
                          Implementations3_1_0.getProducts ::
                          Implementations3_1_0.getProductTree ::
                          Implementations3_1_0.createProductCollection ::
                          Implementations3_1_0.getProductCollection ::
                          Implementations3_1_0.createAccountAttribute ::
                          Implementations3_1_0.deleteBranch ::
                          Implementations3_1_0.createMeeting ::
                          Implementations3_1_0.getMeetings ::
                          Implementations3_1_0.getMeeting ::
                          Implementations3_1_0.getOAuth2ServerJWKsURIs ::
                          Implementations3_1_0.createConsent ::
                          Implementations3_1_0.answerConsentChallenge ::
                          Implementations3_1_0.getConsents ::
                          Implementations3_1_0.revokeConsent ::
                          Implementations3_1_0.createUserAuthContextUpdate ::
                          Implementations3_1_0.answerUserAuthContextUpdateChallenge ::
                          Implementations3_1_0.getSystemView ::
                          Implementations3_1_0.createSystemView ::
                          Implementations3_1_0.deleteSystemView ::
                          Implementations3_1_0.updateSystemView ::
                          Implementations3_1_0.getOAuth2ServerJWKsURIs ::
                          Implementations3_1_0.getMessageDocsSwagger ::
                          Nil
  
  val allResourceDocs = Implementations3_1_0.resourceDocs ++
                        Implementations3_0_0.resourceDocs ++
                        ImplementationsCustom3_0_0.resourceDocs ++
                        Implementations2_2_0.resourceDocs ++
                        Implementations2_1_0.resourceDocs ++
                        Implementations2_0_0.resourceDocs ++
                        Implementations1_4_0.resourceDocs ++
                        Implementations1_3_0.resourceDocs ++
                        Implementations1_2_1.resourceDocs
  
  def findResourceDoc(pf: OBPEndpoint): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction==pf)
  }

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] =
    List(Implementations1_2_1.root(version, versionStatus)) ::: // For now we make this mandatory
  getAllowedEndpoints(endpointsOf1_2_1, Implementations1_2_1.resourceDocs) :::
  getAllowedEndpoints(endpointsOf1_3_0, Implementations1_3_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOf1_4_0, Implementations1_4_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOf2_0_0, Implementations2_0_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOf2_1_0, Implementations2_1_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOf2_2_0, Implementations2_2_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOf3_0_0, Implementations3_0_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOfCustom3_0_0, ImplementationsCustom3_0_0.resourceDocs) :::
  getAllowedEndpoints(endpointsOf3_1_0, Implementations3_1_0.resourceDocs)


  // Make them available for use!
  routes.foreach(route => {
    oauthServe(apiPrefix{route}, findResourceDoc(route))
  })

  logger.info(s"version $version has been run! There are ${routes.length} routes.")

}
