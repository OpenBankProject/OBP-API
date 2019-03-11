package code.api.v1_3_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ApiVersion
import code.api.v1_2_1.APIMethods121
import code.util.Helper.MdcLoggable


// Added so we can add resource docs for this version of the API

//has APIMethods121 as all api calls that went unchanged from 1.2.1 to 1.3.0 will use the old
//implementation
object OBPAPI1_3_0 extends OBPRestHelper with APIMethods130 with APIMethods121 with MdcLoggable {

  val version : ApiVersion = ApiVersion.v1_3_0 //  "1.3.0"
  val versionStatus = "STABLE"

  //TODO: check all these calls to see if they should really have the same behaviour as 1.2.1

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

  val endpointsOf1_3_0 = List(
    Implementations1_3_0.getCards,
    Implementations1_3_0.getCardsForBank
  )

  val allResourceDocs =
    Implementations1_3_0.resourceDocs ++
      Implementations1_2_1.resourceDocs

  def findResourceDoc(pf: OBPEndpoint): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction==pf)
  }
  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] =
    List(Implementations1_2_1.root(version, versionStatus)) ::: // For now we make this mandatory
      getAllowedEndpoints(endpointsOf1_2_1, Implementations1_2_1.resourceDocs) :::
      getAllowedEndpoints(endpointsOf1_3_0, Implementations1_3_0.resourceDocs)


  routes.foreach(route => {
    oauthServe(apiPrefix{route}, findResourceDoc(route))
  })

  logger.info(s"version $version has been run! There are ${routes.length} routes.")

}
