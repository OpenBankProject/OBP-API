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
package code.api.v2_0_0

import code.api.OBPRestHelper
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.APIMethods140
import net.liftweb.common.Loggable
import net.liftweb.util.Props

object OBPAPI2_0_0 extends OBPRestHelper with APIMethods130 with APIMethods140 with APIMethods200 with Loggable {


  val VERSION = "2.0.0"


  // Note: Since we pattern match on these routes, if two implementations match a given url the first will match

  val routes = List(
    Implementations1_2_1.root(VERSION),
    Implementations1_2_1.getBanks,
    Implementations1_2_1.bankById,
    // Now in 2_0_0
//  Implementations1_2_1.allAccountsAllBanks,
//  Implementations1_2_1.privateAccountsAllBanks,
//  Implementations1_2_1.publicAccountsAllBanks,
//  Implementations1_2_1.allAccountsAtOneBank,
//  Implementations1_2_1.privateAccountsAtOneBank,
//  Implementations1_2_1.publicAccountsAtOneBank,
//  Implementations1_2_1.accountById,
    Implementations1_2_1.updateAccountLabel,
    Implementations1_2_1.getViewsForBankAccount,
    Implementations1_2_1.createViewForBankAccount,
    Implementations1_2_1.updateViewForBankAccount,
    Implementations1_2_1.deleteViewForBankAccount,
//    Implementations1_2_1.getPermissionsForBankAccount,
//    Implementations1_2_1.getPermissionForUserForBankAccount,
    Implementations1_2_1.addPermissionForUserForBankAccountForMultipleViews,
    Implementations1_2_1.addPermissionForUserForBankAccountForOneView,
    Implementations1_2_1.removePermissionForUserForBankAccountForOneView,
    Implementations1_2_1.removePermissionForUserForBankAccountForAllViews,
    Implementations1_2_1.getCounterpartiesForBankAccount,
    Implementations1_2_1.getCounterpartyByIdForBankAccount,
    Implementations1_2_1.getCounterpartyMetadata,
    Implementations1_2_1.getCounterpartyPublicAlias,
    Implementations1_2_1.addCounterpartyPublicAlias,
    Implementations1_2_1.updateCounterpartyPublicAlias,
    Implementations1_2_1.deleteCounterpartyPublicAlias,
    Implementations1_2_1.getCounterpartyPrivateAlias,
    Implementations1_2_1.addCounterpartyPrivateAlias,
    Implementations1_2_1.updateCounterpartyPrivateAlias,
    Implementations1_2_1.deleteCounterpartyPrivateAlias,
    Implementations1_2_1.addCounterpartyMoreInfo,
    Implementations1_2_1.updateCounterpartyMoreInfo,
    Implementations1_2_1.deleteCounterpartyMoreInfo,
    Implementations1_2_1.addCounterpartyUrl,
    Implementations1_2_1.updateCounterpartyUrl,
    Implementations1_2_1.deleteCounterpartyUrl,
    Implementations1_2_1.addCounterpartyImageUrl,
    Implementations1_2_1.updateCounterpartyImageUrl,
    Implementations1_2_1.deleteCounterpartyImageUrl,
    Implementations1_2_1.addCounterpartyOpenCorporatesUrl,
    Implementations1_2_1.updateCounterpartyOpenCorporatesUrl,
    Implementations1_2_1.deleteCounterpartyOpenCorporatesUrl,
    Implementations1_2_1.addCounterpartyCorporateLocation,
    Implementations1_2_1.updateCounterpartyCorporateLocation,
    Implementations1_2_1.deleteCounterpartyCorporateLocation,
    Implementations1_2_1.addCounterpartyPhysicalLocation,
    Implementations1_2_1.updateCounterpartyPhysicalLocation,
    Implementations1_2_1.deleteCounterpartyPhysicalLocation,
    Implementations1_2_1.getTransactionsForBankAccount,
    Implementations1_2_1.getTransactionByIdForBankAccount,
    Implementations1_2_1.getTransactionNarrative,
    Implementations1_2_1.addTransactionNarrative,
    Implementations1_2_1.updateTransactionNarrative,
    Implementations1_2_1.deleteTransactionNarrative,
    Implementations1_2_1.getCommentsForViewOnTransaction,
    Implementations1_2_1.addCommentForViewOnTransaction,
    Implementations1_2_1.deleteCommentForViewOnTransaction,
    Implementations1_2_1.getTagsForViewOnTransaction,
    Implementations1_2_1.addTagForViewOnTransaction,
    Implementations1_2_1.deleteTagForViewOnTransaction,
    Implementations1_2_1.getImagesForViewOnTransaction,
    Implementations1_2_1.addImageForViewOnTransaction,
    Implementations1_2_1.deleteImageForViewOnTransaction,
    Implementations1_2_1.getWhereTagForViewOnTransaction,
    Implementations1_2_1.addWhereTagForViewOnTransaction,
    Implementations1_2_1.updateWhereTagForViewOnTransaction,
    Implementations1_2_1.deleteWhereTagForViewOnTransaction,
    Implementations1_2_1.getCounterpartyForTransaction,
    Implementations1_2_1.makePayment,

    // New in 1.3.0
    Implementations1_3_0.getCards,
    Implementations1_3_0.getCardsForBank,

    // New in 1.4.0
    Implementations1_4_0.getCustomer,
    Implementations1_4_0.addCustomer,
    Implementations1_4_0.getCustomerMessages,
    Implementations1_4_0.addCustomerMessage,
    Implementations1_4_0.getBranches,
    Implementations1_4_0.getAtms,
    Implementations1_4_0.getProducts,
    Implementations1_4_0.getCrmEvents,
    // Now in 2.0.0 Implementations1_4_0.createTransactionRequest,
    // Now in 2.0.0 Implementations1_4_0.getTransactionRequests,
    Implementations1_4_0.getTransactionRequestTypes,

    // Updated in 2.0.0 (less info about the views)
    Implementations2_0_0.allAccountsAllBanks,
    Implementations2_0_0.privateAccountsAllBanks,
    Implementations2_0_0.publicAccountsAllBanks,
    Implementations2_0_0.allAccountsAtOneBank,
    Implementations2_0_0.privateAccountsAtOneBank,
    Implementations2_0_0.publicAccountsAtOneBank,
    Implementations2_0_0.createTransactionRequest,
    Implementations2_0_0.answerTransactionRequestChallenge,
    Implementations2_0_0.getTransactionRequests, // Now has charges information
    // Updated in 2.0.0 (added sorting and better guards / error messages)
    Implementations2_0_0.accountById,
    Implementations2_0_0.getPermissionsForBankAccount,
    Implementations2_0_0.getPermissionForUserForBankAccount,
    // New in 2.0.0
    Implementations2_0_0.getKycDocuments,
    Implementations2_0_0.getKycMedia,
    Implementations2_0_0.getKycStatuses,
    Implementations2_0_0.getKycChecks,
    Implementations2_0_0.getSocialMediaHandles,
    Implementations2_0_0.addKycDocument,
    Implementations2_0_0.addKycMedia,
    Implementations2_0_0.addKycStatus,
    Implementations2_0_0.addKycCheck,
    Implementations2_0_0.addSocialMediaHandle,
    Implementations2_0_0.getCoreAccountById,
    Implementations2_0_0.getCoreTransactionsForBankAccount,
    Implementations2_0_0.createAccount,
    Implementations2_0_0.getTransactionTypes,
    Implementations2_0_0.createUser,
    Implementations2_0_0.createMeeting,
    Implementations2_0_0.getMeetings,
    Implementations2_0_0.getMeeting,
    Implementations2_0_0.createCustomer,
    Implementations2_0_0.getCurrentUser,
    Implementations2_0_0.createUserCustomerLinks,
    Implementations2_0_0.addEntitlements,
    Implementations2_0_0.getEntitlements
  )

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

  if (Props.getBool("allow_elasticsearch", false)) {
    if (Props.getBool("allow_elasticsearch_warehouse", false)) {
      oauthServe(apiPrefix {
        Implementations2_0_0.elasticSearchWarehouse
      })
    }
    if (Props.getBool("allow_elasticsearch_metrics", false)) {
      oauthServe(apiPrefix {
        Implementations2_0_0.elasticSearchMetrics
      })
    }
  }


}
