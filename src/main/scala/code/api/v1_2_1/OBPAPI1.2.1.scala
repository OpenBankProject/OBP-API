/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

  */
package code.api.v1_2_1

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ApiVersion
import code.util.Helper.MdcLoggable

// Added so we can add resource docs for this version of the API

object OBPAPI1_2_1 extends OBPRestHelper with APIMethods121 with MdcLoggable {


  val version : ApiVersion = ApiVersion.v1_2_1  //    "1.2.1"
  val versionStatus = "STABLE"

  val endpointsOf1_2_1 = List(
    Implementations1_2_1.root(version, versionStatus),
    Implementations1_2_1.getBanks,
    Implementations1_2_1.bankById,
    Implementations1_2_1.getPrivateAccountsAllBanks,
    Implementations1_2_1.privateAccountsAllBanks,
    Implementations1_2_1.publicAccountsAllBanks,
    Implementations1_2_1.getPrivateAccountsAtOneBank,
    Implementations1_2_1.privateAccountsAtOneBank,
    Implementations1_2_1.publicAccountsAtOneBank,
    Implementations1_2_1.accountById,
    Implementations1_2_1.updateAccountLabel,
    Implementations1_2_1.getViewsForBankAccount,
    Implementations1_2_1.createViewForBankAccount,
    Implementations1_2_1.updateViewForBankAccount,
    Implementations1_2_1.deleteViewForBankAccount,
    Implementations1_2_1.getPermissionsForBankAccount,
    Implementations1_2_1.getPermissionForUserForBankAccount,
    Implementations1_2_1.addPermissionForUserForBankAccountForMultipleViews,
    Implementations1_2_1.addPermissionForUserForBankAccountForOneView,
    Implementations1_2_1.removePermissionForUserForBankAccountForOneView,
    Implementations1_2_1.removePermissionForUserForBankAccountForAllViews,
    Implementations1_2_1.getOtherAccountsForBankAccount,
    Implementations1_2_1.getOtherAccountByIdForBankAccount,
    Implementations1_2_1.getOtherAccountMetadata,
    Implementations1_2_1.getCounterpartyPublicAlias,
    Implementations1_2_1.addCounterpartyPublicAlias,
    Implementations1_2_1.updateCounterpartyPublicAlias,
    Implementations1_2_1.deleteCounterpartyPublicAlias,
    Implementations1_2_1.getOtherAccountPrivateAlias,
    Implementations1_2_1.addOtherAccountPrivateAlias,
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
    Implementations1_2_1.getOtherAccountForTransaction
    //Implementations1_2_1.makePayment
  )
  val allResourceDocs = Implementations1_2_1.resourceDocs

  def findResourceDoc(pf: OBPEndpoint): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction==pf)
  }

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] =
    List(Implementations1_2_1.root(version, versionStatus)) ::: // For now we make this mandatory
      getAllowedEndpoints(endpointsOf1_2_1, Implementations1_2_1.resourceDocs)


  routes.foreach(route => {
    oauthServe(apiPrefix{route}, findResourceDoc(route))
  })

  logger.info(s"version $version has been run! There are ${routes.length} routes.")




  //TODO: call for get more info of other bank account?
  //TODO: call for get url of other bank account?
  //TODO: call for get image url of other bank account?
  //TODO: call for get open corporates url of other bank account?
  //TODO: call for get corporate location of other bank account?
  //TODO: call for get physical location of other bank account?

}