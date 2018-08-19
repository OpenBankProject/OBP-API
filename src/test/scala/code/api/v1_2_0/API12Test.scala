///**
//Open Bank Project - API
//Copyright (C) 2011-2018, TESOBE Ltd
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//Email: contact@tesobe.com
//TESOBE Ltd
//Osloerstrasse 16/17
//Berlin 13359, Germany
//
//  This product includes software developed at
//  TESOBE (http://www.tesobe.com/)
//  by
//  Simon Redfern : simon AT tesobe DOT com
//  Stefan Bethge : stefan AT tesobe DOT com
//  Everett Sochowski : everett AT tesobe DOT com
//  Ayoub Benali: ayoub AT tesobe DOT com
//
// */
//package code.api.v1_2_0
//
//import java.util.Date
//
//import _root_.net.liftweb.json.JsonAST.JObject
//import _root_.net.liftweb.json.Serialization.write
//import _root_.net.liftweb.util._
//import code.api.util.APIUtil
//import code.api.util.APIUtil.OAuth._
//import code.api.v1_2._
//import code.model.{Consumer => OBPConsumer, Token => OBPToken, _}
//import code.setup.{APIResponse, DefaultUsers, User1AllPrivileges}
//import code.views.Views
//import net.liftweb.json.JsonDSL._
//import net.liftweb.util.Helpers._
//import org.scalatest._
//
//import scala.util.Random._
//
//
//class API1_2Test extends User1AllPrivileges with DefaultUsers {
//
//  def v1_2Request = baseRequest / "obp" / "v1.2"
//
//  val viewFileds = List(
//    "can_see_transaction_this_bank_account","can_see_transaction_other_bank_account",
//    "can_see_transaction_metadata","can_see_transaction_label","can_see_transaction_amount",
//    "can_see_transaction_type","can_see_transaction_currency","can_see_transaction_start_date",
//    "can_see_transaction_finish_date","can_see_transaction_balance","can_see_comments",
//    "can_see_narrative","can_see_tags","can_see_images","can_see_bank_account_owners",
//    "can_see_bank_account_type","can_see_bank_account_balance","can_see_bank_account_currency",
//    "can_see_bank_account_label","can_see_bank_account_national_identifier",
//    "can_see_bank_account_swift_bic","can_see_bank_account_iban","can_see_bank_account_number",
//    "can_see_bank_account_bank_name","can_see_other_account_national_identifier",
//    "can_see_other_account_swift_bic","can_see_other_account_iban",
//    "can_see_other_account_bank_name","can_see_other_account_number",
//    "can_see_other_account_metadata","can_see_other_account_kind","can_see_more_info",
//    "can_see_url","can_see_image_url","can_see_open_corporates_url","can_see_corporate_location",
//    "can_see_physical_location","can_see_public_alias","can_see_private_alias","can_add_more_info",
//    "can_add_url","can_add_image_url","can_add_open_corporates_url","can_add_corporate_location",
//    "can_add_physical_location","can_add_public_alias","can_add_private_alias",
//    "can_delete_corporate_location","can_delete_physical_location","can_edit_narrative",
//    "can_add_comment","can_delete_comment","can_add_tag","can_delete_tag","can_add_image",
//    "can_delete_image","can_add_where_tag","can_see_where_tag","can_delete_where_tag"
//    )
//
//  /************************* test tags ************************/
//
//  /**
//   * Example: To run tests with tag "getPermissions":
//   * 	mvn test -D tagsToInclude
//   *
//   *  This is made possible by the scalatest maven plugin
//   */
//
//  object CurrentTest extends Tag("currentScenario")
//  object API1_2 extends Tag("api1.2")
//  object APIInfo extends Tag("apiInfo")
//  object GetHostedBanks extends Tag("hostedBanks")
//  object GetHostedBank extends Tag("getHostedBank")
//  object GetBankAccounts extends Tag("getBankAccounts")
//  object GetPublicBankAccounts extends Tag("getPublicBankAccounts")
//  object GetPrivateBankAccounts extends Tag("getPrivateBankAccounts")
//  object GetBankAccount extends Tag("getBankAccount")
//  object GetViews extends Tag("getViews")
//  object PostView extends Tag("postView")
//  object PutView extends Tag("putView")
//  object DeleteView extends Tag("deleteView")
//  object GetPermissions extends Tag("getPermissions")
//  object GetPermission extends Tag("getPermission")
//  object PostPermission extends Tag("postPermission")
//  object PostPermissions extends Tag("postPermissions")
//  object DeletePermission extends Tag("deletePermission")
//  object DeletePermissions extends Tag("deletePermissions")
//  object GetOtherBankAccounts extends Tag("getOtherBankAccounts")
//  object GetOtherBankAccount extends Tag("getOtherBankAccount")
//  object GetOtherBankAccountMetadata extends Tag("getOtherBankAccountMetadata")
//  object GetPublicAlias extends Tag("getPublicAlias")
//  object PostPublicAlias extends Tag("postPublicAlias")
//  object PutPublicAlias extends Tag("putPublicAlias")
//  object DeletePublicAlias extends Tag("deletePublicAlias")
//  object GetPrivateAlias extends Tag("getPrivateAlias")
//  object PostPrivateAlias extends Tag("postPrivateAlias")
//  object PutPrivateAlias extends Tag("putPrivateAlias")
//  object DeletePrivateAlias extends Tag("deletePrivateAlias")
//  object PostMoreInfo extends Tag("postMoreInfo")
//  object PutMoreInfo extends Tag("putMoreInfo")
//  object DeleteMoreInfo extends Tag("deleteMoreInfo")
//  object PostURL extends Tag("postURL")
//  object PutURL extends Tag("putURL")
//  object DeleteURL extends Tag("deleteURL")
//  object PostImageURL extends Tag("postImageURL")
//  object PutImageURL extends Tag("putImageURL")
//  object DeleteImageURL extends Tag("DeleteImageURL")
//  object PostOpenCorporatesURL extends Tag("postOpenCorporatesURL")
//  object PutOpenCorporatesURL extends Tag("putOpenCorporatesURL")
//  object DeleteOpenCorporatesURL extends Tag("deleteOpenCorporatesURL")
//  object PostCorporateLocation extends Tag("postCorporateLocation")
//  object PutCorporateLocation extends Tag("putCorporateLocation")
//  object DeleteCorporateLocation extends Tag("deleteCorporateLocation")
//  object PostPhysicalLocation extends Tag("postPhysicalLocation")
//  object PutPhysicalLocation extends Tag("putPhysicalLocation")
//  object DeletePhysicalLocation extends Tag("deletePhysicalLocation")
//  object GetTransactions extends Tag("getTransactions")
//  object GetTransactionsWithParams extends Tag("getTransactionsWithParams")
//  object GetTransaction extends Tag("getTransaction")
//  object GetNarrative extends Tag("getNarrative")
//  object PostNarrative extends Tag("postNarrative")
//  object PutNarrative extends Tag("putNarrative")
//  object DeleteNarrative extends Tag("deleteNarrative")
//  object GetComments extends Tag("getComments")
//  object PostComment extends Tag("postComment")
//  object DeleteComment extends Tag("deleteComment")
//  object GetTags extends Tag("getTags")
//  object PostTag extends Tag("postTag")
//  object DeleteTag extends Tag("deleteTag")
//  object GetImages extends Tag("getImages")
//  object PostImage extends Tag("postImage")
//  object DeleteImage extends Tag("deleteImage")
//  object GetWhere extends Tag("getWhere")
//  object PostWhere extends Tag("postWhere")
//  object PutWhere extends Tag("putWhere")
//  object DeleteWhere extends Tag("deleteWhere")
//  object GetTransactionAccount extends Tag("getTransactionAccount")
//
//  /********************* API test methods ********************/
//
//  def randomViewPermalink(bankId: String, account: AccountJSON) : String = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
//    val reply = makeGetRequest(request)
//    val possibleViewsPermalinks = reply.body.extract[ViewsJSON].views.filterNot(_.is_public==true)
//    val randomPosition = nextInt(possibleViewsPermalinks.size)
//    possibleViewsPermalinks(randomPosition).id
//  }
//
//  def randomViewPermalinkButNotOwner(bankId: String, account: AccountJSON) : String = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
//    val reply = makeGetRequest(request)
//    val possibleViewsPermalinksWithoutOwner = reply.body.extract[ViewsJSON].views.filterNot(_.is_public==true).filterNot(_.id == "owner")
//    val randomPosition = nextInt(possibleViewsPermalinksWithoutOwner.size)
//    possibleViewsPermalinksWithoutOwner(randomPosition).id
//  }
//
//  def randomBank : String = {
//    val banksJson = getBanksInfo.body.extract[BanksJSON]
//    val randomPosition = nextInt(banksJson.banks.size)
//    val bank = banksJson.banks(randomPosition)
//    bank.id
//  }
//
//  def randomPublicAccount(bankId : String) : AccountJSON = {
//    val accountsJson = getPublicAccounts(bankId).body.extract[AccountsJSON].accounts
//    val randomPosition = nextInt(accountsJson.size)
//    accountsJson(randomPosition)
//  }
//
//  def randomPrivateAccount(bankId : String) : AccountJSON = {
//    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts
//    val randomPosition = nextInt(accountsJson.size)
//    accountsJson(randomPosition)
//  }
//
//  def randomAccountPermission(bankId : String, accountId : String) : PermissionJSON = {
//    val persmissionsInfo = getAccountPermissions(bankId, accountId, user1).body.extract[PermissionsJSON]
//    val randomPermission = nextInt(persmissionsInfo.permissions.size)
//    persmissionsInfo.permissions(randomPermission)
//  }
//
//  def randomOtherBankAccount(bankId : String, accountId : String, viewId : String): OtherAccountJSON = {
//    val otherAccounts = getTheOtherBankAccounts(bankId, accountId, viewId, user1).body.extract[OtherAccountsJSON].other_accounts
//    otherAccounts(nextInt(otherAccounts.size))
//  }
//
//  def randomLocation : LocationPlainJSON = {
//    def sign = {
//      val b = nextBoolean
//      if(b) 1
//      else -1
//    }
//    val longitude : Double = nextInt(180)*sign*nextDouble
//    val latitude : Double = nextInt(90)*sign*nextDouble
//    JSONFactory.createLocationPlainJSON(latitude, longitude)
//  }
//
//  def randomTransaction(bankId : String, accountId : String, viewId: String) : TransactionJSON = {
//    val transactionsJson = getTransactions(bankId, accountId, viewId, user1).body.extract[TransactionsJSON].transactions
//    val randomPosition = nextInt(transactionsJson.size)
//    transactionsJson(randomPosition)
//  }
//
//  def randomViewsIdsToGrant(bankId : String, accountId : String) : List[String]= {
//    //get the view ids of the available views on the bank accounts
//    val viewsIds = getAccountViews(bankId, accountId, user1).body.extract[ViewsJSON].views.map(_.id)
//    //choose randomly some view ids to grant
//    val (viewsIdsToGrant, _) = viewsIds.splitAt(nextInt(viewsIds.size) + 1)
//    viewsIdsToGrant
//  }
//
//  def randomView(isPublic: Boolean, alias: String) : CreateViewJson = {
//    CreateViewJson(
//      name = randomString(3),
//      description = randomString(3),
//      is_public = isPublic,
//      which_alias_to_use=alias,
//      hide_metadata_if_alias_used=false,
//      allowed_actions = viewFileds
//    )
//  }
//  def getAPIInfo : APIResponse = {
//    val request = v1_2Request
//    makeGetRequest(request)
//  }
//
//  def getBanksInfo : APIResponse  = {
//    val request = v1_2Request / "banks"
//    makeGetRequest(request)
//  }
//
//  def getBankInfo(bankId : String) : APIResponse  = {
//    val request = v1_2Request / "banks" / bankId
//    makeGetRequest(request)
//  }
//
//  def getPublicAccounts(bankId : String) : APIResponse= {
//    val request = v1_2Request / "banks" / bankId / "accounts" / "public"
//    makeGetRequest(request)
//  }
//
//  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getBankAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getPublicBankAccountDetails(bankId : String, accountId : String, viewId : String) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "account"
//    makeGetRequest(request)
//  }
//
//  def getPrivateBankAccountDetails(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "account" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getAccountViews(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
//    makePostRequest(request, write(view))
//  }
//
//  def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).PUT <@(consumerAndToken)
//    makePutRequest(request, write(view))
//  }
//
//  def deleteView(bankId: String, accountId: String, viewId: String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getAccountPermissions(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getUserAccountPermission(bankId : String, accountId : String, userId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def grantUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" / viewId).POST <@(consumerAndToken)
//    makePostRequest(request)
//  }
//
//  def grantUserAccessToViews(bankId : String, accountId : String, userId : String, viewIds : List[String], consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views").POST <@(consumerAndToken)
//    val viewsJson = ViewIdsJson(viewIds)
//    makePostRequest(request, write(viewsJson))
//  }
//
//  def revokeUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" / viewId).DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def revokeUserAccessToAllViews(bankId : String, accountId : String, userId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getTheOtherBankAccounts(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getTheOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getMetadataOfOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getThePublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postAPublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").POST <@(consumerAndToken)
//    val aliasJson = AliasJSON(alias)
//    makePostRequest(request, write(aliasJson))
//  }
//
//  def updateThePublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").PUT <@(consumerAndToken)
//    val aliasJson = AliasJSON(alias)
//    makePutRequest(request, write(aliasJson))
//  }
//
//  def deleteThePublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getThePrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postAPrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").POST <@(consumerAndToken)
//    val aliasJson = AliasJSON(alias)
//    makePostRequest(request, write(aliasJson))
//  }
//
//  def updateThePrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").PUT <@(consumerAndToken)
//    val aliasJson = AliasJSON(alias)
//    makePutRequest(request, write(aliasJson))
//  }
//
//  def deleteThePrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
//    getMetadataOfOneOtherBankAccount(bankId,accountId,viewId,otherBankAccountId,consumerAndToken).body.extract[OtherAccountMetadataJSON].more_info
//  }
//
//  def postMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").POST <@(consumerAndToken)
//    val moreInfoJson = MoreInfoJSON(moreInfo)
//    makePostRequest(request, write(moreInfoJson))
//  }
//
//  def updateMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").PUT <@(consumerAndToken)
//    val moreInfoJson = MoreInfoJSON(moreInfo)
//    makePutRequest(request, write(moreInfoJson))
//  }
//
//  def deleteMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
//    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId,consumerAndToken).body.extract[OtherAccountMetadataJSON].URL
//  }
//
//  def postUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").POST <@(consumerAndToken)
//    val urlJson = UrlJSON(url)
//    makePostRequest(request, write(urlJson))
//  }
//
//  def updateUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").PUT <@(consumerAndToken)
//    val urlJson = UrlJSON(url)
//    makePutRequest(request, write(urlJson))
//  }
//
//  def deleteUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
//    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId,consumerAndToken).body.extract[OtherAccountMetadataJSON].image_URL
//  }
//
//  def postImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").POST <@(consumerAndToken)
//    val imageUrlJson = ImageUrlJSON(imageUrl)
//    makePostRequest(request, write(imageUrlJson))
//  }
//
//  def updateImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").PUT <@(consumerAndToken)
//    val imageUrlJson = ImageUrlJSON(imageUrl)
//    makePutRequest(request, write(imageUrlJson))
//  }
//
//  def deleteImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
//    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId, consumerAndToken).body.extract[OtherAccountMetadataJSON].open_corporates_URL
//  }
//
//  def postOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").POST <@(consumerAndToken)
//    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
//    makePostRequest(request, write(openCorporateUrlJson))
//  }
//
//  def updateOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").PUT <@(consumerAndToken)
//    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
//    makePutRequest(request, write(openCorporateUrlJson))
//  }
//
//  def deleteOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : LocationJSON = {
//    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId, consumerAndToken).body.extract[OtherAccountMetadataJSON].corporate_location
//  }
//
//  def postCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").POST <@(consumerAndToken)
//    val corpLocationJson = CorporateLocationJSON(corporateLocation)
//    makePostRequest(request, write(corpLocationJson))
//  }
//
//  def updateCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").PUT <@(consumerAndToken)
//    val corpLocationJson = CorporateLocationJSON(corporateLocation)
//    makePutRequest(request, write(corpLocationJson))
//  }
//
//  def deleteCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getPhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : LocationJSON = {
//    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId, consumerAndToken).body.extract[OtherAccountMetadataJSON].physical_location
//  }
//
//  def postPhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").POST <@(consumerAndToken)
//    val physLocationJson = PhysicalLocationJSON(physicalLocation)
//    makePostRequest(request, write(physLocationJson))
//  }
//
//  def updatePhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)])  : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").PUT <@(consumerAndToken)
//    val physLocationJson = PhysicalLocationJSON(physicalLocation)
//    makePutRequest(request, write(physLocationJson))
//  }
//
//  def deletePhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getTransactions(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)], params: List[(String, String)] = Nil) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumerAndToken)
//    makeGetRequest(request, params)
//  }
//
//  def getTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "transaction" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def getNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").POST <@(consumerAndToken)
//    val narrativeJson = TransactionNarrativeJSON(narrative)
//    makePostRequest(request, write(narrativeJson))
//  }
//
//  def updateNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").PUT <@(consumerAndToken)
//    val narrativeJson = TransactionNarrativeJSON(narrative)
//    makePutRequest(request, write(narrativeJson))
//  }
//
//  def deleteNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getCommentsForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments").POST <@(consumerAndToken)
//    makePostRequest(request, write(comment))
//  }
//
//  def deleteCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, commentId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" / commentId).DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getTagsForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postTagForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, tag: PostTransactionTagJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags").POST <@(consumerAndToken)
//    makePostRequest(request, write(tag))
//  }
//
//  def deleteTagForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, tagId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" / tagId).DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getImagesForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postImageForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, image: PostTransactionImageJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images").POST <@(consumerAndToken)
//    makePostRequest(request, write(image))
//  }
//
//  def deleteImageForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, imageId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" / imageId).DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//  def postWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").POST <@(consumerAndToken)
//    val whereJson = PostTransactionWhereJSON(where)
//    makePostRequest(request, write(whereJson))
//  }
//
//  def updateWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").PUT <@(consumerAndToken)
//    val whereJson = PostTransactionWhereJSON(where)
//    makePutRequest(request, write(whereJson))
//  }
//
//  def deleteWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").DELETE <@(consumerAndToken)
//    makeDeleteRequest(request)
//  }
//
//  def getTheOtherBankAccountOfOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
//    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "other_account" <@(consumerAndToken)
//    makeGetRequest(request)
//  }
//
//
///************************ the tests ************************/
//  feature("base line URL works"){
//    scenario("we get the api information", API1_2, APIInfo) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getAPIInfo
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val apiInfo = reply.body.extract[APIInfoJSON]
//      apiInfo.version should equal ("v1.2")
///*      apiInfo.git_commit.nonEmpty should equal (true)*/
//    }
//  }
//
//  feature("Information about the hosted banks"){
//    scenario("we get the hosted banks information", API1_2, GetHostedBanks) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getBanksInfo
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val banksInfo = reply.body.extract[BanksJSON]
//      banksInfo.banks.foreach(b => {
//        b.id.nonEmpty should equal (true)
//      })
//    }
//  }
//
//  feature("Information about one hosted bank"){
//    scenario("we get the hosted bank information", API1_2, GetHostedBank) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getBankInfo(randomBank)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val bankInfo = reply.body.extract[BankJSON]
//      bankInfo.id.nonEmpty should equal (true)
//    }
//
//    scenario("we don't get the hosted bank information", API1_2, GetHostedBank) {
//      Given("We will not use an access token and request a random bankId")
//      When("the request is sent")
//      val reply = getBankInfo(randomString(5))
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  def assertViewExistsWithCondition(accJson: AccountsJSON, cond: ViewJSON => Boolean): Unit = {
//    val exists = accJson.accounts.exists(acc => acc.views_available.exists(cond))
//    exists should equal(true)
//  }
//
//  def assertAllAccountsHaveAViewWithCondition(accJson: AccountsJSON, cond: ViewJSON => Boolean): Unit = {
//    val forAll = accJson.accounts.forall(acc => acc.views_available.exists(cond))
//    forAll should equal(true)
//  }
//
//  def assertAccountsFromOneBank(accJson : AccountsJSON) : Unit = {
//    accJson.accounts.size should be > 0
//    val theBankId = accJson.accounts.head.bank_id
//    theBankId should not be ("")
//
//    accJson.accounts.foreach(acc => acc.bank_id should equal (theBankId))
//  }
//
//  def assertNoDuplicateAccounts(accJson : AccountsJSON) : Unit = {
//    //bankId : String, accountId: String
//    type AccountIdentifier = (String, String)
//    //unique accounts have unique bankId + accountId
//    val accountIdentifiers : Set[AccountIdentifier] = {
//      accJson.accounts.map(acc => (acc.bank_id, acc.id)).toSet
//    }
//    //if they are all unique, the set will contain the same number of elements as the list
//    accJson.accounts.size should equal(accountIdentifiers.size)
//  }
//
//  feature("Information about all the bank accounts for a single bank"){
//    scenario("we get only the public bank accounts", API1_2, GetBankAccounts) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getBankAccounts(randomBank, None)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val publicAccountsInfo = reply.body.extract[AccountsJSON]
//      And("some fields should not be empty")
//      publicAccountsInfo.accounts.foreach(a => {
//        a.id.nonEmpty should equal (true)
//        a.views_available.nonEmpty should equal (true)
//        a.views_available.foreach(
//          //check that all the views are public
//          v => v.is_public should equal (true)
//        )
//      })
//
//      And("The accounts are only from one bank")
//      assertAccountsFromOneBank(publicAccountsInfo)
//
//      And("There are no duplicate accounts")
//      assertNoDuplicateAccounts(publicAccountsInfo)
//    }
//    scenario("we get the bank accounts the user have access to", API1_2, GetBankAccounts) {
//      Given("We will use an access token")
//      When("the request is sent")
//      val reply = getBankAccounts(randomBank, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val accountsInfo = reply.body.extract[AccountsJSON]
//      And("some fields should not be empty")
//      accountsInfo.accounts.foreach(a => {
//        a.id.nonEmpty should equal (true)
//        a.views_available.nonEmpty should equal (true)
//      })
//
//      //Note: this API call is technically wrong, as it was originally intended to have returned
//      // public + private accounts when logged in, but actually returned only the accounts with
//      // more than public access. This test therefore does not test that condition as the v1.2 API
//      // call is being kept that way to avoid breaking any existing applications using it. This API
//      // call is fixed in v1.2.1
//      And("Some accounts should have private views")
//      assertViewExistsWithCondition(accountsInfo, !_.is_public)
//
//      And("The accounts are only from one bank")
//      assertAccountsFromOneBank(accountsInfo)
//
//      And("There are no duplicate accounts")
//      assertNoDuplicateAccounts(accountsInfo)
//    }
//  }
//
//  feature("Information about the public bank accounts for a single bank"){
//    scenario("we get the public bank accounts", API1_2, GetPublicBankAccounts) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getPublicAccounts(randomBank)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val publicAccountsInfo = reply.body.extract[AccountsJSON]
//      And("some fields should not be empty")
//      publicAccountsInfo.accounts.foreach(a => {
//        a.id.nonEmpty should equal (true)
//        a.views_available.nonEmpty should equal (true)
//        a.views_available.foreach(
//          //check that all the views are public
//          v => v.is_public should equal (true)
//        )
//      })
//
//      And("The accounts are only from one bank")
//      assertAccountsFromOneBank(publicAccountsInfo)
//
//      And("There are no duplicate accounts")
//      assertNoDuplicateAccounts(publicAccountsInfo)
//    }
//  }
//
//  feature("Information about the private bank accounts for a single bank"){
//    scenario("we get the private bank accounts", API1_2, GetPrivateBankAccounts) {
//      Given("We will use an access token")
//      When("the request is sent")
//      val reply = getPrivateAccounts(randomBank, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      And("some fields should not be empty")
//      val privateAccountsInfo = reply.body.extract[AccountsJSON]
//      privateAccountsInfo.accounts.foreach(a => {
//        a.id.nonEmpty should equal (true)
//        a.views_available.nonEmpty should equal (true)
//      })
//
//      And("All accounts should have at least one private view")
//      assertAllAccountsHaveAViewWithCondition(privateAccountsInfo, !_.is_public)
//
//      And("The accounts are only from one bank")
//      assertAccountsFromOneBank(privateAccountsInfo)
//
//      And("There are no duplicate accounts")
//      assertNoDuplicateAccounts(privateAccountsInfo)
//    }
//    scenario("we don't get the private bank accounts", API1_2, GetPrivateBankAccounts) {
//      Given("We will not use an access token")
//      When("the request is sent")
//      val reply = getPrivateAccounts(randomBank, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Information about a bank account"){
//    scenario("we get data without using an access token", API1_2, GetBankAccount) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPublicAccount(bankId)
//      val randomPosition = nextInt(bankAccount.views_available.size)
//      val view = bankAccount.views_available.toList(randomPosition)
//      When("the request is sent")
//      val reply = getPublicBankAccountDetails(bankId, bankAccount.id, view.id)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      And("some fields should not be empty")
//      val publicAccountDetails = reply.body.extract[ModeratedAccountJSON]
//      publicAccountDetails.id.nonEmpty should equal (true)
//      publicAccountDetails.bank_id.nonEmpty should equal (true)
//      publicAccountDetails.views_available.nonEmpty should equal (true)
//    }
//
//    scenario("we get data by using an access token", API1_2, GetBankAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val randomPosition = nextInt(bankAccount.views_available.size)
//      val view = bankAccount.views_available.toList(randomPosition)
//      When("the request is sent")
//      val reply = getPrivateBankAccountDetails(bankId, bankAccount.id, view.id, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val privateAccountDetails = reply.body.extract[ModeratedAccountJSON]
//      And("some fields should not be empty")
//      privateAccountDetails.id.nonEmpty should equal (true)
//      privateAccountDetails.bank_id.nonEmpty should equal (true)
//      privateAccountDetails.views_available.nonEmpty should equal (true)
//    }
//  }
//
//  feature("List of the views of specific bank account"){
//    scenario("We will get the list of the available views on a bank account", API1_2, GetViews) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getAccountViews(bankId, bankAccount.id, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      reply.body.extract[ViewsJSON]
//    }
//
//    scenario("We will not get the list of the available views on a bank account due to missing token", API1_2, GetViews) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getAccountViews(bankId, bankAccount.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("We will not get the list of the available views on a bank account due to insufficient privileges", API1_2, GetViews) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getAccountViews(bankId, bankAccount.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//  feature("Create a view on a bank account"){
//    scenario("we will create a view on a bank account", API1_2, PostView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val viewsBefore = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJSON].views
//      val view = randomView(true, "")
//      When("the request is sent")
//      val reply = postView(bankId, bankAccount.id, view, user1)
//      Then("we should get a 201 code")
//      reply.code should equal (201)
//      reply.body.extract[ViewJSON]
//      And("we should get a new view")
//      val viewsAfter = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJSON].views
//      viewsBefore.size should equal (viewsAfter.size -1)
//    }
//
//    scenario("We will not create a view on a bank account due to missing token", API1_2, PostView) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomView(true, "")
//      When("the request is sent")
//      val reply = postView(bankId, bankAccount.id, view, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("We will not create a view on a bank account due to insufficient privileges", API1_2, PostView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomView(true, "")
//      When("the request is sent")
//      val reply = postView(bankId, bankAccount.id, view, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("We will not create a view because the bank account does not exist", API1_2, PostView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val view = randomView(true, "")
//      When("the request is sent")
//      val reply = postView(bankId, randomString(3), view, user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("We will not create a view because the view already exists", API1_2, PostView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomView(true, "")
//      postView(bankId, bankAccount.id, view, user1)
//      When("the request is sent")
//      val reply = postView(bankId, bankAccount.id, view, user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Update a view on a bank account") {
//
//    val updatedViewDescription = "aloha"
//    val updatedAliasToUse = "public"
//    val allowedActions = List("can_see_images", "can_delete_comment")
//
//    def viewUpdateJson(originalView : ViewJSON) = {
//      //it's not perfect, assumes too much about originalView (i.e. randomView(true, ""))
//      new UpdateViewJSON(
//        description = updatedViewDescription,
//        is_public = !originalView.is_public,
//        which_alias_to_use = updatedAliasToUse,
//        hide_metadata_if_alias_used = !originalView.hide_metadata_if_alias,
//        allowed_actions = allowedActions
//      )
//    }
//
//    def someViewUpdateJson() = {
//      new UpdateViewJSON(
//        description = updatedViewDescription,
//        is_public = true,
//        which_alias_to_use = updatedAliasToUse,
//        hide_metadata_if_alias_used = true,
//        allowed_actions = allowedActions
//      )
//    }
//
//    scenario("we will update a view on a bank account", API1_2, PutView) {
//      Given("A view exists")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomView(true, "")
//      val creationReply = postView(bankId, bankAccount.id, view, user1)
//      creationReply.code should equal (201)
//      val createdView : ViewJSON = creationReply.body.extract[ViewJSON]
//      createdView.can_see_images should equal(true)
//      createdView.can_delete_comment should equal(true)
//      createdView.can_delete_physical_location should equal(true)
//      createdView.can_edit_owner_comment should equal(true)
//      createdView.description should not equal(updatedViewDescription)
//      createdView.is_public should equal(true)
//      createdView.hide_metadata_if_alias should equal(false)
//
//      When("We use a valid access token and valid put json")
//      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), user1)
//      Then("We should get back the updated view")
//      reply.code should equal (200)
//      val updatedView = reply.body.extract[ViewJSON]
//      updatedView.can_see_images should equal(true)
//      updatedView.can_delete_comment should equal(true)
//      updatedView.can_delete_physical_location should equal(false)
//      updatedView.can_edit_owner_comment should equal(false)
//      updatedView.description should equal(updatedViewDescription)
//      updatedView.is_public should equal(false)
//      updatedView.hide_metadata_if_alias should equal(true)
//    }
//
//    scenario("we will not update a view that doesn't exist", API1_2, PutView) {
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//
//      Given("a view does not exist")
//      val nonExistantViewId = "asdfasdfasdfasdfasdf"
//      val getReply = getAccountViews(bankId, bankAccount.id, user1)
//      getReply.code should equal (200)
//      val views : ViewsJSON = getReply.body.extract[ViewsJSON]
//      views.views.foreach(v => v.id should not equal(nonExistantViewId))
//
//      When("we try to update that view")
//      val reply = putView(bankId, bankAccount.id, nonExistantViewId, someViewUpdateJson(), user1)
//      Then("We should get a 404")
//      reply.code should equal(404)
//    }
//
//    scenario("We will not update a view on a bank account due to missing token", API1_2, PutView) {
//      Given("A view exists")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomView(true, "")
//      val creationReply = postView(bankId, bankAccount.id, view, user1)
//      creationReply.code should equal (201)
//      val createdView : ViewJSON = creationReply.body.extract[ViewJSON]
//
//      When("we don't use an access token")
//      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), None)
//      Then("we should get a 400")
//      reply.code should equal(400)
//
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update a view on a bank account due to insufficient privileges", API1_2, PutView) {
//      Given("A view exists")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomView(true, "")
//      val creationReply = postView(bankId, bankAccount.id, view, user1)
//      creationReply.code should equal (201)
//      val createdView : ViewJSON = creationReply.body.extract[ViewJSON]
//
//      When("we try to update a view without having sufficient privileges to do so")
//      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), user3)
//      Then("we should get a 400")
//      reply.code should equal(400)
//
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  //TODO: no get view call? just get views?
//
//  feature("Delete a view on a bank account"){
//    scenario("we will delete a view on a bank account", API1_2, DeleteView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = postView(bankId, bankAccount.id, randomView(true, ""), user1).body.extract[ViewJSON]
//      val viewsBefore = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJSON].views
//      When("the request is sent")
//      val reply = deleteView(bankId, bankAccount.id, view.id, user1)
//      Then("we should get a 204 code")
//      reply.code should equal (204)
//      And("the views should be updated")
//      val viewsAfter = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJSON].views
//      viewsBefore.size should equal (viewsAfter.size +1)
//    }
//
//    scenario("We will not delete a view on a bank account due to missing token", API1_2, DeleteView) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = deleteView(bankId, bankAccount.id, view, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("We will not delete a view on a bank account due to insufficient privileges", API1_2, DeleteView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = deleteView(bankId, bankAccount.id, view, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("We will not delete a view on a bank account because it does not exist", API1_2, PostView) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = deleteView(bankId, bankAccount.id, randomString(3), user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Information about the permissions of a specific bank account"){
//    scenario("we will get one bank account permissions by using an access token", API1_2, GetPermissions) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getAccountPermissions(bankId, bankAccount.id, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      reply.body.extract[PermissionsJSON]
//
//      val permissions = reply.body.extract[PermissionsJSON]
//
//      def stringNotEmpty(s : String) {
//        s should not equal null
//        s should not equal ""
//      }
//
//      for {
//        permission <- permissions.permissions
//      } {
//        val user = permission.user
//
//        //TODO: Need to come up with a better way to check that information is not missing
//        // idea: reflection on all the json case classes, marking "required" information with annotations
//        stringNotEmpty(user.id)
//        stringNotEmpty(user.provider)
//
//        for {
//          view <- permission.views
//        } {
//           stringNotEmpty(view.id)
//        }
//      }
//    }
//
//    scenario("we will not get one bank account permissions", API1_2, GetPermissions) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getAccountPermissions(bankId, bankAccount.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get one bank account permissions by using an other access token", API1_2, GetPermissions) {
//      Given("We will use an access token, but that does not grant owner view")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getAccountPermissions(bankId, bankAccount.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Information about the permissions of a specific user on a specific bank account"){
//    scenario("we will get the permissions by using an access token", API1_2, GetPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val permission = randomAccountPermission(bankId, bankAccount.id)
//      val userID = permission.user.id
//      When("the request is sent")
//      val reply = getUserAccountPermission(bankId, bankAccount.id, userID, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val viewsInfo = reply.body.extract[ViewsJSON]
//      And("some fields should not be empty")
//      viewsInfo.views.foreach(v => v.id.nonEmpty should equal (true))
//    }
//
//    scenario("we will not get the permissions of a specific user", API1_2, GetPermission) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val permission = randomAccountPermission(bankId, bankAccount.id)
//      val userID = permission.user.id
//      When("the request is sent")
//      val reply = getUserAccountPermission(bankId, bankAccount.id, userID, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the permissions of a random user", API1_2, GetPermission) {
//      Given("We will use an access token with random user id")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getUserAccountPermission(bankId, bankAccount.id, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Grant a user access to a view on a bank account"){
//    scenario("we will grant a user access to a view on an bank account", API1_2, PostPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val userId = resourceUser2.idGivenByProvider
//      val reply = grantUserAccessToView(bankId, bankAccount.id, userId, randomViewPermalink(bankId, bankAccount), user1)
//      Then("we should get a 201 ok code")
//      reply.code should equal (201)
//      val viewInfo = reply.body.extract[ViewJSON]
//      And("some fields should not be empty")
//      viewInfo.id.nonEmpty should equal (true)
//    }
//
//    scenario("we cannot grant a user access to a view on an bank account because the user does not exist", API1_2, PostPermission) {
//      Given("We will use an access token with a random user Id")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = grantUserAccessToView(bankId, bankAccount.id, randomString(5), randomViewPermalink(bankId, bankAccount), user1)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we cannot grant a user access to a view on an bank account because the view does not exist", API1_2, PostPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser2.idGivenByProvider
//      When("the request is sent")
//      val reply = grantUserAccessToView(bankId, bankAccount.id, userId, randomString(5), user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we cannot grant a user access to a view on an bank account because the user does not have owner view access", API1_2, PostPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser2.idGivenByProvider
//      When("the request is sent")
//      val reply = grantUserAccessToView(bankId, bankAccount.id, userId, randomViewPermalink(bankId, bankAccount), user3)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Grant a user access to a list of views on a bank account"){
//    scenario("we will grant a user access to a list of views on an bank account", API1_2, PostPermissions) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser3.idGivenByProvider
//      val viewsIdsToGrant = randomViewsIdsToGrant(bankId, bankAccount.id)
//      When("the request is sent")
//      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      Then("we should get a 201 ok code")
//      reply.code should equal (201)
//      val viewsInfo = reply.body.extract[ViewsJSON]
//      And("some fields should not be empty")
//      viewsInfo.views.foreach(v => v.id.nonEmpty should equal (true))
//      And("the granted views should be the same")
//      viewsIdsToGrant.toSet should equal(viewsInfo.views.map(_.id).toSet)
//      //we revoke access to the granted views for the next tests
//      revokeUserAccessToAllViews(bankId, bankAccount.id, userId, user1)
//    }
//
//    scenario("we cannot grant a user access to a list of views on an bank account because the user does not exist", API1_2, PostPermissions) {
//      Given("We will use an access token with a random user Id")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = randomString(5)
//      val viewsIdsToGrant= randomViewsIdsToGrant(bankId, bankAccount.id)
//      When("the request is sent")
//      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we cannot grant a user access to a list of views on an bank account because they don't exist", API1_2, PostPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser3.idGivenByProvider
//      val viewsIdsToGrant= List(randomString(3),randomString(3))
//      When("the request is sent")
//      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we cannot grant a user access to a list of views on an bank account because some views don't exist", API1_2, PostPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser3.idGivenByProvider
//      val viewsIdsToGrant= randomViewsIdsToGrant(bankId, bankAccount.id) ++ List(randomString(3),randomString(3))
//      When("the request is sent")
//      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we cannot grant a user access to a list of views on an bank account because the user does not have owner view access", API1_2, PostPermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser3.idGivenByProvider
//      val viewsIdsToGrant= randomViewsIdsToGrant(bankId, bankAccount.id) ++ List(randomString(3),randomString(3))
//      When("the request is sent")
//      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user3)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("Revoke a user access to a view on a bank account"){
//    scenario("we will revoke the access of a user to a view different from owner on an bank account", API1_2, DeletePermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser2.idGivenByProvider
//      val viewId = randomViewPermalinkButNotOwner(bankId, bankAccount)
//      val viewsIdsToGrant = viewId :: Nil
//      grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      val viewsBefore = getUserAccountPermission(bankId, bankAccount.id, userId, user1).body.extract[ViewsJSON].views.length
//      When("the request is sent")
//      val reply = revokeUserAccessToView(bankId, bankAccount.id, userId, viewId, user1)
//      Then("we should get a 204 no content code")
//      reply.code should equal (204)
//      val viewsAfter = getUserAccountPermission(bankId, bankAccount.id, userId, user1).body.extract[ViewsJSON].views.length
//      viewsAfter should equal(viewsBefore -1)
//    }
//
//    scenario("we will revoke the access of a user to owner view on an bank account if there is more than one user", API1_2, DeletePermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val viewId = "owner"
//      val userId1 = resourceUser2.idGivenByProvider
//      val userId2 = resourceUser2.idGivenByProvider
//      grantUserAccessToView(bankId, bankAccount.id, userId1, viewId, user1)
//      grantUserAccessToView(bankId, bankAccount.id, userId2, viewId, user1)
//      val viewsBefore = getUserAccountPermission(bankId, bankAccount.id, userId1, user1).body.extract[ViewsJSON].views.length
//      When("the request is sent")
//      val reply = revokeUserAccessToView(bankId, bankAccount.id, userId1, viewId, user1)
//      Then("we should get a 204 no content code")
//      reply.code should equal (204)
//      val viewsAfter = getUserAccountPermission(bankId, bankAccount.id, userId1, user1).body.extract[ViewsJSON].views.length
//      viewsAfter should equal(viewsBefore -1)
//    }
//
//    scenario("we cannot revoke the access of a user to owner view on an bank account if there is only one user", API1_2, DeletePermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val viewId = ViewId("owner")
//      val view = Views.views.vend.view(ViewIdBankIdAccountId(viewId, BankId(bankId), AccountId(bankAccount.id))).get
//      if(Views.views.vend.getOwners(view).toList.length == 0){
//        val userId = resourceUser2.idGivenByProvider
//        grantUserAccessToView(bankId, bankAccount.id, userId, viewId.value, user1)
//      }
//      while(Views.views.vend.getOwners(view).toList.length > 1){
//        revokeUserAccessToView(bankId, bankAccount.id, Views.views.vend.getOwners(view).toList(0).idGivenByProvider, viewId.value, user1)
//      }
//      val viewUsersBefore = Views.views.vend.getOwners(view).toList
//      When("the request is sent")
//      val reply = revokeUserAccessToView(bankId, bankAccount.id, viewUsersBefore(0).idGivenByProvider, viewId.value, user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      val viewUsersAfter = Views.views.vend.getOwners(view).toList
//      viewUsersAfter.length should equal(viewUsersBefore.length)
//    }
//
//    scenario("we cannot revoke the access to a user that does not exist", API1_2, DeletePermission) {
//      Given("We will use an access token with a random user Id")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = revokeUserAccessToView(bankId, bankAccount.id, randomString(5), randomViewPermalink(bankId, bankAccount), user1)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//    }
//
//    scenario("we cannot revoke a user access to a view on an bank account because the view does not exist", API1_2, DeletePermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId =resourceUser2.idGivenByProvider
//      When("the request is sent")
//      val reply = revokeUserAccessToView(bankId, bankAccount.id, userId, randomString(5), user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//    }
//
//    scenario("we cannot revoke a user access to a view on an bank account because the user does not have owner view access", API1_2, DeletePermission) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser2.idGivenByProvider
//      When("the request is sent")
//      val reply = revokeUserAccessToView(bankId, bankAccount.id, userId, randomViewPermalink(bankId, bankAccount), user3)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//    }
//  }
//
//  feature("Revoke a user access to all the views on a bank account"){
//    scenario("we will revoke the access of a user to all the views on an bank account", API1_2, DeletePermissions) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser2.idGivenByProvider
//      val viewId = randomViewPermalink(bankId, bankAccount)
//      val viewsIdsToGrant = viewId :: Nil
//      grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      When("the request is sent")
//      val reply = revokeUserAccessToAllViews(bankId, bankAccount.id, userId, user1)
//      Then("we should get a 204 no content code")
//      reply.code should equal (204)
//    }
//    scenario("we cannot revoke the access to a user that does not exist", API1_2, DeletePermissions) {
//      Given("We will use an access token with a random user Id")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = revokeUserAccessToAllViews(bankId, bankAccount.id, randomString(5), user1)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//    }
//
//    scenario("we cannot revoke a user access to a view on an bank account because the user does not have owner view access", API1_2, DeletePermissions) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val userId = resourceUser2.idGivenByProvider
//      val viewId = randomViewPermalink(bankId, bankAccount)
//      val viewsIdsToGrant = viewId :: Nil
//      grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant, user1)
//      When("the request is sent")
//      val reply = revokeUserAccessToAllViews(bankId, bankAccount.id, userId, user3)
//      Then("we should get a 400 ok code")
//      reply.code should equal (400)
//    }
//  }
//
//  feature("We get the list of the other bank accounts linked with a bank account"){
//    scenario("we will get the other bank accounts of a bank account", API1_2, GetOtherBankAccounts) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getTheOtherBankAccounts(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount), user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      val accountsJson = reply.body.extract[OtherAccountsJSON]
//      And("some fields should not be empty")
//      accountsJson.other_accounts.foreach( a =>
//        a.id.nonEmpty should equal (true)
//      )
//    }
//
//    scenario("we will not get the other bank accounts of a bank account due to missing access token", API1_2, GetOtherBankAccounts) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getTheOtherBankAccounts(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount), None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the other bank accounts of a bank account because the user does not have enough privileges", API1_2, GetOtherBankAccounts) {
//      Given("We will use an access token ")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getTheOtherBankAccounts(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount), user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the other bank accounts of a bank account because the view does not exist", API1_2, GetOtherBankAccounts) {
//      Given("We will use an access token ")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      When("the request is sent")
//      val reply = getTheOtherBankAccounts(bankId, bankAccount.id, randomString(5), user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We get one specific other bank account among the other accounts "){
//    scenario("we will get one random other bank account of a bank account", API1_2, GetOtherBankAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      val accountJson = reply.body.extract[OtherAccountJSON]
//      And("some fields should not be empty")
//      accountJson.id.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get one random other bank account of a bank account due to a missing token", API1_2, GetOtherBankAccount) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get one random other bank account of a bank account because the user does not have enough privileges", API1_2, GetOtherBankAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get one random other bank account of a bank account because the view does not exist", API1_2, GetOtherBankAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount))
//      When("the request is sent")
//      val reply = getTheOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get one random other bank account of a bank account because the account does not exist", API1_2, GetOtherBankAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We get the metadata of one specific other bank account among the other accounts"){
//    scenario("we will get the metadata of one random other bank account", API1_2, GetOtherBankAccountMetadata) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("some fields should not be empty")
//      reply.body.extract[OtherAccountMetadataJSON]
//    }
//
//    scenario("we will not get the metadata of one random other bank account due to a missing token", API1_2, GetOtherBankAccountMetadata) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the metadata of one random other bank account because the user does not have enough privileges", API1_2, GetOtherBankAccountMetadata) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the metadata of one random other bank account because the view does not exist", API1_2, GetOtherBankAccountMetadata) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the metadata of one random other bank account because the account does not exist", API1_2, GetOtherBankAccountMetadata) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We get the public alias of one specific other bank account among the other accounts "){
//    scenario("we will get the public alias of one random other bank account", API1_2, GetPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      reply.body.extract[AliasJSON]
//    }
//
//    scenario("we will not get the public alias of one random other bank account due to a missing token", API1_2, GetPublicAlias) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the public alias of one random other bank account because the user does not have enough privileges", API1_2, GetPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the public alias of one random other bank account because the view does not exist", API1_2, GetPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the public alias of one random other bank account because the account does not exist", API1_2, GetPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post a public alias for one specific other bank"){
//    scenario("we will post a public alias for one random other bank account", API1_2, PostPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomAlias = randomString(5)
//      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the alias should be changed")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a public alias for a random other bank account due to a missing token", API1_2, PostPublicAlias) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a public alias for a random other bank account because the user does not have enough privileges", API1_2, PostPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a public alias for a random other bank account because the view does not exist", API1_2, PostPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomAlias, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a public alias for a random other bank account because the account does not exist", API1_2, PostPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the public alias for one specific other bank"){
//    scenario("we will update the public alias for one random other bank account", API1_2, PutPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomAlias = randomString(5)
//      val putReply = updateThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the alias should be changed")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not update the public alias for a random other bank account due to a missing token", API1_2, PutPublicAlias) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val putReply = updateThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not update the public alias for a random other bank account because the user does not have enough privileges", API1_2, PutPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val putReply = updateThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the public alias for a random other bank account because the account does not exist", API1_2, PutPublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val putReply = updateThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the public alias for one specific other bank"){
//    scenario("we will delete the public alias for one random other bank account", API1_2, DeletePublicAlias) {
//      Given("We will use an access token and will set an alias first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the public alias should be null")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
//      theAliasAfterTheDelete.alias should equal (null)
//    }
//    scenario("we will not delete the public alias for a random other bank account due to a missing token", API1_2, DeletePublicAlias) {
//      Given("We will not use an access token and will set an alias first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the public alias should not be null")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
//      theAliasAfterTheDelete.alias should not equal (null)
//    }
//    scenario("we will not delete the public alias for a random other bank account because the user does not have enough privileges", API1_2, DeletePublicAlias) {
//      Given("We will use an access token and will set an alias first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the public alias should not be null")
//      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
//      theAliasAfterTheDelete.alias should not equal (null)
//    }
//    scenario("we will not delete the public alias for a random other bank account because the account does not exist", API1_2, DeletePublicAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomAlias = randomString(5)
//      When("the delete request is sent")
//      val deleteReply = deleteThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We get the private alias of one specific other bank account among the other accounts "){
//    scenario("we will get the private alias of one random other bank account", API1_2, GetPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      reply.body.extract[AliasJSON]
//    }
//
//    scenario("we will not get the private alias of one random other bank account due to a missing token", API1_2, GetPrivateAlias) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the private alias of one random other bank account because the user does not have enough privileges", API1_2, GetPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the private alias of one random other bank account because the view does not exist", API1_2, GetPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the private alias of one random other bank account because the account does not exist", API1_2, GetPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//
//      When("the request is sent")
//      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post a private alias for one specific other bank"){
//    scenario("we will post a private alias for one random other bank account", API1_2, PostPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomAlias = randomString(5)
//      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the alias should be changed")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a private alias for a random other bank account due to a missing token", API1_2, PostPrivateAlias) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a private alias for a random other bank account because the user does not have enough privileges", API1_2, PostPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a private alias for a random other bank account because the view does not exist", API1_2, PostPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomAlias, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not post a private alias for a random other bank account because the account does not exist", API1_2, PostPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the private alias for one specific other bank"){
//    scenario("we will update the private alias for one random other bank account", API1_2, PutPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomAlias = randomString(5)
//      val putReply = updateThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the alias should be changed")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not update the private alias for a random other bank account due to a missing token", API1_2, PutPrivateAlias) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val putReply = updateThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the alias should not be changed")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
//      randomAlias should not equal (theAliasAfterThePost.alias)
//    }
//
//    scenario("we will not update the private alias for a random other bank account because the user does not have enough privileges", API1_2, PutPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val putReply = updateThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the private alias for a random other bank account because the account does not exist", API1_2, PutPrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomAlias = randomString(5)
//      When("the request is sent")
//      val putReply = updateThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the private alias for one specific other bank"){
//    scenario("we will delete the private alias for one random other bank account", API1_2, DeletePrivateAlias) {
//      Given("We will use an access token and will set an alias first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the Private alias should be null")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
//      theAliasAfterTheDelete.alias should equal (null)
//    }
//    scenario("we will not delete the private alias for a random other bank account due to a missing token", API1_2, DeletePrivateAlias) {
//      Given("We will not use an access token and will set an alias first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the Private alias should not be null")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
//      theAliasAfterTheDelete.alias should not equal (null)
//    }
//    scenario("we will not delete the private alias for a random other bank account because the user does not have enough privileges", API1_2, DeletePrivateAlias) {
//      Given("We will use an access token and will set an alias first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomAlias = randomString(5)
//      postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the Private alias should not be null")
//      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
//      theAliasAfterTheDelete.alias should not equal (null)
//    }
//    scenario("we will not delete the private alias for a random other bank account because the account does not exist", API1_2, DeletePrivateAlias) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomAlias = randomString(5)
//      When("the delete request is sent")
//      val deleteReply = deleteThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We post more information for one specific other bank"){
//    scenario("we will post more information for one random other bank account", API1_2, PostMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomInfo = randomString(20)
//      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the information should be changed")
//      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomInfo should equal (moreInfo)
//    }
//
//    scenario("we will not post more information for a random other bank account due to a missing token", API1_2, PostMoreInfo) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the information should not be changed")
//      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomInfo should not equal (moreInfo)
//    }
//
//    scenario("we will not post more information for a random other bank account because the user does not have enough privileges", API1_2, PostMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the information should not be changed")
//      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomInfo should not equal (moreInfo)
//    }
//
//    scenario("we will not post more information for a random other bank account because the view does not exist", API1_2, PostMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomInfo, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the information should not be changed")
//      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomInfo should not equal (moreInfo)
//    }
//
//    scenario("we will not post more information for a random other bank account because the account does not exist", API1_2, PostMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomInfo, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the information for one specific other bank"){
//    scenario("we will update the information for one random other bank account", API1_2, PutMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomInfo = randomString(20)
//      val putReply = updateMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the information should be changed")
//      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomInfo should equal (moreInfo)
//    }
//
//    scenario("we will not update the information for a random other bank account due to a missing token", API1_2, PutMoreInfo) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val putReply = updateMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the information should not be changed")
//      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomInfo should not equal (moreInfo)
//    }
//
//    scenario("we will not update the information for a random other bank account because the user does not have enough privileges", API1_2, PutMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val putReply = updateMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the information for a random other bank account because the account does not exist", API1_2, PutMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomInfo = randomString(20)
//      When("the request is sent")
//      val putReply = updateMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomInfo, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
// feature("We delete the information for one specific other bank"){
//    scenario("we will delete the information for one random other bank account", API1_2, DeleteMoreInfo) {
//      Given("We will use an access token and will set an info first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the info should be null")
//      val infoAfterDelete = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      infoAfterDelete should equal (null)
//    }
//
//    scenario("we will not delete the information for a random other bank account due to a missing token", API1_2, DeleteMoreInfo) {
//      Given("We will not use an access token and will set an info first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the info should not be null")
//      val infoAfterDelete = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      infoAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the information for a random other bank account because the user does not have enough privileges", API1_2, DeleteMoreInfo) {
//      Given("We will use an access token and will set an info first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomInfo = randomString(20)
//      postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the info should not be null")
//      val infoAfterDelete = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      infoAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the information for a random other bank account because the account does not exist", API1_2, DeleteMoreInfo) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomInfo = randomString(20)
//      When("the delete request is sent")
//      val deleteReply = deleteMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We post the url for one specific other bank"){
//    scenario("we will post the url for one random other bank account", API1_2, PostURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomURL = randomString(20)
//      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the url should be changed")
//      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should equal (url)
//    }
//
//    scenario("we will not post the url for a random other bank account due to a missing token", API1_2, PostURL) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the url should not be changed")
//      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not post the url for a random other bank account because the user does not have enough privileges", API1_2, PostURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the url should not be changed")
//      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not post the url for a random other bank account because the view does not exist", API1_2, PostURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomURL, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the url should not be changed")
//      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not post the url for a random other bank account because the account does not exist", API1_2, PostURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the url for one specific other bank"){
//    scenario("we will update the url for one random other bank account", API1_2, PutURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomURL = randomString(20)
//      val putReply = updateUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the url should be changed")
//      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should equal (url)
//    }
//
//    scenario("we will not update the url for a random other bank account due to a missing token", API1_2, PutURL) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the url should not be changed")
//      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not update the url for a random other bank account because the user does not have enough privileges", API1_2, PutURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the url for a random other bank account because the account does not exist", API1_2, PutURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the url for one specific other bank"){
//    scenario("we will delete the url for one random other bank account", API1_2, DeleteURL) {
//      Given("We will use an access token and will set an open corporates url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the url should be null")
//      val urlAfterDelete = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should equal (null)
//    }
//
//    scenario("we will not delete the url for a random other bank account due to a missing token", API1_2, DeleteURL) {
//      Given("We will not use an access token and will set an open corporates url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the url should not be null")
//      val urlAfterDelete = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the url for a random other bank account because the user does not have enough privileges", API1_2, DeleteURL) {
//      Given("We will use an access token and will set an open corporates url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the url should not be null")
//      val urlAfterDelete = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the url for a random other bank account because the account does not exist", API1_2, DeleteURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomURL = randomString(20)
//      When("the delete request is sent")
//      val deleteReply = deleteUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We post the image url for one specific other bank"){
//    scenario("we will post the image url for one random other bank account", API1_2, PostImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomImageURL = randomString(20)
//      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the image url should be changed")
//      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomImageURL should equal (url)
//    }
//
//    scenario("we will not post the image url for a random other bank account due to a missing token", API1_2, PostImageURL) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image url should not be changed")
//      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomImageURL should not equal (url)
//    }
//
//    scenario("we will not post the image url for a random other bank account because the user does not have enough privileges", API1_2, PostImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image url should not be changed")
//      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomImageURL should not equal (url)
//    }
//
//    scenario("we will not post the image url for a random other bank account because the view does not exist", API1_2, PostImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomImageURL, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image url should not be changed")
//      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomImageURL should not equal (url)
//    }
//
//    scenario("we will not post the image url for a random other bank account because the account does not exist", API1_2, PostImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomImageURL, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the image url for one specific other bank"){
//    scenario("we will update the image url for one random other bank account", API1_2, PutImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomImageURL = randomString(20)
//      val putReply = updateImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the image url should be changed")
//      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomImageURL should equal (url)
//    }
//
//    scenario("we will not update the image url for a random other bank account due to a missing token", API1_2, PutImageURL) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image url should not be changed")
//      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomImageURL should not equal (url)
//    }
//
//    scenario("we will not update the image url for a random other bank account because the user does not have enough privileges", API1_2, PutImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the image url for a random other bank account because the account does not exist", API1_2, PutImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomImageURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomImageURL, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the image url for one specific other bank"){
//    scenario("we will delete the image url for one random other bank account", API1_2, DeleteImageURL) {
//      Given("We will use an access token and will set a url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the image url should be null")
//      val urlAfterDelete = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should equal (null)
//    }
//
//    scenario("we will not delete the image url for a random other bank account due to a missing token", API1_2, DeleteImageURL) {
//      Given("We will not use an access token and will set a url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the image url should not be null")
//      val urlAfterDelete = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the image url for a random other bank account because the user does not have enough privileges", API1_2, DeleteImageURL) {
//      Given("We will use an access token and will set a url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomImageURL = randomString(20)
//      postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the image url should not be null")
//      val urlAfterDelete = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the image url for a random other bank account because the account does not exist", API1_2, DeleteImageURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomImageURL = randomString(20)
//      When("the delete request is sent")
//      val deleteReply = deleteImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We post the open corporates url for one specific other bank"){
//    scenario("we will post the open corporates url for one random other bank account", API1_2, PostOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomURL = randomString(20)
//      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the open corporates url should be changed")
//      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should equal (url)
//    }
//
//    scenario("we will not post the open corporates url for a random other bank account due to a missing token", API1_2, PostOpenCorporatesURL) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the open corporates url should not be changed")
//      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not post the open corporates url for a random other bank account because the user does not have enough privileges", API1_2, PostOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the open corporates url should not be changed")
//      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not post the open corporates url for a random other bank account because the view does not exist", API1_2, PostOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomURL, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the open corporates url should not be changed")
//      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not post the open corporates url for a random other bank account because the account does not exist", API1_2, PostOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the open corporates url for one specific other bank"){
//    scenario("we will update the open corporates url for one random other bank account", API1_2, PutOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomURL = randomString(20)
//      val putReply = updateOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the open corporates url should be changed")
//      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should equal (url)
//    }
//
//    scenario("we will not update the open corporates url for a random other bank account due to a missing token", API1_2, PutOpenCorporatesURL) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the open corporates url should not be changed")
//      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomURL should not equal (url)
//    }
//
//    scenario("we will not update the open corporates url for a random other bank account because the user does not have enough privileges", API1_2, PutOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the open corporates url for a random other bank account because the account does not exist", API1_2, PutOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomURL = randomString(20)
//      When("the request is sent")
//      val putReply = updateOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the open corporates url for one specific other bank"){
//    scenario("we will delete the open corporates url for one random other bank account", API1_2, DeleteOpenCorporatesURL) {
//      Given("We will use an access token and will set an open corporates url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the open corporates url should be null")
//      val urlAfterDelete = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should equal (null)
//    }
//
//    scenario("we will not delete the open corporates url for a random other bank account due to a missing token", API1_2, DeleteOpenCorporatesURL) {
//      Given("We will not use an access token and will set an open corporates url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the open corporates url should not be null")
//      val urlAfterDelete = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the open corporates url for a random other bank account because the user does not have enough privileges", API1_2, DeleteOpenCorporatesURL) {
//      Given("We will use an access token and will set an open corporates url first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomURL = randomString(20)
//      postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the open corporates url should not be null")
//      val urlAfterDelete = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      urlAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the open corporates url for a random other bank account because the account does not exist", API1_2, DeleteOpenCorporatesURL) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomURL = randomString(20)
//      When("the delete request is sent")
//      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We post the corporate location for one specific other bank"){
//    scenario("we will post the corporate location for one random other bank account", API1_2, PostCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the corporate location should be changed")
//      val location = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomLoc.latitude should equal (location.latitude)
//      randomLoc.longitude should equal (location.longitude)
//    }
//
//    scenario("we will not post the corporate location for a random other bank account due to a missing token", API1_2, PostCorporateLocation) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the corporate location for one random other bank account because the coordinates don't exist", API1_2, PostCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
//      When("the request is sent")
//      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the corporate location for a random other bank account because the user does not have enough privileges", API1_2, PostCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the corporate location for a random other bank account because the view does not exist", API1_2, PostCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the corporate location for a random other bank account because the account does not exist", API1_2, PostCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the corporate location for one specific other bank"){
//    scenario("we will update the corporate location for one random other bank account", API1_2, PutCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomLoc = randomLocation
//      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the corporate location should be changed")
//      val location = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomLoc.latitude should equal (location.latitude)
//      randomLoc.longitude should equal (location.longitude)
//    }
//
//    scenario("we will not update the corporate location for one random other bank account because the coordinates don't exist", API1_2, PutCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
//      When("the request is sent")
//      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the corporate location for a random other bank account due to a missing token", API1_2, PutCorporateLocation) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the corporate location for a random other bank account because the user does not have enough privileges", API1_2, PutCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the corporate location for a random other bank account because the account does not exist", API1_2, PutCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the corporate location for one specific other bank"){
//    scenario("we will delete the corporate location for one random other bank account", API1_2, DeleteCorporateLocation) {
//      Given("We will use an access token and will set a corporate location first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the corporate location should be null")
//      val locationAfterDelete = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      locationAfterDelete should equal (null)
//    }
//
//    scenario("we will not delete the corporate location for a random other bank account due to a missing token", API1_2, DeleteCorporateLocation) {
//      Given("We will not use an access token and will set a corporate location first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the corporate location should not be null")
//      val locationAfterDelete = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      locationAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the corporate location for a random other bank account because the user does not have enough privileges", API1_2, DeleteCorporateLocation) {
//      Given("We will use an access token and will set a corporate location first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the corporate location should not be null")
//      val locationAfterDelete = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      locationAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the corporate location for a random other bank account because the account does not exist", API1_2, DeleteCorporateLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the delete request is sent")
//      val deleteReply = deleteCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We post the physical location for one specific other bank"){
//    scenario("we will post the physical location for one random other bank account", API1_2, PostPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the physical location should be changed")
//      val location = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomLoc.latitude should equal (location.latitude)
//      randomLoc.longitude should equal (location.longitude)
//    }
//
//    scenario("we will not post the physical location for one random other bank account because the coordinates don't exist", API1_2, PostPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
//      When("the request is sent")
//      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the physical location for a random other bank account due to a missing token", API1_2, PostPhysicalLocation) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the physical location for a random other bank account because the user does not have enough privileges", API1_2, PostPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the physical location for a random other bank account because the view does not exist", API1_2, PostPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the physical location for a random other bank account because the account does not exist", API1_2, PostPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the physical location for one specific other bank"){
//    scenario("we will update the physical location for one random other bank account", API1_2, PutPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomLoc = randomLocation
//      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the physical location should be changed")
//      val location = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      randomLoc.latitude should equal (location.latitude)
//      randomLoc.longitude should equal (location.longitude)
//    }
//
//    scenario("we will not update the physical location for one random other bank account because the coordinates don't exist", API1_2, PutPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
//      When("the request is sent")
//      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the physical location for a random other bank account due to a missing token", API1_2, PutPhysicalLocation) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the physical location for a random other bank account because the user does not have enough privileges", API1_2, PutPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the physical location for a random other bank account because the account does not exist", API1_2, PutPhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the physical location for one specific other bank"){
//    scenario("we will delete the physical location for one random other bank account", API1_2, DeletePhysicalLocation) {
//      Given("We will use an access token and will set a physical location first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deletePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the physical location should be null")
//      val locationAfterDelete = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      locationAfterDelete should equal (null)
//    }
//
//    scenario("we will not delete the physical location for a random other bank account due to a missing token", API1_2, DeletePhysicalLocation) {
//      Given("We will not use an access token and will set a physical location first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deletePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the physical location should not be null")
//      val locationAfterDelete = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      locationAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the physical location for a random other bank account because the user does not have enough privileges", API1_2, DeletePhysicalLocation) {
//      Given("We will use an access token and will set a physical location first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deletePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the physical location should not be null")
//      val locationAfterDelete = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, user1)
//      locationAfterDelete should not equal (null)
//    }
//
//    scenario("we will not delete the physical location for a random other bank account because the account does not exist", API1_2, DeletePhysicalLocation) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the delete request is sent")
//      val deleteReply = deletePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("Information about all the transaction"){
//    scenario("we get all the transactions of one random (private) bank account", API1_2, GetTransactions) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getTransactions(bankId,bankAccount.id,view, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions array should not be empty")
//      transactions.transactions.size should not equal (0)
//
//    }
//
//    scenario("we do not get transactions of one random bank account, because the account doesn't exist", API1_2, GetTransactions) {
//      Given("We will use an access token")
//      When("the request is sent")
//      val bankId = randomBank
//      val reply = getTransactions(bankId,randomString(5),randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//
//    scenario("we do not get transactions of one random bank account, because the view doesn't exist", API1_2, GetTransactions) {
//      Given("We will use an access token")
//      When("the request is sent")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val reply = getTransactions(bankId,bankAccount.id,randomString(5), user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//    }
//  }
//
//  feature("transactions with params"){
//    import java.text.SimpleDateFormat
//    import java.util.Calendar
//    val defaultFormat = APIUtil.DateWithMsFormat
//    val rollbackFormat = APIUtil.DateWithMsRollbackFormat
//    scenario("we don't get transactions due to wrong value for obp_sort_direction parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_sort_direction")
//      val params = ("obp_sort_direction", "foo") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we get all the transactions sorted by ASC", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with the value ASC for param obp_sort_by")
//      val params = ("obp_sort_direction", "ASC") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions array should not be empty")
//      transactions.transactions.size should not equal (0)
//      val transaction1 = transactions.transactions(0)
//      val transaction2 = transactions.transactions(1)
//      transaction1.details.completed.before(transaction2.details.completed) should equal(true)
//    }
//    scenario("we get all the transactions sorted by asc", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with the value asc for param obp_sort_by")
//      val params = ("obp_sort_direction", "asc") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions array should not be empty")
//      transactions.transactions.size should not equal (0)
//      val transaction1 = transactions.transactions(0)
//      val transaction2 = transactions.transactions(1)
//      transaction1.details.completed.before(transaction2.details.completed) should equal(true)
//    }
//    scenario("we get all the transactions sorted by DESC", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with the value DESC for param obp_sort_by")
//      val params = ("obp_sort_direction", "DESC") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions array should not be empty")
//      transactions.transactions.size should not equal (0)
//      val transaction1 = transactions.transactions(0)
//      val transaction2 = transactions.transactions(1)
//      transaction1.details.completed.before(transaction2.details.completed) should equal(false)
//    }
//    scenario("we get all the transactions sorted by desc", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with the value desc for param obp_sort_by")
//      val params = ("obp_sort_direction", "desc") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions array should not be empty")
//      transactions.transactions.size should not equal (0)
//      val transaction1 = transactions.transactions(0)
//      val transaction2 = transactions.transactions(1)
//      transaction1.details.completed.before(transaction2.details.completed) should equal(false)
//
//    }
//    scenario("we don't get transactions due to wrong value (not a number) for obp_limit parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_limit")
//      val params = ("obp_limit", "foo") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we don't get transactions due to wrong value (0) for obp_limit parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_limit")
//      val params = ("obp_limit", "0") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we don't get transactions due to wrong value (-100) for obp_limit parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_limit")
//      val params = ("obp_limit", "-100") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we get only 5 transactions due to the obp_limit parameter value", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with the value ASC for parameter obp_limit")
//      val params = ("obp_limit", "5") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions size should be equal to 5")
//      transactions.transactions.size should equal (5)
//    }
//    scenario("we don't get transactions due to wrong value for obp_from_date parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_from_date")
//      val params = ("obp_from_date", "foo") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we get transactions from a previous date with the right format", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with obp_from_date into a proper format")
//      val currentDate = new Date()
//      val calendar = Calendar.getInstance
//      calendar.setTime(currentDate)
//      calendar.add(Calendar.YEAR, -1)
//      val pastDate = calendar.getTime
//      val formatedPastDate = defaultFormat.format(pastDate)
//      val params = ("obp_from_date", formatedPastDate) :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should not be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should not equal (0)
//    }
//    scenario("we get transactions from a previous date (obp_from_date) with the fallback format", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with obp_from_date into an accepted format")
//      val currentDate = new Date()
//      val calendar = Calendar.getInstance
//      calendar.setTime(currentDate)
//      calendar.add(Calendar.YEAR, -1)
//      val pastDate = calendar.getTime
//      val formatedPastDate = rollbackFormat.format(pastDate)
//      val params = ("obp_from_date", formatedPastDate) :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should not be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should not equal (0)
//    }
//    scenario("we don't get transactions from a date in the future", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with obp_from_date into a proper format")
//      val currentDate = new Date()
//      val calendar = Calendar.getInstance
//      calendar.setTime(currentDate)
//      calendar.add(Calendar.YEAR, 1)
//      val futureDate = calendar.getTime
//      val formatedFutureDate = defaultFormat.format(futureDate)
//      val params = ("obp_from_date", formatedFutureDate) :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should not be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should equal (0)
//    }
//    scenario("we don't get transactions due to wrong value for obp_to_date parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_to_date")
//      val params = ("obp_to_date", "foo") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we get transactions from a previous (obp_to_date) date with the right format", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with obp_to_date into a proper format")
//      val currentDate = new Date()
//      val formatedCurrentDate = defaultFormat.format(currentDate)
//      val params = ("obp_to_date", formatedCurrentDate) :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should not be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should not equal (0)
//    }
//    scenario("we get transactions from a previous date with the fallback format", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with obp_to_date into an accepted format")
//      val currentDate = new Date()
//      val formatedCurrentDate = defaultFormat.format(currentDate)
//      val params = ("obp_to_date", formatedCurrentDate) :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should not be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should not equal (0)
//    }
//    scenario("we don't get transactions from a date in the past", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with obp_to_date into a proper format")
//      val currentDate = new Date()
//      val calendar = Calendar.getInstance
//      calendar.setTime(currentDate)
//      calendar.add(Calendar.YEAR, -1)
//      val pastDate = calendar.getTime
//      val formatedPastDate = defaultFormat.format(pastDate)
//      val params = ("obp_to_date", formatedPastDate) :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should equal (0)
//    }
//    scenario("we don't get transactions due to wrong value (not a number) for obp_offset parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_offset")
//      val params = ("obp_offset", "foo") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we don't get transactions due to the (2000) for obp_offset parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_offset")
//      val params = ("obp_offset", "2000") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      And("transactions size should be empty")
//      val transactions = reply.body.extract[TransactionsJSON]
//      transactions.transactions.size should equal (0)
//    }
//    scenario("we don't get transactions due to wrong value (-100) for obp_offset parameter", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with a wrong value for param obp_offset")
//      val params = ("obp_offset", "-100") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//    scenario("we get only 5 transactions due to the obp_offset parameter value", API1_2, GetTransactions, GetTransactionsWithParams) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent with the value ASC for parameter obp_offset")
//      val params = ("obp_offset", "5") :: Nil
//      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      val transactions = reply.body.extract[TransactionsJSON]
//      And("transactions size should be equal to 5")
//      transactions.transactions.size should equal (5)
//    }
//  }
//
//  feature("Information about a transaction"){
//    scenario("we get transaction data by using an access token", API1_2, GetTransaction) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 ok code")
//      reply.code should equal (200)
//      reply.body.extract[TransactionJSON]
//    }
//
//    scenario("we will not get transaction data due to a missing token", API1_2, GetTransaction) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//
//    scenario("we will not get transaction data because user does not have enough privileges", API1_2, GetTransaction) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//
//    scenario("we will not get transaction data because the account does not exist", API1_2, GetTransaction) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTransaction(bankId, randomString(5), view, transaction.id, user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//
//    scenario("we will not get transaction data because the view does not exist", API1_2, GetTransaction) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//    }
//
//    scenario("we will not get transaction data because the transaction does not exist", API1_2, GetTransaction) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//    }
//
//  }
//
//  feature("We get the narrative of one random transaction"){
//    scenario("we will get the narrative of one random transaction", API1_2, GetNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      reply.body.extract[TransactionNarrativeJSON]
//    }
//
//    scenario("we will not get the narrative of one random transaction due to a missing token", API1_2, GetNarrative) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the narrative of one random transaction because the user does not have enough privileges", API1_2, GetNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the narrative of one random transaction because the view does not exist", API1_2, GetNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the narrative of one random transaction because the transaction does not exist", API1_2, GetNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post the narrative for one random transaction"){
//    scenario("we will post the narrative for one random transaction", API1_2, PostNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomNarrative = randomString(20)
//      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the narrative should be added")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should equal (theNarrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not post the narrative for one random transaction due to a missing token", API1_2, PostNarrative) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the narrative should not be added")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should not equal (theNarrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not post the narrative for one random transaction because the user does not have enough privileges", API1_2, PostNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the narrative should not be added")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should not equal (theNarrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not post the narrative for one random transaction because the view does not exist", API1_2, PostNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomNarrative, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the narrative should not be added")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should not equal (theNarrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not post the narrative for one random transaction because the transaction does not exist", API1_2, PostNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomNarrative, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the narrative for one random transaction"){
//    scenario("we will the narrative for one random transaction", API1_2, PutNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomNarrative = randomString(20)
//      val putReply = updateNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the narrative should be changed")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val narrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should equal (narrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not update the narrative for one random transaction due to a missing token", API1_2, PutNarrative) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val putReply = updateNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the narrative should not be changed")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val narrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should not equal (narrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not update the narrative for one random transaction because the user does not have enough privileges", API1_2, PutNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val putReply = updateNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the narrative should not be changed")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val narrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      randomNarrative should not equal (narrativeAfterThePost.narrative)
//    }
//
//    scenario("we will not update the narrative for one random transaction because the transaction does not exist", API1_2, PutNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transactionId = randomString(5)
//      val randomNarrative = randomString(20)
//      When("the request is sent")
//      val putReply = updateNarrativeForOneTransaction(bankId, bankAccount.id, view, transactionId, randomNarrative, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the narrative for one random transaction"){
//    scenario("we will delete the narrative for one random transaction", API1_2, DeleteNarrative) {
//      Given("We will use an access token and will set a narrative first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the narrative should be null")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val narrativeAfterTheDelete : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      narrativeAfterTheDelete.narrative should equal (null)
//    }
//
//    scenario("we will not delete narrative for one random transaction due to a missing token", API1_2, DeleteNarrative) {
//      Given("We will not use an access token and will set a narrative first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the public narrative should not be null")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val narrativeAfterTheDelete : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      narrativeAfterTheDelete.narrative should not equal (null)
//    }
//
//    scenario("we will not delete the narrative for one random transaction because the user does not have enough privileges", API1_2, DeleteNarrative) {
//      Given("We will use an access token and will set a narrative first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomNarrative = randomString(20)
//      postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      And("the narrative should not be null")
//      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val narrativeAfterTheDelete : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
//      narrativeAfterTheDelete.narrative should not equal (null)
//    }
//
//    scenario("we will not delete the narrative for one random transaction because the transaction does not exist", API1_2, DeleteNarrative) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "owner"
//      val randomNarrative = randomString(20)
//      When("the delete request is sent")
//      val deleteReply = deleteNarrativeForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We get the comments of one random transaction"){
//    scenario("we will get the comments of one random transaction", API1_2, GetComments) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      reply.body.extract[TransactionCommentsJSON]
//    }
//
//    scenario("we will not get the comments of one random transaction due to a missing token", API1_2, GetComments) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the comments of one random transaction because the user does not have enough privileges", API1_2, GetComments) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the comments of one random transaction because the view does not exist", API1_2, GetComments) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the comments of one random transaction because the transaction does not exist", API1_2, GetComments) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post a comment for one random transaction"){
//    scenario("we will post a comment for one random transaction", API1_2, PostComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[TransactionCommentJSON]
//      And("the comment should be added")
//      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
//      val theComment = theCommentsAfterThePost.find(_.value == randomComment.value)
//      theComment.nonEmpty should equal (true)
//      theComment.get.user should not equal (null)
//
//    }
//
//    scenario("we will not post a comment for one random transaction due to a missing token", API1_2, PostComment) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      When("the request is sent")
//      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the comment should not be added")
//      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
//      val notFound = theCommentsAfterThePost.find(_.value == randomComment.value) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//
//    scenario("we will not post a comment for one random transaction because the user does not have enough privileges", API1_2, PostComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//       val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      When("the request is sent")
//      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the comment should not be added")
//      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
//      val notFound = theCommentsAfterThePost.find(_.value == randomComment.value) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post a comment for one random transaction because the view does not exist", API1_2, PostComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//       val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      When("the request is sent")
//      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomComment, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the comment should not be added")
//      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
//      val notFound = theCommentsAfterThePost.find(_.value == randomComment.value) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post a comment for one random transaction because the transaction does not exist", API1_2, PostComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      When("the request is sent")
//      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomComment, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete a comment for one random transaction"){
//    scenario("we will delete a comment for one random transaction", API1_2, DeleteComment) {
//      Given("We will use an access token and will set a comment first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user1)
//      val postedComment = postedReply.body.extract[TransactionCommentJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedComment.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//    }
//
//    scenario("we will not delete a comment for one random transaction due to a missing token", API1_2, DeleteComment) {
//      Given("We will not use an access token and will set a comment first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user1)
//      val postedComment = postedReply.body.extract[TransactionCommentJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedComment.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a comment for one random transaction because the user does not have enough privileges", API1_2, DeleteComment) {
//      Given("We will use an access token and will set a comment first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user1)
//      val postedComment = postedReply.body.extract[TransactionCommentJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedComment.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a comment for one random transaction because the user did not post the comment", API1_2, DeleteComment) {
//      Given("We will use an access token and will set a comment first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "public"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user2)
//      val postedComment = postedReply.body.extract[TransactionCommentJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedComment.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a comment for one random transaction because the comment does not exist", API1_2, DeleteComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a comment for one random transaction because the transaction does not exist", API1_2, DeleteComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user1)
//      val postedComment = postedReply.body.extract[TransactionCommentJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, randomString(5), postedComment.id, user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a comment for one random transaction because the view does not exist", API1_2, DeleteComment) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomComment = PostTransactionCommentJSON(randomString(20))
//      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment, user1)
//      val postedComment = postedReply.body.extract[TransactionCommentJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, postedComment.id, user1)
//      Then("we should get a 404 code")
//      deleteReply.code should equal (404)
//    }
//  }
//
//  feature("We get the tags of one random transaction"){
//    scenario("we will get the tags of one random transaction", API1_2, GetTags) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      reply.body.extract[TransactionTagsJSON]
//    }
//
//    scenario("we will not get the tags of one random transaction due to a missing token", API1_2, GetTags) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the tags of one random transaction because the user does not have enough privileges", API1_2, GetTags) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the tags of one random transaction because the view does not exist", API1_2, GetTags) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTagsForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the tags of one random transaction because the transaction does not exist", API1_2, GetTags) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getTagsForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post a tag for one random transaction"){
//    scenario("we will post a tag for one random transaction", API1_2, PostTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[TransactionTagJSON]
//      And("the tag should be added")
//      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
//      val theTag = theTagsAfterThePost.find(_.value == randomTag.value)
//      theTag.nonEmpty should equal (true)
//      theTag.get.user should not equal (null)
//    }
//
//    scenario("we will not post a tag for one random transaction due to a missing token", API1_2, PostTag) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      When("the request is sent")
//      val postReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the tag should not be added")
//      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
//      val notFound = theTagsAfterThePost.find(_.value == randomTag.value) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post a tag for one random transaction because the user does not have enough privileges", API1_2, PostTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//       val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      When("the request is sent")
//      val postReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the tag should not be added")
//      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
//      val notFound = theTagsAfterThePost.find(_.value == randomTag.value) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post a tag for one random transaction because the view does not exist", API1_2, PostTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//       val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      When("the request is sent")
//      val postReply = postTagForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomTag, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the tag should not be added")
//      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
//      val notFound = theTagsAfterThePost.find(_.value == randomTag.value) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post a tag for one random transaction because the transaction does not exist", API1_2, PostTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      When("the request is sent")
//      val postReply = postTagForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomTag, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete a tag for one random transaction"){
//    scenario("we will delete a tag for one random transaction", API1_2, DeleteTag) {
//      Given("We will use an access token and will set a tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user1)
//      val postedTag = postedReply.body.extract[TransactionTagJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedTag.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//    }
//
//    scenario("we will not delete a tag for one random transaction due to a missing token", API1_2, DeleteTag) {
//      Given("We will not use an access token and will set a tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user1)
//      val postedTag = postedReply.body.extract[TransactionTagJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedTag.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a tag for one random transaction because the user does not have enough privileges", API1_2, DeleteTag) {
//      Given("We will use an access token and will set a tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user1)
//      val postedTag = postedReply.body.extract[TransactionTagJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedTag.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a tag for one random transaction because the user did not post the tag", API1_2, DeleteTag) {
//      Given("We will use an access token and will set a tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "public"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user2)
//      val postedTag = postedReply.body.extract[TransactionTagJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedTag.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a tag for one random transaction because the tag does not exist", API1_2, DeleteTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a tag for one random transaction because the transaction does not exist", API1_2, DeleteTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user1)
//      val postedTag = postedReply.body.extract[TransactionTagJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, randomString(5), postedTag.id, user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete a tag for one random transaction because the view does not exist", API1_2, DeleteTag) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomTag = PostTransactionTagJSON(randomString(5))
//      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag, user1)
//      val postedTag = postedReply.body.extract[TransactionTagJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id,  postedTag.id, user1)
//      Then("we should get a 404 code")
//      deleteReply.code should equal (404)
//    }
//  }
//
//  feature("We get the images of one random transaction"){
//    scenario("we will get the images of one random transaction", API1_2, GetImages) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      reply.body.extract[TransactionImagesJSON]
//    }
//
//    scenario("we will not get the images of one random transaction due to a missing token", API1_2, GetImages) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the images of one random transaction because the user does not have enough privileges", API1_2, GetImages) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the images of one random transaction because the view does not exist", API1_2, GetImages) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getImagesForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the images of one random transaction because the transaction does not exist", API1_2, GetImages) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getImagesForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post an image for one random transaction"){
//    scenario("we will post an image for one random transaction", API1_2, PostImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[TransactionImageJSON]
//      And("the image should be added")
//      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
//      val theImage = theImagesAfterThePost.find(_.URL == randomImage.URL)
//      theImage.nonEmpty should equal (true)
//      theImage.get.user should not equal (null)
//    }
//
//    scenario("we will not post an image for one random transaction due to a missing token", API1_2, PostImage) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com/"+randomString(5))
//      When("the request is sent")
//      val postReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image should not be added")
//      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
//      val notFound = theImagesAfterThePost.find(_.URL == randomImage.URL) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post an image for one random transaction because the user does not have enough privileges", API1_2, PostImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//       val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      When("the request is sent")
//      val postReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image should not be added")
//      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
//      val notFound = theImagesAfterThePost.find(_.URL == randomImage.URL) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post an image for one random transaction because the view does not exist", API1_2, PostImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com/"+randomString(5))
//      When("the request is sent")
//      val postReply = postImageForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomImage, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//      And("the image should not be added")
//      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
//      val notFound = theImagesAfterThePost.find(_.URL == randomImage.URL) match {
//        case None => true
//        case Some(_) => false
//      }
//      notFound should equal (true)
//    }
//
//    scenario("we will not post an image for one random transaction because the transaction does not exist", API1_2, PostImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      When("the request is sent")
//      val postReply = postImageForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomImage, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete an image for one random transaction"){
//    scenario("we will delete an image for one random transaction", API1_2, DeleteImage) {
//      Given("We will use an access token and will set an image first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      val postedImage = postedReply.body.extract[TransactionImageJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedImage.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//    }
//
//    scenario("we will not delete an image for one random transaction due to a missing token", API1_2, DeleteImage) {
//      Given("We will not use an access token and will set an image first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      val postedImage = postedReply.body.extract[TransactionImageJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedImage.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete an image for one random transaction because the user does not have enough privileges", API1_2, DeleteImage) {
//      Given("We will use an access token and will set an image first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      val postedImage = postedReply.body.extract[TransactionImageJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedImage.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete an image for one random transaction because the user did not post the image", API1_2, DeleteImage) {
//      Given("We will use an access token and will set an image first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "public"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      val postedImage = postedReply.body.extract[TransactionImageJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedImage.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete an image for one random transaction because the image does not exist", API1_2, DeleteImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete an image for one random transaction because the transaction does not exist", API1_2, DeleteImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      val postedImage = postedReply.body.extract[TransactionImageJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, randomString(5), postedImage.id, user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete an image for one random transaction because the view does not exist", API1_2, DeleteImage) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
//      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage, user1)
//      val postedImage = postedReply.body.extract[TransactionImageJSON]
//      When("the delete request is sent")
//      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, postedImage.id, user1)
//      Then("we should get a 404 code")
//      deleteReply.code should equal (404)
//    }
//  }
//
//  feature("We get the where of one random transaction"){
//    scenario("we will get the where of one random transaction", API1_2, GetWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the request is sent")
//      val reply = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//    }
//
//    scenario("we will not get the where of one random transaction due to a missing token", API1_2, GetWhere) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the request is sent")
//      val reply = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the where of one random transaction because the user does not have enough privileges", API1_2, GetWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the request is sent")
//      val reply = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the where of one random transaction because the view does not exist", API1_2, GetWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the request is sent")
//      val reply = getWhereForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the where of one random transaction because the transaction does not exist", API1_2, GetWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We post the where for one random transaction"){
//    scenario("we will post the where for one random transaction", API1_2, PostWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      Then("we should get a 201 code")
//      postReply.code should equal (201)
//      postReply.body.extract[SuccessMessage]
//      And("the where should be posted")
//      val location = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1).body.extract[TransactionWhereJSON]
//      randomLoc.latitude should equal (location.where.latitude)
//      randomLoc.longitude should equal (location.where.longitude)
//      location.where.user should not equal (null)
//    }
//
//    scenario("we will not post the where for one random transaction because the coordinates don't exist", API1_2, PostWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
//      When("the request is sent")
//      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the where for a random transaction due to a missing token", API1_2, PostWhere) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, None)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the where for a random transaction because the user does not have enough privileges", API1_2, PostWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user3)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the where for a random transaction because the view does not exist", API1_2, PostWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply =  postWhereForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomLoc, user1)
//      Then("we should get a 404 code")
//      postReply.code should equal (404)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not post the where for a random transaction because the transaction does not exist", API1_2, PostWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomLoc, user1)
//      Then("we should get a 400 code")
//      postReply.code should equal (400)
//      And("we should get an error message")
//      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We update the where for one random transaction"){
//    scenario("we will update the where for one random transaction", API1_2, PutWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val randomLoc = randomLocation
//      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      Then("we should get a 200 code")
//      putReply.code should equal (200)
//      putReply.body.extract[SuccessMessage]
//      And("the where should be changed")
//      val location = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1).body.extract[TransactionWhereJSON]
//      randomLoc.latitude should equal (location.where.latitude)
//      randomLoc.longitude should equal (location.where.longitude)
//    }
//
//    scenario("we will not update the where for one random transaction because the coordinates don't exist", API1_2, PutWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
//      When("the request is sent")
//      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the where for a random transaction due to a missing token", API1_2, PutWhere) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      var randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, None)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the where for a random transaction because the user does not have enough privileges", API1_2, PutWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user3)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not update the where for a random transaction because the transaction does not exist", API1_2, PutWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the request is sent")
//      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomLoc, user1)
//      Then("we should get a 400 code")
//      putReply.code should equal (400)
//      And("we should get an error message")
//      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//
//  feature("We delete the where for one random transaction"){
//    scenario("we will delete the where for one random transaction", API1_2, DeleteWhere) {
//      Given("We will use an access token and will set a where tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 204 code")
//      deleteReply.code should equal (204)
//      And("the where should be null")
//      val locationAfterDelete = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user1).body.extract[TransactionWhereJSON]
//      locationAfterDelete.where should equal (null)
//    }
//
//    scenario("we will not delete the where for a random transaction due to a missing token", API1_2, DeleteWhere) {
//      Given("We will not use an access token and will set a where tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      // And("the where should not be null")
//    }
//
//    scenario("we will not delete the where for a random transaction because the user does not have enough privileges", API1_2, DeleteWhere) {
//      Given("We will use an access token and will set a where tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//      // And("the where should not be null")
//    }
//
//    scenario("we will not delete the where for one random transaction because the user did not post the geo tag", API1_2, DeleteWhere) {
//      Given("We will use an access token and will set a where tag first")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = "public"
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      val randomLoc = randomLocation
//      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc, user1)
//      When("the delete request is sent")
//      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//
//    scenario("we will not delete the where for a random transaction because the transaction does not exist", API1_2, DeleteWhere) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val randomLoc = randomLocation
//      When("the delete request is sent")
//      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      deleteReply.code should equal (400)
//    }
//  }
//
//  feature("We get the other bank account of a transaction "){
//    scenario("we will get the other bank account of a random transaction", API1_2, GetTransactionAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccountOfOneTransaction(bankId, bankAccount.id, view, transaction.id, user1)
//      Then("we should get a 200 code")
//      reply.code should equal (200)
//      val accountJson = reply.body.extract[OtherAccountJSON]
//      And("some fields should not be empty")
//      accountJson.id.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the other bank account of a random transaction due to a missing token", API1_2, GetTransactionAccount) {
//      Given("We will not use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccountOfOneTransaction(bankId, bankAccount.id, view, transaction.id, None)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get get the other bank account of a random transaction because the user does not have enough privileges", API1_2, GetTransactionAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccountOfOneTransaction(bankId, bankAccount.id, view, transaction.id, user3)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get the other bank account of a random transaction because the view does not exist", API1_2, GetTransactionAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      val transaction = randomTransaction(bankId, bankAccount.id, view)
//      When("the request is sent")
//      val reply = getTheOtherBankAccountOfOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, user1)
//      Then("we should get a 404 code")
//      reply.code should equal (404)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//
//    scenario("we will not get get the other bank account of a random transaction because the transaction does not exist", API1_2, GetTransactionAccount) {
//      Given("We will use an access token")
//      val bankId = randomBank
//      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
//      val view = randomViewPermalink(bankId, bankAccount)
//      When("the request is sent")
//      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, randomString(5), user1)
//      Then("we should get a 400 code")
//      reply.code should equal (400)
//      And("we should get an error message")
//      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
//    }
//  }
//}
