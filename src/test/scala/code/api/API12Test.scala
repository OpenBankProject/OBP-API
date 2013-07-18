/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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
package code.api.v1_2

import org.scalatest._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import _root_.net.liftweb.util._
import Helpers._
import _root_.net.liftweb.common._
import dispatch._, Defaults._
import _root_.net.liftweb.json.Extraction
import _root_.net.liftweb.json.Serialization
import _root_.net.liftweb.json.Serialization.write
import _root_.net.liftweb.json.JsonAST.{JValue, JObject}
import org.mortbay.jetty.nio.SelectChannelConnector
import net.liftweb.json.NoTypeHints
import net.liftweb.json.JsonDSL._
import scala.util.Random._
import net.liftweb.mapper.By
import java.util.Date

import code.model.TokenType._
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import code.model.dataAccess.{OBPUser, Privilege, HostedAccount, ViewImpl, ViewPrivileges }
import code.api.test.{ServerSetup, APIResponse}
import code.model.dataAccess.{OBPUser, Privilege, HostedAccount, Account}
import code.util.APIUtil.OAuth._


class API1_2Test extends ServerSetup{

  def v1_2Request = baseRequest / "obp" / "v1.2"

  implicit val dateFormats = net.liftweb.json.DefaultFormats

  val viewFileds = List(
      "canSeeTransactionThisBankAccount", "canSeeTransactionOtherBankAccount", "canSeeTransactionMetadata",
      "canSeeTransactionLabel", "canSeeTransactionAmount", "canSeeTransactionType", "canSeeTransactionCurrency",
      "canSeeTransactionStartDate", "canSeeTransactionFinishDate", "canSeeTransactionBalance", "canSeeComments",
      "canSeeOwnerComment", "canSeeTags", "canSeeImages", "canSeeBankAccountOwners", "canSeeBankAccountType",
      "canSeeBankAccountBalance", "canSeeBankAccountCurrency", "canSeeBankAccountLabel",
      "canSeeBankAccountNationalIdentifier", "canSeeBankAccountSwift_bic", "canSeeBankAccountIban",
      "canSeeBankAccountNumber", "canSeeBankAccountBankName", "canSeeBankAccountBankPermalink",
      "canSeeOtherAccountNationalIdentifier", "canSeeSWIFT_BIC", "canSeeOtherAccountIBAN",
      "canSeeOtherAccountBankName", "canSeeOtherAccountNumber", "canSeeOtherAccountMetadata", "canSeeOtherAccountKind",
      "canSeeMoreInfo", "canSeeUrl", "canSeeImageUrl", "canSeeOpenCorporatesUrl", "canSeeCorporateLocation",
      "canSeePhysicalLocation", "canSeePublicAlias", "canSeePrivateAlias", "canAddMoreInfo", "canAddURL", "canAddImageURL",
      "canAddOpenCorporatesUrl", "canAddCorporateLocation", "canAddPhysicalLocation", "canAddPublicAlias",
      "canAddPrivateAlias", "canDeleteCorporateLocation", "canDeletePhysicalLocation", "canEditOwnerComment",
      "canAddComment", "canDeleteComment", "canAddTag", "canDeleteTag", "canAddImage", "canDeleteImage", "canAddWhereTag",
      "canSeeWhereTag", "canDeleteWhereTag"
    )


  //create the application
  lazy val testConsumer =
    OBPConsumer.create.
      name("test application").
      isActive(true).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      saveMe

  lazy val consumer = new Consumer (testConsumer.key,testConsumer.secret)
  // create the access token
  lazy val tokenDuration = weeks(4)

  lazy val user1 =
    OBPUser.create.
      email("testuser1@example.com").
      password(randomString(10)).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  override def specificSetup() ={
    //give to user1 all the privileges on all the accounts
    val privileges =
      HostedAccount.findAll.map(bankAccount => {
        Privilege.create.
        account(bankAccount).
        user(user1).
        saveMe
      })
    for{
      p <- privileges
      v <- ViewImpl.findAll()
    }{
      ViewPrivileges.create.
        view(v).
        privilege(p).
        save
    }
  }

  lazy val testToken =
    OBPToken.create.
    tokenType(Access).
    consumerId(testConsumer.id).
    userId(user1.email).
    key(randomString(40).toLowerCase).
    secret(randomString(40).toLowerCase).
    duration(tokenDuration).
    expirationDate({(now : TimeSpan) + tokenDuration}).
    insertDate(now).
    saveMe

  lazy val token = new Token(testToken.key, testToken.secret)

  // create a user for test purposes
  lazy val user2 =
    OBPUser.create.
      email("testuser2@example.com").
      password(randomString(10)).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  //we create an access token for the other user
  lazy val testToken2 =
    OBPToken.create.
    tokenType(Access).
    consumerId(testConsumer.id).
    userId(user2.email).
    key(randomString(40).toLowerCase).
    secret(randomString(40).toLowerCase).
    duration(tokenDuration).
    expirationDate({(now : TimeSpan) + tokenDuration}).
    insertDate(now).
    saveMe

  lazy val token2 = new Token(testToken2.key, testToken2.secret)

  // create a user for test purposes
  lazy val user3 =
    OBPUser.create.
      email("testuser3@example.com").
      password(randomString(10)).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  //we create an access token for the other user
  lazy val testToken3 =
    OBPToken.create.
    tokenType(Access).
    consumerId(testConsumer.id).
    userId(user3.email).
    key(randomString(40).toLowerCase).
    secret(randomString(40).toLowerCase).
    duration(tokenDuration).
    expirationDate({(now : TimeSpan) + tokenDuration}).
    insertDate(now).
    saveMe

  lazy val token3 = new Token(testToken3.key, testToken3.secret)


  /************************* test tags ************************/

  object CurrentTest extends Tag("currentScenario")
  object API1_2 extends Tag("api1.2")
  object APIInfo extends Tag("apiInfo")
  object GetHostedBanks extends Tag("hostedBanks")
  object GetHostedBank extends Tag("getHostedBank")
  object GetBankAccounts extends Tag("getBankAccounts")
  object GetPublicBankAccounts extends Tag("getPublicBankAccounts")
  object GetPrivateBankAccounts extends Tag("getPrivateBankAccounts")
  object GetBankAccount extends Tag("getBankAccount")
  object GetViews extends Tag("getViews")
  object PostView extends Tag("postView")
  object DeleteView extends Tag("deleteView")
  object GetPermissions extends Tag("getPermissions")
  object GetPermission extends Tag("getPermission")
  object PostPermission extends Tag("postPermission")
  object PostPermissions extends Tag("postPermissions")
  object DeletePermission extends Tag("deletePermission")
  object DeletePermissions extends Tag("deletePermissions")
  object GetOtherBankAccounts extends Tag("getOtherBankAccounts")
  object GetOtherBankAccount extends Tag("getOtherBankAccount")
  object GetOtherBankAccountMetadata extends Tag("getOtherBankAccountMetadata")
  object GetPublicAlias extends Tag("getPublicAlias")
  object PostPublicAlias extends Tag("postPublicAlias")
  object PutPublicAlias extends Tag("putPublicAlias")
  object DeletePublicAlias extends Tag("deletePublicAlias")
  object GetPrivateAlias extends Tag("getPrivateAlias")
  object PostPrivateAlias extends Tag("postPrivateAlias")
  object PutPrivateAlias extends Tag("putPrivateAlias")
  object DeletePrivateAlias extends Tag("deletePrivateAlias")
  object PostMoreInfo extends Tag("postMoreInfo")
  object PutMoreInfo extends Tag("putMoreInfo")
  object DeleteMoreInfo extends Tag("deleteMoreInfo")
  object PostURL extends Tag("postURL")
  object PutURL extends Tag("putURL")
  object DeleteURL extends Tag("deleteURL")
  object PostImageURL extends Tag("postImageURL")
  object PutImageURL extends Tag("putImageURL")
  object DeleteImageURL extends Tag("DeleteImageURL")
  object PostOpenCorporatesURL extends Tag("postOpenCorporatesURL")
  object PutOpenCorporatesURL extends Tag("putOpenCorporatesURL")
  object DeleteOpenCorporatesURL extends Tag("deleteOpenCorporatesURL")
  object PostCorporateLocation extends Tag("postCorporateLocation")
  object PutCorporateLocation extends Tag("putCorporateLocation")
  object DeleteCorporateLocation extends Tag("deleteCorporateLocation")
  object PostPhysicalLocation extends Tag("postPhysicalLocation")
  object PutPhysicalLocation extends Tag("putPhysicalLocation")
  object DeletePhysicalLocation extends Tag("deletePhysicalLocation")
  object GetTransactions extends Tag("getTransactions")
  object GetTransaction extends Tag("getTransaction")
  object GetNarrative extends Tag("getNarrative")
  object PostNarrative extends Tag("postNarrative")
  object PutNarrative extends Tag("putNarrative")
  object DeleteNarrative extends Tag("deleteNarrative")
  object GetComments extends Tag("getComments")
  object PostComment extends Tag("postComment")
  object DeleteComment extends Tag("deleteComment")
  object GetTags extends Tag("getTags")
  object PostTag extends Tag("postTag")
  object DeleteTag extends Tag("deleteTag")
  object GetImages extends Tag("getImages")
  object PostImage extends Tag("postImage")
  object DeleteImage extends Tag("deleteImage")
  object GetWhere extends Tag("getWhere")
  object PostWhere extends Tag("postWhere")
  object PutWhere extends Tag("putWhere")
  object DeleteWhere extends Tag("deleteWhere")
  object GetTransactionAccount extends Tag("getTransactionAccount")

  /********************* API test methods ********************/
  val emptyJSON : JObject =
    ("error" -> "empty List")
  val errorAPIResponse = new APIResponse(400,emptyJSON)

  def randomViewPermalink(bankId: String, account: AccountJSON) : String = {
    val request = v1_2Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinks = reply.body.extract[ViewsJSON].views.filterNot(_.is_public==true)
    val randomPosition = nextInt(possibleViewsPermalinks.size)
    possibleViewsPermalinks(randomPosition).id
  }

  def randomBank : String = {
    val banksJson = getBanksInfo.body.extract[BanksJSON]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }

  def randomPublicAccount(bankId : String) : AccountJSON = {
    val accountsJson = getPublicAccounts(bankId).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomPrivateAccount(bankId : String) : AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomAccountPermission(bankId : String, accountId : String) : PermissionJSON = {
    val persmissionsInfo = getAccountPermissions(bankId, accountId).body.extract[PermissionsJSON]
    val randomPermission = nextInt(persmissionsInfo.permissions.size)
    persmissionsInfo.permissions(randomPermission)
  }

  def randomOtherBankAccount(bankId : String, accountId : String, viewId : String): OtherAccountJSON = {
    val otherAccounts = getTheOtherBankAccounts(bankId, accountId, viewId).body.extract[OtherAccountsJSON].other_accounts
    otherAccounts(nextInt(otherAccounts.size))
  }

  def randomLocation : LocationPlainJSON = {
    def sign = {
      val b = nextBoolean
      if(b) 1
      else -1
    }
    val longitude : Double = nextInt(180)*sign*nextDouble
    val latitude : Double = nextInt(90)*sign*nextDouble
    JSONFactory.createLocationPlainJSON(latitude, longitude)
  }

  def randomTransaction(bankId : String, accountId : String, viewId: String) : TransactionJSON = {
    val transactionsJson = getTransactions(bankId, accountId, viewId).body.extract[TransactionsJSON].transactions
    val randomPosition = nextInt(transactionsJson.size)
    transactionsJson(randomPosition)
  }

  def randomViewsIdsToGrant(bankId : String, accountId : String) : List[String]= {
    //get the view ids of the available views on the bank accounts
    val viewsIds = getAccountViews(bankId, accountId).body.extract[ViewsJSON].views.map(_.id)
    //choose randomly some view ids to grant
    val (viewsIdsToGrant, _) = viewsIds.splitAt(nextInt(viewsIds.size) + 1)
    viewsIdsToGrant
  }

  def randomView(isPublic: Boolean, alias: String) : ViewCreationJSON = {
    ViewCreationJSON(
      name = randomString(3),
      description = randomString(3),
      isPublic = isPublic,
      alias=alias,
      hideMetadataIfAlias=false,
      allowedFields = viewFileds
    )
  }
  def getAPIInfo : APIResponse = {
    val request = v1_2Request
    makeGetRequest(request)
  }

  def getBanksInfo : APIResponse  = {
    val request = v1_2Request / "banks"
    makeGetRequest(request)
  }

  def getBankInfo(bankId : String) : APIResponse  = {
    val request = v1_2Request / "banks" / bankId
    makeGetRequest(request)
  }

  def getPublicAccounts(bankId : String) : APIResponse= {
    val request = v1_2Request / "banks" / bankId / "accounts" / "public"
    makeGetRequest(request)
  }

  def getPrivateAccounts(bankId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / "private" <@(consumer,token)
    makeGetRequest(request)
  }

  def getBankAccounts(bankId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts"
    makeGetRequest(request)
  }

  def getBankAccountsWithToken(bankId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" <@(consumer,token)
    makeGetRequest(request)
  }

  def getPrivateAccountsWithOutToken(bankId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / "private"
    makeGetRequest(request)
  }

  def getPublicBankAccountDetails(bankId : String, accountId : String, viewId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "account"
    makeGetRequest(request)
  }

  def getPrivateBankAccountDetails(bankId : String, accountId : String, viewId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "account" <@(consumer,token)
    makeGetRequest(request)
  }

  def getAccountViews(bankId : String, accountId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumer,token)
    makeGetRequest(request)
  }

  def getAccountViewsWithoutToken(bankId : String, accountId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "views"
    makeGetRequest(request)
  }

  def getAccountViewsWithoutOwnerAccess(bankId : String, accountId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumer,token3)
    makeGetRequest(request)
  }

  def postView(bankId: String, accountId: String, view: ViewCreationJSON): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumer,token)
    makePostRequest(request, write(view))
  }

  def postViewWithoutToken(bankId: String, accountId: String, view: ViewCreationJSON): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views").POST
    makePostRequest(request, write(view))
  }

  def postViewWithoutOwnerAccess(bankId: String, accountId: String, view: ViewCreationJSON): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumer,token3)
    makePostRequest(request, write(view))
  }

  def deleteView(bankId: String, accountId: String, viewId: String): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).DELETE <@(consumer,token)
    makeDeleteRequest(request)
  }

  def deleteViewWithoutToken(bankId: String, accountId: String, viewId: String): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).DELETE
    makeDeleteRequest(request)
  }

  def deleteViewWithoutOwnerAccess(bankId: String, accountId: String, viewId: String): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).DELETE <@(consumer,token3)
    makeDeleteRequest(request)
  }

  def getAccountPermissions(bankId : String, accountId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions" <@(consumer,token)
    makeGetRequest(request)
  }

  def getAccountPermissionsWithoutToken(bankId : String, accountId : String) : APIResponse= {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"
    makeGetRequest(request)
  }

  def getAccountPermissionsWithoutOwnerAccess(bankId : String, accountId : String) : APIResponse= {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions" <@(consumer,token2)
    makeGetRequest(request)
  }

  def getUserAccountPermission(bankId : String, accountId : String, userId : String) : APIResponse= {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId <@(consumer,token)
    makeGetRequest(request)
  }

  def getUserAccountPermissionWithoutToken(bankId : String, accountId : String, userId : String) : APIResponse= {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId
    makeGetRequest(request)
  }

  def grantUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" / viewId).POST.<@(consumer,token)
    makePostRequest(request)
  }

  def grantUserAccessToViewWithWrongUser(bankId : String, accountId : String, userId : String, viewId : String) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" / viewId).POST.<@(consumer,token3)
    makePostRequest(request)
  }

  def grantUserAccessToViews(bankId : String, accountId : String, userId : String, viewIds : List[String]) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views").POST.<@(consumer,token)
    val viewsJson = ViewIdsJson(viewIds)
    makePostRequest(request, write(viewsJson))
  }

  def grantUserAccessToViewsWithWrongUser(bankId : String, accountId : String, userId : String, viewIds : List[String]) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views").POST.<@(consumer,token3)
    val viewsJson = ViewIdsJson(viewIds)
    makePostRequest(request, write(viewsJson))
  }

  def revokeUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" / viewId).DELETE.<@(consumer,token)
    makeDeleteRequest(request)
  }

  def revokeUserAccessToViewWithoutOwnerAccess(bankId : String, accountId : String, userId : String, viewId : String) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" / viewId).DELETE.<@(consumer,token3)
    makeDeleteRequest(request)
  }

  def revokeUserAccessToAllViews(bankId : String, accountId : String, userId : String) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views").DELETE.<@(consumer,token)
    makeDeleteRequest(request)
  }

  def revokeUserAccessToAllViewsWithoutOwnerAccess(bankId : String, accountId : String, userId : String) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ userId / "views" ).DELETE.<@(consumer,token3)
    makeDeleteRequest(request)
  }

  def getTheOtherBankAccounts(bankId : String, accountId : String, viewId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" <@(consumer,token)
    makeGetRequest(request)
  }

  def getTheOtherBankAccountsWithoutToken(bankId : String, accountId : String, viewId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts"
    makeGetRequest(request)
  }

  def getTheOtherBankAccountsWithWrongUser(bankId : String, accountId : String, viewId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" <@(consumer,token3)
    makeGetRequest(request)
  }

  def getTheOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId <@(consumer,token)
    makeGetRequest(request)
  }

  def getTheOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId
    makeGetRequest(request)
  }

  def getTheOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId <@(consumer,token3)
    makeGetRequest(request)
  }

  def getMetadataOfOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" <@(consumer,token)
    makeGetRequest(request)
  }

  def getMetadataOfOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata"
    makeGetRequest(request)
  }

  def getMetadataOfOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" <@(consumer,token3)
    makeGetRequest(request)
  }

  def getThePublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias" <@(consumer, token)
    makeGetRequest(request)
  }

  def getThePublicAliasForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias"
    makeGetRequest(request)
  }

  def getThePublicAliasForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postAPublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").POST <@(consumer,token)
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }

  def postAPublicAliasForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias"
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }

  def postAPublicAliasForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").POST <@(consumer,token3)
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }

  def updateThePublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").PUT <@(consumer, token)
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }

  def updateThePublicAliasForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias"
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }

  def updateThePublicAliasForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").PUT <@(consumer, token3)
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }

  def deleteThePublicAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteThePublicAliasForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias"
    makeDeleteRequest(request)
  }

  def deleteThePublicAliasForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getThePrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias" <@(consumer, token)
    makeGetRequest(request)
  }

  def getThePrivateAliasForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias"
    makeGetRequest(request)
  }

  def getThePrivateAliasForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postAPrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").POST <@(consumer,token)
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }

  def postAPrivateAliasForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias"
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }

  def postAPrivateAliasForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").POST <@(consumer,token3)
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }

  def updateThePrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").PUT <@(consumer, token)
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }

  def updateThePrivateAliasForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias"
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }

  def updateThePrivateAliasForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").PUT <@(consumer, token3)
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }

  def deleteThePrivateAliasForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteThePrivateAliasForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias"
    makeDeleteRequest(request)
  }

  def deleteThePrivateAliasForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : String = {
    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId).body.extract[OtherAccountMetadataJSON].more_info
  }

  def postMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").POST <@(consumer,token)
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePostRequest(request, write(moreInfoJson))
  }

  def postMoreInfoForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info"
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePostRequest(request, write(moreInfoJson))
  }

  def postMoreInfoForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").POST <@(consumer,token3)
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePostRequest(request, write(moreInfoJson))
  }

  def updateMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").PUT <@(consumer, token)
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePutRequest(request, write(moreInfoJson))
  }

  def updateMoreInfoForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info"
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePutRequest(request, write(moreInfoJson))
  }

  def updateMoreInfoForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").PUT <@(consumer, token3)
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePutRequest(request, write(moreInfoJson))
  }

  def deleteMoreInfoForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteMoreInfoForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info"
    makeDeleteRequest(request)
  }

  def deleteMoreInfoForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "more_info").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : String = {
    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId).body.extract[OtherAccountMetadataJSON].URL
  }

  def postUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").POST <@(consumer,token)
    val urlJson = UrlJSON(url)
    makePostRequest(request, write(urlJson))
  }

  def postUrlForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url"
    val urlJson = UrlJSON(url)
    makePostRequest(request, write(urlJson))
  }

  def postUrlForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").POST <@(consumer,token3)
    val urlJson = UrlJSON(url)
    makePostRequest(request, write(urlJson))
  }

  def updateUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").PUT <@(consumer, token)
    val urlJson = UrlJSON(url)
    makePutRequest(request, write(urlJson))
  }

  def updateUrlForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url"
    val urlJson = UrlJSON(url)
    makePutRequest(request, write(urlJson))
  }

  def updateUrlForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").PUT <@(consumer, token3)
    val urlJson = UrlJSON(url)
    makePutRequest(request, write(urlJson))
  }

  def deleteUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteUrlForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url"
    makeDeleteRequest(request)
  }

  def deleteUrlForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "url").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : String = {
    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId).body.extract[OtherAccountMetadataJSON].image_URL
  }

  def postImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").POST <@(consumer,token)
    val imageUrlJson = ImageUrlJSON(imageUrl)
    makePostRequest(request, write(imageUrlJson))
  }

  def postImageUrlForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url"
    val imageUrlJson = ImageUrlJSON(imageUrl)
    makePostRequest(request, write(imageUrlJson))
  }

  def postImageUrlForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").POST <@(consumer,token3)
    val imageUrlJson = ImageUrlJSON(imageUrl)
    makePostRequest(request, write(imageUrlJson))
  }

  def updateImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").PUT <@(consumer, token)
    val imageUrlJson = ImageUrlJSON(imageUrl)
    makePutRequest(request, write(imageUrlJson))
  }

  def updateImageUrlForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url"
    val imageUrlJson = UrlJSON(imageUrl)
    makePutRequest(request, write(imageUrlJson))
  }

  def updateImageUrlForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").PUT <@(consumer, token3)
    val imageUrlJson = UrlJSON(imageUrl)
    makePutRequest(request, write(imageUrlJson))
  }

  def deleteImageUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteImageUrlForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url"
    makeDeleteRequest(request)
  }

  def deleteImageUrlForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "image_url").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : String = {
    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId).body.extract[OtherAccountMetadataJSON].open_corporates_URL
  }

  def postOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").POST <@(consumer,token)
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePostRequest(request, write(openCorporateUrlJson))
  }

  def postOpenCorporatesUrlForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url"
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePostRequest(request, write(openCorporateUrlJson))
  }

  def postOpenCorporatesUrlForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").POST <@(consumer,token3)
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePostRequest(request, write(openCorporateUrlJson))
  }

  def updateOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").PUT <@(consumer, token)
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePutRequest(request, write(openCorporateUrlJson))
  }

  def updateOpenCorporatesUrlForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url"
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePutRequest(request, write(openCorporateUrlJson))
  }

  def updateOpenCorporatesUrlForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").PUT <@(consumer, token3)
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePutRequest(request, write(openCorporateUrlJson))
  }

  def deleteOpenCorporatesUrlForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteOpenCorporatesUrlForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url"
    makeDeleteRequest(request)
  }

  def deleteOpenCorporatesUrlForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "open_corporates_url").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : LocationJSON = {
    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId).body.extract[OtherAccountMetadataJSON].corporate_location
  }

  def postCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").POST <@(consumer,token)
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePostRequest(request, write(corpLocationJson))
  }

  def postCorporateLocationForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location"
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePostRequest(request, write(corpLocationJson))
  }

  def postCorporateLocationForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").POST <@(consumer,token3)
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePostRequest(request, write(corpLocationJson))
  }

  def updateCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").PUT <@(consumer, token)
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePutRequest(request, write(corpLocationJson))
  }

  def updateCorporateLocationForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location"
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePutRequest(request, write(corpLocationJson))
  }

  def updateCorporateLocationForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").PUT <@(consumer, token3)
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePutRequest(request, write(corpLocationJson))
  }

  def deleteCorporateLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteCorporateLocationForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location"
    makeDeleteRequest(request)
  }

  def deleteCorporateLocationForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "corporate_location").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getPhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : LocationJSON = {
    getMetadataOfOneOtherBankAccount(bankId,accountId, viewId,otherBankAccountId).body.extract[OtherAccountMetadataJSON].physical_location
  }

  def postPhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").POST <@(consumer,token)
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePostRequest(request, write(physLocationJson))
  }

  def postPhysicalLocationForAnOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location"
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePostRequest(request, write(physLocationJson))
  }

  def postPhysicalLocationForAnOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").POST <@(consumer,token3)
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePostRequest(request, write(physLocationJson))
  }

  def updatePhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").PUT <@(consumer, token)
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePutRequest(request, write(physLocationJson))
  }

  def updatePhysicalLocationForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location"
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePutRequest(request, write(physLocationJson))
  }

  def updatePhysicalLocationForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").PUT <@(consumer, token3)
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePutRequest(request, write(physLocationJson))
  }

  def deletePhysicalLocationForOneOtherBankAccount(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deletePhysicalLocationForOneOtherBankAccountWithoutToken(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location"
    makeDeleteRequest(request)
  }

  def deletePhysicalLocationForOneOtherBankAccountWithWrongUser(bankId : String, accountId : String, viewId : String, otherBankAccountId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "physical_location").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getTransactions(bankId : String, accountId : String, viewId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumer,token)
    makeGetRequest(request)
  }

  def getTransaction(bankId : String, accountId : String, viewId : String, transactionId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "transaction" <@(consumer,token)
    makeGetRequest(request)
  }

  def getTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "transaction"
    makeGetRequest(request)
  }

  def getTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "transaction" <@(consumer,token3)
    makeGetRequest(request)
  }

  def getNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative" <@(consumer, token)
    makeGetRequest(request)
  }

  def getNarrativeForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative"
    makeGetRequest(request)
  }

  def getNarrativeForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").POST <@(consumer,token)
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePostRequest(request, write(narrativeJson))
  }

  def postNarrativeForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative"
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePostRequest(request, write(narrativeJson))
  }

  def postNarrativeForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").POST <@(consumer,token3)
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePostRequest(request, write(narrativeJson))
  }

  def updateNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").PUT <@(consumer, token)
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePutRequest(request, write(narrativeJson))
  }

  def updateNarrativeForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative"
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePutRequest(request, write(narrativeJson))
  }

  def updateNarrativeForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").PUT <@(consumer, token3)
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePutRequest(request, write(narrativeJson))
  }

  def deleteNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteNarrativeForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative"
    makeDeleteRequest(request)
  }

  def deleteNarrativeForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getCommentsForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" <@(consumer, token)
    makeGetRequest(request)
  }

  def getCommentsForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments"
    makeGetRequest(request)
  }

  def getCommentsForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments").POST <@(consumer,token)
    makePostRequest(request, write(comment))
  }

  def postCommentForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments"
    makePostRequest(request, write(comment))
  }

  def postCommentForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments").POST <@(consumer,token3)
    makePostRequest(request, write(comment))
  }

  def deleteCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, commentId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" / commentId).DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteCommentForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, commentId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" / commentId
    makeDeleteRequest(request)
  }

  def deleteCommentForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, commentId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" / commentId).DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getTagsForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" <@(consumer, token)
    makeGetRequest(request)
  }

  def getTagsForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags"
    makeGetRequest(request)
  }

  def getTagsForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postTagForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, tag: PostTransactionTagJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags").POST <@(consumer,token)
    makePostRequest(request, write(tag))
  }

  def postTagForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, tag: PostTransactionTagJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags"
    makePostRequest(request, write(tag))
  }

  def postTagForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, tag: PostTransactionTagJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags").POST <@(consumer,token3)
    makePostRequest(request, write(tag))
  }

  def deleteTagForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, tagId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" / tagId).DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteTagForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, tagId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" / tagId
    makeDeleteRequest(request)
  }

  def deleteTagForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, tagId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" / tagId).DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getImagesForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" <@(consumer, token)
    makeGetRequest(request)
  }

  def getImagesForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images"
    makeGetRequest(request)
  }

  def getImagesForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postImageForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, image: PostTransactionImageJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images").POST <@(consumer,token)
    makePostRequest(request, write(image))
  }

  def postImageForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, image: PostTransactionImageJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images"
    makePostRequest(request, write(image))
  }

  def postImageForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, image: PostTransactionImageJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images").POST <@(consumer,token3)
    makePostRequest(request, write(image))
  }

  def deleteImageForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, imageId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" / imageId).DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteImageForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, imageId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" / imageId
    makeDeleteRequest(request)
  }

  def deleteImageForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, imageId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" / imageId).DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where" <@(consumer, token)
    makeGetRequest(request)
  }

  def getWhereForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where"
    makeGetRequest(request)
  }

  def getWhereForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where" <@(consumer, token3)
    makeGetRequest(request)
  }

  def postWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").POST <@(consumer,token)
    val whereJson = PostTransactionWhereJSON(where)
    makePostRequest(request, write(whereJson))
  }

  def postWhereForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where"
    val whereJson = PostTransactionWhereJSON(where)
    makePostRequest(request, write(whereJson))
  }

  def postWhereForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").POST <@(consumer,token3)
    val whereJson = PostTransactionWhereJSON(where)
    makePostRequest(request, write(whereJson))
  }

  def updateWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").PUT <@(consumer, token)
    val whereJson = PostTransactionWhereJSON(where)
    makePutRequest(request, write(whereJson))
  }

  def updateWhereForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where"
    val whereJson = PostTransactionWhereJSON(where)
    makePutRequest(request, write(whereJson))
  }

  def updateWhereForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").PUT <@(consumer, token3)
    val whereJson = PostTransactionWhereJSON(where)
    makePutRequest(request, write(whereJson))
  }

  def deleteWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").DELETE <@(consumer, token)
    makeDeleteRequest(request)
  }

  def deleteWhereForOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where"
    makeDeleteRequest(request)
  }

  def deleteWhereForOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").DELETE <@(consumer, token3)
    makeDeleteRequest(request)
  }

  def getTheOtherBankAccountOfOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "other_account" <@(consumer, token)
    makeGetRequest(request)
  }

  def getTheOtherBankAccountOfOneTransactionWithoutToken(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "other_account"
    makeGetRequest(request)
  }

  def getTheOtherBankAccountOfOneTransactionWithWrongUser(bankId : String, accountId : String, viewId : String, transactionId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "other_account" <@(consumer, token3)
    makeGetRequest(request)
  }


/************************ the tests ************************/
  feature("base line URL works"){
    scenario("we get the api information", API1_2, APIInfo) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getAPIInfo
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val apiInfo = reply.body.extract[APIInfoJSON]
      apiInfo.version should equal ("1.2")
      apiInfo.git_commit.nonEmpty should equal (true)
    }
  }

  feature("Information about the hosted banks"){
    scenario("we get the hosted banks information", API1_2, GetHostedBanks) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getBanksInfo
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val banksInfo = reply.body.extract[BanksJSON]
      banksInfo.banks.foreach(b => {
        b.id.nonEmpty should equal (true)
      })
    }
  }

  feature("Information about one hosted bank"){
    scenario("we get the hosted bank information", API1_2, GetHostedBank) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getBankInfo(randomBank)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val bankInfo = reply.body.extract[BankJSON]
      bankInfo.id.nonEmpty should equal (true)
    }

    scenario("we don't get the hosted bank information", API1_2, GetHostedBank) {
      Given("We will not use an access token and request a random bankId")
      When("the request is sent")
      val reply = getBankInfo(randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Information about all the bank accounts"){
    scenario("we get only the public bank accounts", API1_2, GetBankAccounts) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getBankAccounts(randomBank)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val publicAccountsInfo = reply.body.extract[AccountsJSON]
      And("some fields should not be empty")
      publicAccountsInfo.accounts.foreach(a => {
        a.id.nonEmpty should equal (true)
        a.views_available.nonEmpty should equal (true)
        a.views_available.foreach(
          //check that all the views are public
          v => v.is_public should equal (true)
        )
      })

    }
    scenario("we get the bank accounts the user have access to", API1_2, GetBankAccounts) {
      Given("We will use an access token")
      When("the request is sent")
      val reply = getBankAccountsWithToken(randomBank)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val accountsInfo = reply.body.extract[AccountsJSON]
      And("some fields should not be empty")
      accountsInfo.accounts.foreach(a => {
        a.id.nonEmpty should equal (true)
        a.views_available.nonEmpty should equal (true)
      })
    }
  }

  feature("Information about the public bank accounts"){
    scenario("we get the public bank accounts", API1_2, GetPublicBankAccounts) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getPublicAccounts(randomBank)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val publicAccountsInfo = reply.body.extract[AccountsJSON]
      And("some fields should not be empty")
      publicAccountsInfo.accounts.foreach(a => {
        a.id.nonEmpty should equal (true)
        a.views_available.nonEmpty should equal (true)
        a.views_available.foreach(
          //check that all the views are public
          v => v.is_public should equal (true)
        )
      })
    }
  }

  feature("Information about the private bank accounts"){
    scenario("we get the private bank accounts", API1_2, GetPrivateBankAccounts) {
      Given("We will use an access token")
      When("the request is sent")
      val reply = getPrivateAccounts(randomBank)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      And("some fields should not be empty")
      val privateAccountsInfo = reply.body.extract[AccountsJSON]
      privateAccountsInfo.accounts.foreach(a => {
        a.id.nonEmpty should equal (true)
        a.views_available.nonEmpty should equal (true)
      })
    }
    scenario("we don't get the private bank accounts", API1_2, GetPrivateBankAccounts) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getPrivateAccountsWithOutToken(randomBank)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Information about a bank account"){
    scenario("we get data without using an access token", API1_2, GetBankAccount) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPublicAccount(bankId)
      val randomPosition = nextInt(bankAccount.views_available.size)
      val view = bankAccount.views_available.toList(randomPosition)
      When("the request is sent")
      val reply = getPublicBankAccountDetails(bankId, bankAccount.id, view.id)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      And("some fields should not be empty")
      val publicAccountDetails = reply.body.extract[ModeratedAccountJSON]
      publicAccountDetails.id.nonEmpty should equal (true)
      publicAccountDetails.bank_id.nonEmpty should equal (true)
      publicAccountDetails.views_available.nonEmpty should equal (true)
    }

    scenario("we get data by using an access token", API1_2, GetBankAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val randomPosition = nextInt(bankAccount.views_available.size)
      val view = bankAccount.views_available.toList(randomPosition)
      When("the request is sent")
      val reply = getPrivateBankAccountDetails(bankId, bankAccount.id, view.id)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val privateAccountDetails = reply.body.extract[ModeratedAccountJSON]
      And("some fields should not be empty")
      privateAccountDetails.id.nonEmpty should equal (true)
      privateAccountDetails.bank_id.nonEmpty should equal (true)
      privateAccountDetails.views_available.nonEmpty should equal (true)
    }
  }

  feature("List of the views of specific bank account"){
    scenario("We will get the list of the available views on a bank account", API1_2, GetViews) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      reply.body.extract[ViewsJSON]
    }

    scenario("We will not get the list of the available views on a bank account due to missing token", API1_2, GetViews) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViewsWithoutToken(bankId, bankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not get the list of the available views on a bank account due to insufficient privileges", API1_2, GetViews) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViewsWithoutOwnerAccess(bankId, bankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }
  feature("Create a view on a bank account"){
    scenario("we will create a view on a bank account", API1_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val viewsBefore = getAccountViews(bankId, bankAccount.id).body.extract[ViewsJSON].views
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view)
      Then("we should get a 201 code")
      reply.code should equal (201)
      reply.body.extract[ViewJSON]
      And("we should get a new view")
      val viewsAfter = getAccountViews(bankId, bankAccount.id).body.extract[ViewsJSON].views
      viewsBefore.size should equal (viewsAfter.size -1)
    }

    scenario("We will not create a view on a bank account due to missing token", API1_2, PostView) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postViewWithoutToken(bankId, bankAccount.id, view)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view on a bank account due to insufficient privileges", API1_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postViewWithoutOwnerAccess(bankId, bankAccount.id, view)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view because the bank account does not exist", API1_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postViewWithoutOwnerAccess(bankId, randomString(3), view)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }
  feature("Delete a view on a bank account"){
    scenario("we will delete a view on a bank account", API1_2, DeleteView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = postView(bankId, bankAccount.id, randomView(true, "")).body.extract[ViewJSON]
      val viewsBefore = getAccountViews(bankId, bankAccount.id).body.extract[ViewsJSON].views
      When("the request is sent")
      val reply = deleteView(bankId, bankAccount.id, view.id)
      Then("we should get a 204 code")
      reply.code should equal (204)
      And("the views should be updated")
      val viewsAfter = getAccountViews(bankId, bankAccount.id).body.extract[ViewsJSON].views
      viewsBefore.size should equal (viewsAfter.size +1)
    }

    scenario("We will not delete a view on a bank account due to missing token", API1_2, DeleteView) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = deleteViewWithoutToken(bankId, bankAccount.id, view)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not delete a view on a bank account due to insufficient privileges", API1_2, DeleteView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = deleteViewWithoutOwnerAccess(bankId, bankAccount.id, view)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not delete a view on a bank account it does not exist", API1_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = deleteView(bankId, bankAccount.id, randomString(3))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Information about the permissions of a specific bank account"){
    scenario("we will get one bank account permissions by using an access token", API1_2, GetPermissions) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountPermissions(bankId, bankAccount.id)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      reply.body.extract[PermissionsJSON]
    }

    scenario("we will not get one bank account permissions", API1_2, GetPermissions) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountPermissionsWithoutToken(bankId, bankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get one bank account permissions by using an other access token", API1_2, GetPermissions) {
      Given("We will use an access token, but that does not grant owner view")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountPermissionsWithoutOwnerAccess(bankId, bankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Information about the permissions of a specific user on a specific bank account"){
    scenario("we will get the permissions by using an access token", API1_2, GetPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val permission = randomAccountPermission(bankId, bankAccount.id)
      val userID = permission.user.id
      When("the request is sent")
      val reply = getUserAccountPermission(bankId, bankAccount.id, userID)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val viewsInfo = reply.body.extract[ViewsJSON]
      And("some fields should not be empty")
      viewsInfo.views.foreach(v => v.id.nonEmpty should equal (true))
    }

    scenario("we will not get the permissions of a specific user", API1_2, GetPermission) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val permission = randomAccountPermission(bankId, bankAccount.id)
      val userID = permission.user.id
      When("the request is sent")
      val reply = getUserAccountPermissionWithoutToken(bankId, bankAccount.id, userID)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the permissions of a random user", API1_2, GetPermission) {
      Given("We will use an access token with random user id")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getUserAccountPermission(bankId, bankAccount.id, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Grant a user access to a view on a bank account"){
    scenario("we will grant a user access to a view on an bank account", API1_2, PostPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val userId = user2.email
      val reply = grantUserAccessToView(bankId, bankAccount.id, userId, randomViewPermalink(bankId, bankAccount))
      Then("we should get a 201 ok code")
      reply.code should equal (201)
      val viewInfo = reply.body.extract[ViewJSON]
      And("some fields should not be empty")
      viewInfo.id.nonEmpty should equal (true)
    }

    scenario("we cannot grant a user access to a view on an bank account because the user does not exist", API1_2, PostPermission) {
      Given("We will use an access token with a random user Id")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = grantUserAccessToView(bankId, bankAccount.id, randomString(5), randomViewPermalink(bankId, bankAccount))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we cannot grant a user access to a view on an bank account because the view does not exist", API1_2, PostPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user2.email
      When("the request is sent")
      val reply = grantUserAccessToView(bankId, bankAccount.id, userId, randomString(5))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we cannot grant a user access to a view on an bank account because the user does not have owner view access", API1_2, PostPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user2.email
      When("the request is sent")
      val reply = grantUserAccessToViewWithWrongUser(bankId, bankAccount.id, userId, randomViewPermalink(bankId, bankAccount))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Grant a user access to a list of views on a bank account"){
    scenario("we will grant a user access to a list of views on an bank account", API1_2, PostPermissions) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user3.email
      val viewsIdsToGrant = randomViewsIdsToGrant(bankId, bankAccount.id)
      When("the request is sent")
      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      Then("we should get a 201 ok code")
      reply.code should equal (201)
      val viewsInfo = reply.body.extract[ViewsJSON]
      And("some fields should not be empty")
      viewsInfo.views.foreach(v => v.id.nonEmpty should equal (true))
      And("the granted views should be the same")
      viewsIdsToGrant.toSet should equal(viewsInfo.views.map(_.id).toSet)
      //we revoke access to the granted views for the next tests
      revokeUserAccessToAllViews(bankId, bankAccount.id, userId)
    }

    scenario("we cannot grant a user access to a list of views on an bank account because the user does not exist", API1_2, PostPermissions) {
      Given("We will use an access token with a random user Id")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = randomString(5)
      val viewsIdsToGrant= randomViewsIdsToGrant(bankId, bankAccount.id)
      When("the request is sent")
      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we cannot grant a user access to a list of views on an bank account because they don't exist", API1_2, PostPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user3.email
      val viewsIdsToGrant= List(randomString(3),randomString(3))
      When("the request is sent")
      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we cannot grant a user access to a list of views on an bank account because some views don't exist", API1_2, PostPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user3.email
      val viewsIdsToGrant= randomViewsIdsToGrant(bankId, bankAccount.id) ++ List(randomString(3),randomString(3))
      When("the request is sent")
      val reply = grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we cannot grant a user access to a list of views on an bank account because the user does not have owner view access", API1_2, PostPermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user3.email
      val viewsIdsToGrant= randomViewsIdsToGrant(bankId, bankAccount.id) ++ List(randomString(3),randomString(3))
      When("the request is sent")
      val reply = grantUserAccessToViewsWithWrongUser(bankId, bankAccount.id, userId, viewsIdsToGrant)
      Then("we should get a 400 ok code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Revoke a user access to a view on a bank account"){
    scenario("we will revoke the access of a user to a view on an bank account", API1_2, DeletePermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user2.email
      val viewId = randomViewPermalink(bankId, bankAccount)
      val viewsIdsToGrant = viewId :: Nil
      grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      When("the request is sent")
      val reply = revokeUserAccessToView(bankId, bankAccount.id, userId, viewId)
      Then("we should get a 204 no content code")
      reply.code should equal (204)
    }

    scenario("we cannot revoke the access to a user that does not exist", API1_2, DeletePermission) {
      Given("We will use an access token with a random user Id")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = revokeUserAccessToView(bankId, bankAccount.id, randomString(5), randomViewPermalink(bankId, bankAccount))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
    }

    scenario("we cannot revoke a user access to a view on an bank account because the view does not exist", API1_2, DeletePermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId =user2.email
      When("the request is sent")
      val reply = revokeUserAccessToView(bankId, bankAccount.id, userId, randomString(5))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
    }

    scenario("we cannot revoke a user access to a view on an bank account because the user does not have owner view access", API1_2, DeletePermission) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user2.email
      When("the request is sent")
      val reply = revokeUserAccessToViewWithoutOwnerAccess(bankId, bankAccount.id, userId, randomViewPermalink(bankId, bankAccount))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
    }
  }
  feature("Revoke a user access to all the views on a bank account"){
    scenario("we will revoke the access of a user to all the views on an bank account", API1_2, DeletePermissions) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user2.email
      val viewId = randomViewPermalink(bankId, bankAccount)
      val viewsIdsToGrant = viewId :: Nil
      grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      When("the request is sent")
      val reply = revokeUserAccessToAllViews(bankId, bankAccount.id, userId)
      Then("we should get a 204 no content code")
      reply.code should equal (204)
    }

    scenario("we cannot revoke the access to a user that does not exist", API1_2, DeletePermissions) {
      Given("We will use an access token with a random user Id")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = revokeUserAccessToAllViews(bankId, bankAccount.id, randomString(5))
      Then("we should get a 400 ok code")
      reply.code should equal (400)
    }

    scenario("we cannot revoke a user access to a view on an bank account because the user does not have owner view access", API1_2, DeletePermissions) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val userId = user2.email
      val viewId = randomViewPermalink(bankId, bankAccount)
      val viewsIdsToGrant = viewId :: Nil
      grantUserAccessToViews(bankId, bankAccount.id, userId, viewsIdsToGrant)
      When("the request is sent")
      val reply = revokeUserAccessToAllViewsWithoutOwnerAccess(bankId, bankAccount.id, userId)
      Then("we should get a 400 ok code")
      reply.code should equal (400)
    }
  }

  feature("We get the list of the other bank accounts linked with a bank account"){
    scenario("we will get the other bank accounts of a bank account", API1_2, GetOtherBankAccounts) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getTheOtherBankAccounts(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount))
      Then("we should get a 200 code")
      reply.code should equal (200)
      val accountsJson = reply.body.extract[OtherAccountsJSON]
      And("some fields should not be empty")
      accountsJson.other_accounts.foreach( a =>
        a.id.nonEmpty should equal (true)
      )
    }

    scenario("we will not get the other bank accounts of a bank account due to missing access token", API1_2, GetOtherBankAccounts) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getTheOtherBankAccountsWithoutToken(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the other bank accounts of a bank account because the user does not have enough privileges", API1_2, GetOtherBankAccounts) {
      Given("We will use an access token ")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getTheOtherBankAccountsWithWrongUser(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the other bank accounts of a bank account because the view does not exist", API1_2, GetOtherBankAccounts) {
      Given("We will use an access token ")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getTheOtherBankAccounts(bankId, bankAccount.id, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We get one specific other bank account among the other accounts "){
    scenario("we will get one random other bank account of a bank account", API1_2, GetOtherBankAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      val accountJson = reply.body.extract[OtherAccountJSON]
      And("some fields should not be empty")
      accountJson.id.nonEmpty should equal (true)
    }

    scenario("we will not get one random other bank account of a bank account due to a missing token", API1_2, GetOtherBankAccount) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get one random other bank account of a bank account because the user does not have enough privileges", API1_2, GetOtherBankAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get one random other bank account of a bank account because the view does not exist", API1_2, GetOtherBankAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, randomViewPermalink(bankId, bankAccount))
      When("the request is sent")
      val reply = getTheOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get one random other bank account of a bank account because the account does not exist", API1_2, GetOtherBankAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We get the metadata of one specific other bank account among the other accounts"){
    scenario("we will get the metadata of one random other bank account", API1_2, GetOtherBankAccountMetadata) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("some fields should not be empty")
      reply.body.extract[OtherAccountMetadataJSON]
    }

    scenario("we will not get the metadata of one random other bank account due to a missing token", API1_2, GetOtherBankAccountMetadata) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getMetadataOfOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the metadata of one random other bank account because the user does not have enough privileges", API1_2, GetOtherBankAccountMetadata) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getMetadataOfOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the metadata of one random other bank account because the view does not exist", API1_2, GetOtherBankAccountMetadata) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the metadata of one random other bank account because the account does not exist", API1_2, GetOtherBankAccountMetadata) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getMetadataOfOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We get the public alias of one specific other bank account among the other accounts "){
    scenario("we will get the public alias of one random other bank account", API1_2, GetPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      reply.body.extract[AliasJSON]
    }

    scenario("we will not get the public alias of one random other bank account due to a missing token", API1_2, GetPublicAlias) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePublicAliasForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the public alias of one random other bank account because the user does not have enough privileges", API1_2, GetPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePublicAliasForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the public alias of one random other bank account because the view does not exist", API1_2, GetPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the public alias of one random other bank account because the account does not exist", API1_2, GetPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post a public alias for one specific other bank"){
    scenario("we will post a public alias for one random other bank account", API1_2, PostPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomAlias = randomString(5)
      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the alias should be changed")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a public alias for a random other bank account due to a missing token", API1_2, PostPublicAlias) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPublicAliasForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a public alias for a random other bank account because the user does not have enough privileges", API1_2, PostPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPublicAliasForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a public alias for a random other bank account because the view does not exist", API1_2, PostPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a public alias for a random other bank account because the account does not exist", API1_2, PostPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the public alias for one specific other bank"){
    scenario("we will update the public alias for one random other bank account", API1_2, PutPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomAlias = randomString(5)
      val putReply = updateThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the alias should be changed")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should equal (theAliasAfterThePost.alias)
    }

    scenario("we will not update the public alias for a random other bank account due to a missing token", API1_2, PutPublicAlias) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val putReply = updateThePublicAliasForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not update the public alias for a random other bank account because the user does not have enough privileges", API1_2, PutPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val putReply = updateThePublicAliasForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the public alias for a random other bank account because the account does not exist", API1_2, PutPublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomAlias = randomString(5)
      When("the request is sent")
      val putReply = updateThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the public alias for one specific other bank"){
    scenario("we will delete the public alias for one random other bank account", API1_2, DeletePublicAlias) {
      Given("We will use an access token and will set an alias first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      When("the delete request is sent")
      val deleteReply = deleteThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the public alias should be null")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
      theAliasAfterTheDelete.alias should equal (null)
    }
    scenario("we will not delete the public alias for a random other bank account due to a missing token", API1_2, DeletePublicAlias) {
      Given("We will not use an access token and will set an alias first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      When("the delete request is sent")
      val deleteReply = deleteThePublicAliasForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the public alias should not be null")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
      theAliasAfterTheDelete.alias should not equal (null)
    }
    scenario("we will not delete the public alias for a random other bank account because the user does not have enough privileges", API1_2, DeletePublicAlias) {
      Given("We will use an access token and will set an alias first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      postAPublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      When("the delete request is sent")
      val deleteReply = deleteThePublicAliasForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the public alias should not be null")
      val getReply = getThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
      theAliasAfterTheDelete.alias should not equal (null)
    }
    scenario("we will not delete the public alias for a random other bank account because the account does not exist", API1_2, DeletePublicAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomAlias = randomString(5)
      When("the delete request is sent")
      val deleteReply = deleteThePublicAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We get the private alias of one specific other bank account among the other accounts "){
    scenario("we will get the private alias of one random other bank account", API1_2, GetPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      reply.body.extract[AliasJSON]
    }

    scenario("we will not get the private alias of one random other bank account due to a missing token", API1_2, GetPrivateAlias) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePrivateAliasForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the private alias of one random other bank account because the user does not have enough privileges", API1_2, GetPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePrivateAliasForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the private alias of one random other bank account because the view does not exist", API1_2, GetPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the private alias of one random other bank account because the account does not exist", API1_2, GetPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)

      When("the request is sent")
      val reply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post a private alias for one specific other bank"){
    scenario("we will post a private alias for one random other bank account", API1_2, PostPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomAlias = randomString(5)
      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the alias should be changed")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a private alias for a random other bank account due to a missing token", API1_2, PostPrivateAlias) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPrivateAliasForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a private alias for a random other bank account because the user does not have enough privileges", API1_2, PostPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPrivateAliasForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a private alias for a random other bank account because the view does not exist", API1_2, PostPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not post a private alias for a random other bank account because the account does not exist", API1_2, PostPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomAlias = randomString(5)
      When("the request is sent")
      val postReply = postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the private alias for one specific other bank"){
    scenario("we will update the private alias for one random other bank account", API1_2, PutPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomAlias = randomString(5)
      val putReply = updateThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the alias should be changed")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should equal (theAliasAfterThePost.alias)
    }

    scenario("we will not update the private alias for a random other bank account due to a missing token", API1_2, PutPrivateAlias) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val putReply = updateThePrivateAliasForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the alias should not be changed")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterThePost : AliasJSON = getReply.body.extract[AliasJSON]
      randomAlias should not equal (theAliasAfterThePost.alias)
    }

    scenario("we will not update the private alias for a random other bank account because the user does not have enough privileges", API1_2, PutPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      When("the request is sent")
      val putReply = updateThePrivateAliasForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the private alias for a random other bank account because the account does not exist", API1_2, PutPrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomAlias = randomString(5)
      When("the request is sent")
      val putReply = updateThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomAlias)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the private alias for one specific other bank"){
    scenario("we will delete the private alias for one random other bank account", API1_2, DeletePrivateAlias) {
      Given("We will use an access token and will set an alias first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      When("the delete request is sent")
      val deleteReply = deleteThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the Private alias should be null")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
      theAliasAfterTheDelete.alias should equal (null)
    }
    scenario("we will not delete the private alias for a random other bank account due to a missing token", API1_2, DeletePrivateAlias) {
      Given("We will not use an access token and will set an alias first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      When("the delete request is sent")
      val deleteReply = deleteThePrivateAliasForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the Private alias should not be null")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
      theAliasAfterTheDelete.alias should not equal (null)
    }
    scenario("we will not delete the private alias for a random other bank account because the user does not have enough privileges", API1_2, DeletePrivateAlias) {
      Given("We will use an access token and will set an alias first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomAlias = randomString(5)
      postAPrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomAlias)
      When("the delete request is sent")
      val deleteReply = deleteThePrivateAliasForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the Private alias should not be null")
      val getReply = getThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      val theAliasAfterTheDelete : AliasJSON = getReply.body.extract[AliasJSON]
      theAliasAfterTheDelete.alias should not equal (null)
    }
    scenario("we will not delete the private alias for a random other bank account because the account does not exist", API1_2, DeletePrivateAlias) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomAlias = randomString(5)
      When("the delete request is sent")
      val deleteReply = deleteThePrivateAliasForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We post more information for one specific other bank"){
    scenario("we will post more information for one random other bank account", API1_2, PostMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomInfo = randomString(20)
      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the information should be changed")
      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomInfo should equal (moreInfo)
    }

    scenario("we will not post more information for a random other bank account due to a missing token", API1_2, PostMoreInfo) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      When("the request is sent")
      val postReply = postMoreInfoForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the information should not be changed")
      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomInfo should not equal (moreInfo)
    }

    scenario("we will not post more information for a random other bank account because the user does not have enough privileges", API1_2, PostMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      When("the request is sent")
      val postReply = postMoreInfoForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the information should not be changed")
      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomInfo should not equal (moreInfo)
    }

    scenario("we will not post more information for a random other bank account because the view does not exist", API1_2, PostMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      When("the request is sent")
      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomInfo)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the information should not be changed")
      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomInfo should not equal (moreInfo)
    }

    scenario("we will not post more information for a random other bank account because the account does not exist", API1_2, PostMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomInfo = randomString(20)
      When("the request is sent")
      val postReply = postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomInfo)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the information for one specific other bank"){
    scenario("we will update the information for one random other bank account", API1_2, PutMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomInfo = randomString(20)
      val putReply = updateMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the information should be changed")
      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomInfo should equal (moreInfo)
    }

    scenario("we will not update the information for a random other bank account due to a missing token", API1_2, PutMoreInfo) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      When("the request is sent")
      val putReply = updateMoreInfoForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the information should not be changed")
      val moreInfo = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomInfo should not equal (moreInfo)
    }

    scenario("we will not update the information for a random other bank account because the user does not have enough privileges", API1_2, PutMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      When("the request is sent")
      val putReply = updateMoreInfoForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the information for a random other bank account because the account does not exist", API1_2, PutMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomInfo = randomString(20)
      When("the request is sent")
      val putReply = updateMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomInfo)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

 feature("We delete the information for one specific other bank"){
    scenario("we will delete the information for one random other bank account", API1_2, DeleteMoreInfo) {
      Given("We will use an access token and will set an info first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      When("the delete request is sent")
      val deleteReply = deleteMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the info should be null")
      val infoAfterDelete = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      infoAfterDelete should equal (null)
    }

    scenario("we will not delete the information for a random other bank account due to a missing token", API1_2, DeleteMoreInfo) {
      Given("We will not use an access token and will set an info first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      When("the delete request is sent")
      val deleteReply = deleteMoreInfoForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the info should not be null")
      val infoAfterDelete = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      infoAfterDelete should not equal (null)
    }

    scenario("we will not delete the information for a random other bank account because the user does not have enough privileges", API1_2, DeleteMoreInfo) {
      Given("We will use an access token and will set an info first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomInfo = randomString(20)
      postMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomInfo)
      When("the delete request is sent")
      val deleteReply = deleteMoreInfoForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the info should not be null")
      val infoAfterDelete = getMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      infoAfterDelete should not equal (null)
    }

    scenario("we will not delete the information for a random other bank account because the account does not exist", API1_2, DeleteMoreInfo) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomInfo = randomString(20)
      When("the delete request is sent")
      val deleteReply = deleteMoreInfoForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We post the url for one specific other bank"){
    scenario("we will post the url for one random other bank account", API1_2, PostURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomURL = randomString(20)
      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the url should be changed")
      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should equal (url)
    }

    scenario("we will not post the url for a random other bank account due to a missing token", API1_2, PostURL) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postUrlForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the url should not be changed")
      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not post the url for a random other bank account because the user does not have enough privileges", API1_2, PostURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postUrlForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the url should not be changed")
      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not post the url for a random other bank account because the view does not exist", API1_2, PostURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the url should not be changed")
      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not post the url for a random other bank account because the account does not exist", API1_2, PostURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the url for one specific other bank"){
    scenario("we will update the url for one random other bank account", API1_2, PutURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomURL = randomString(20)
      val putReply = updateUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the url should be changed")
      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should equal (url)
    }

    scenario("we will not update the url for a random other bank account due to a missing token", API1_2, PutURL) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val putReply = updateUrlForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the url should not be changed")
      val url = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not update the url for a random other bank account because the user does not have enough privileges", API1_2, PutURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val putReply = updateUrlForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the url for a random other bank account because the account does not exist", API1_2, PutURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomURL = randomString(20)
      When("the request is sent")
      val putReply = updateUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the url for one specific other bank"){
    scenario("we will delete the url for one random other bank account", API1_2, DeleteURL) {
      Given("We will use an access token and will set an open corporates url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      When("the delete request is sent")
      val deleteReply = deleteUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the url should be null")
      val urlAfterDelete = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should equal (null)
    }

    scenario("we will not delete the url for a random other bank account due to a missing token", API1_2, DeleteURL) {
      Given("We will not use an access token and will set an open corporates url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      When("the delete request is sent")
      val deleteReply = deleteUrlForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the url should not be null")
      val urlAfterDelete = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should not equal (null)
    }

    scenario("we will not delete the url for a random other bank account because the user does not have enough privileges", API1_2, DeleteURL) {
      Given("We will use an access token and will set an open corporates url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      postUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      When("the delete request is sent")
      val deleteReply = deleteUrlForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the url should not be null")
      val urlAfterDelete = getUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should not equal (null)
    }

    scenario("we will not delete the url for a random other bank account because the account does not exist", API1_2, DeleteURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomURL = randomString(20)
      When("the delete request is sent")
      val deleteReply = deleteUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We post the image url for one specific other bank"){
    scenario("we will post the image url for one random other bank account", API1_2, PostImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomImageURL = randomString(20)
      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the image url should be changed")
      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomImageURL should equal (url)
    }

    scenario("we will not post the image url for a random other bank account due to a missing token", API1_2, PostImageURL) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val postReply = postImageUrlForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image url should not be changed")
      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomImageURL should not equal (url)
    }

    scenario("we will not post the image url for a random other bank account because the user does not have enough privileges", API1_2, PostImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val postReply = postImageUrlForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image url should not be changed")
      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomImageURL should not equal (url)
    }

    scenario("we will not post the image url for a random other bank account because the view does not exist", API1_2, PostImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomImageURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image url should not be changed")
      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomImageURL should not equal (url)
    }

    scenario("we will not post the image url for a random other bank account because the account does not exist", API1_2, PostImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val postReply = postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomImageURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the image url for one specific other bank"){
    scenario("we will update the image url for one random other bank account", API1_2, PutImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomImageURL = randomString(20)
      val putReply = updateImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the image url should be changed")
      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomImageURL should equal (url)
    }

    scenario("we will not update the image url for a random other bank account due to a missing token", API1_2, PutImageURL) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val putReply = updateImageUrlForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image url should not be changed")
      val url = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomImageURL should not equal (url)
    }

    scenario("we will not update the image url for a random other bank account because the user does not have enough privileges", API1_2, PutImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val putReply = updateImageUrlForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the image url for a random other bank account because the account does not exist", API1_2, PutImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomImageURL = randomString(20)
      When("the request is sent")
      val putReply = updateImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomImageURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the image url for one specific other bank"){
    scenario("we will delete the image url for one random other bank account", API1_2, DeleteImageURL) {
      Given("We will use an access token and will set a url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      When("the delete request is sent")
      val deleteReply = deleteImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the image url should be null")
      val urlAfterDelete = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should equal (null)
    }

    scenario("we will not delete the image url for a random other bank account due to a missing token", API1_2, DeleteImageURL) {
      Given("We will not use an access token and will set a url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      When("the delete request is sent")
      val deleteReply = deleteImageUrlForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the image url should not be null")
      val urlAfterDelete = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should not equal (null)
    }

    scenario("we will not delete the image url for a random other bank account because the user does not have enough privileges", API1_2, DeleteImageURL) {
      Given("We will use an access token and will set a url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomImageURL = randomString(20)
      postImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomImageURL)
      When("the delete request is sent")
      val deleteReply = deleteImageUrlForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the image url should not be null")
      val urlAfterDelete = getImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should not equal (null)
    }

    scenario("we will not delete the image url for a random other bank account because the account does not exist", API1_2, DeleteImageURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomImageURL = randomString(20)
      When("the delete request is sent")
      val deleteReply = deleteImageUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We post the open corporates url for one specific other bank"){
    scenario("we will post the open corporates url for one random other bank account", API1_2, PostOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomURL = randomString(20)
      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the open corporates url should be changed")
      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should equal (url)
    }

    scenario("we will not post the open corporates url for a random other bank account due to a missing token", API1_2, PostOpenCorporatesURL) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postOpenCorporatesUrlForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the open corporates url should not be changed")
      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not post the open corporates url for a random other bank account because the user does not have enough privileges", API1_2, PostOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postOpenCorporatesUrlForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the open corporates url should not be changed")
      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not post the open corporates url for a random other bank account because the view does not exist", API1_2, PostOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the open corporates url should not be changed")
      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not post the open corporates url for a random other bank account because the account does not exist", API1_2, PostOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomURL = randomString(20)
      When("the request is sent")
      val postReply = postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the open corporates url for one specific other bank"){
    scenario("we will update the open corporates url for one random other bank account", API1_2, PutOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomURL = randomString(20)
      val putReply = updateOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the open corporates url should be changed")
      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should equal (url)
    }

    scenario("we will not update the open corporates url for a random other bank account due to a missing token", API1_2, PutOpenCorporatesURL) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val putReply = updateOpenCorporatesUrlForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the open corporates url should not be changed")
      val url = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomURL should not equal (url)
    }

    scenario("we will not update the open corporates url for a random other bank account because the user does not have enough privileges", API1_2, PutOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      When("the request is sent")
      val putReply = updateOpenCorporatesUrlForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the open corporates url for a random other bank account because the account does not exist", API1_2, PutOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomURL = randomString(20)
      When("the request is sent")
      val putReply = updateOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomURL)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the open corporates url for one specific other bank"){
    scenario("we will delete the open corporates url for one random other bank account", API1_2, DeleteOpenCorporatesURL) {
      Given("We will use an access token and will set an open corporates url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      When("the delete request is sent")
      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the open corporates url should be null")
      val urlAfterDelete = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should equal (null)
    }

    scenario("we will not delete the open corporates url for a random other bank account due to a missing token", API1_2, DeleteOpenCorporatesURL) {
      Given("We will not use an access token and will set an open corporates url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      When("the delete request is sent")
      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the open corporates url should not be null")
      val urlAfterDelete = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should not equal (null)
    }

    scenario("we will not delete the open corporates url for a random other bank account because the user does not have enough privileges", API1_2, DeleteOpenCorporatesURL) {
      Given("We will use an access token and will set an open corporates url first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomURL = randomString(20)
      postOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomURL)
      When("the delete request is sent")
      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the open corporates url should not be null")
      val urlAfterDelete = getOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      urlAfterDelete should not equal (null)
    }

    scenario("we will not delete the open corporates url for a random other bank account because the account does not exist", API1_2, DeleteOpenCorporatesURL) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomURL = randomString(20)
      When("the delete request is sent")
      val deleteReply = deleteOpenCorporatesUrlForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We post the corporate location for one specific other bank"){
    scenario("we will post the corporate location for one random other bank account", API1_2, PostCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the corporate location should be changed")
      val location = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomLoc.latitude should equal (location.latitude)
      randomLoc.longitude should equal (location.longitude)
    }

    scenario("we will not post the corporate location for a random other bank account due to a missing token", API1_2, PostCorporateLocation) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postCorporateLocationForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the corporate location for one random other bank account because the coordinates don't exist", API1_2, PostCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
      When("the request is sent")
      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the corporate location for a random other bank account because the user does not have enough privileges", API1_2, PostCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postCorporateLocationForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the corporate location for a random other bank account because the view does not exist", API1_2, PostCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the corporate location for a random other bank account because the account does not exist", API1_2, PostCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the corporate location for one specific other bank"){
    scenario("we will update the corporate location for one random other bank account", API1_2, PutCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomLoc = randomLocation
      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the corporate location should be changed")
      val location = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomLoc.latitude should equal (location.latitude)
      randomLoc.longitude should equal (location.longitude)
    }

    scenario("we will not update the corporate location for one random other bank account because the coordinates don't exist", API1_2, PutCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
      When("the request is sent")
      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the corporate location for a random other bank account due to a missing token", API1_2, PutCorporateLocation) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updateCorporateLocationForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the corporate location for a random other bank account because the user does not have enough privileges", API1_2, PutCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updateCorporateLocationForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the corporate location for a random other bank account because the account does not exist", API1_2, PutCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updateCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the corporate location for one specific other bank"){
    scenario("we will delete the corporate location for one random other bank account", API1_2, DeleteCorporateLocation) {
      Given("We will use an access token and will set a corporate location first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the corporate location should be null")
      val locationAfterDelete = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      locationAfterDelete should equal (null)
    }

    scenario("we will not delete the corporate location for a random other bank account due to a missing token", API1_2, DeleteCorporateLocation) {
      Given("We will not use an access token and will set a corporate location first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteCorporateLocationForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the corporate location should not be null")
      val locationAfterDelete = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      locationAfterDelete should not equal (null)
    }

    scenario("we will not delete the corporate location for a random other bank account because the user does not have enough privileges", API1_2, DeleteCorporateLocation) {
      Given("We will use an access token and will set a corporate location first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteCorporateLocationForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the corporate location should not be null")
      val locationAfterDelete = getCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      locationAfterDelete should not equal (null)
    }

    scenario("we will not delete the corporate location for a random other bank account because the account does not exist", API1_2, DeleteCorporateLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the delete request is sent")
      val deleteReply = deleteCorporateLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We post the physical location for one specific other bank"){
    scenario("we will post the physical location for one random other bank account", API1_2, PostPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the physical location should be changed")
      val location = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomLoc.latitude should equal (location.latitude)
      randomLoc.longitude should equal (location.longitude)
    }

    scenario("we will not post the physical location for one random other bank account because the coordinates don't exist", API1_2, PostPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
      When("the request is sent")
      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the physical location for a random other bank account due to a missing token", API1_2, PostPhysicalLocation) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postPhysicalLocationForAnOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the physical location for a random other bank account because the user does not have enough privileges", API1_2, PostPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postPhysicalLocationForAnOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the physical location for a random other bank account because the view does not exist", API1_2, PostPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, randomString(5), otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the physical location for a random other bank account because the account does not exist", API1_2, PostPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the physical location for one specific other bank"){
    scenario("we will update the physical location for one random other bank account", API1_2, PutPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomLoc = randomLocation
      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the physical location should be changed")
      val location = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      randomLoc.latitude should equal (location.latitude)
      randomLoc.longitude should equal (location.longitude)
    }

    scenario("we will not update the physical location for one random other bank account because the coordinates don't exist", API1_2, PutPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
      When("the request is sent")
      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the physical location for a random other bank account due to a missing token", API1_2, PutPhysicalLocation) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updatePhysicalLocationForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the physical location for a random other bank account because the user does not have enough privileges", API1_2, PutPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updatePhysicalLocationForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the physical location for a random other bank account because the account does not exist", API1_2, PutPhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updatePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5), randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the physical location for one specific other bank"){
    scenario("we will delete the physical location for one random other bank account", API1_2, DeletePhysicalLocation) {
      Given("We will use an access token and will set a physical location first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deletePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the physical location should be null")
      val locationAfterDelete = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      locationAfterDelete should equal (null)
    }

    scenario("we will not delete the physical location for a random other bank account due to a missing token", API1_2, DeletePhysicalLocation) {
      Given("We will not use an access token and will set a physical location first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deletePhysicalLocationForOneOtherBankAccountWithoutToken(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the physical location should not be null")
      val locationAfterDelete = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      locationAfterDelete should not equal (null)
    }

    scenario("we will not delete the physical location for a random other bank account because the user does not have enough privileges", API1_2, DeletePhysicalLocation) {
      Given("We will use an access token and will set a physical location first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val otherBankAccount = randomOtherBankAccount(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deletePhysicalLocationForOneOtherBankAccountWithWrongUser(bankId, bankAccount.id, view, otherBankAccount.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the physical location should not be null")
      val locationAfterDelete = getPhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, otherBankAccount.id)
      locationAfterDelete should not equal (null)
    }

    scenario("we will not delete the physical location for a random other bank account because the account does not exist", API1_2, DeletePhysicalLocation) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the delete request is sent")
      val deleteReply = deletePhysicalLocationForOneOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("Information about all the transaction"){
    scenario("we get all the transactions of one random (private) bank account", API1_2, GetTransactions) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getTransactions(bankId,bankAccount.id,view)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = reply.body.extract[TransactionsJSON]
    }

    scenario("we do not get transactions of one random bank account, because the account doesn't exist", API1_2, GetTransactions) {
      Given("We will use an access token")
      When("the request is sent")
      val bankId = randomBank
      val reply = getTransactions(bankId,randomString(5),randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
    }

    scenario("we do not get transactions of one random bank account, because the view doesn't exist", API1_2, GetTransactions) {
      Given("We will use an access token")
      When("the request is sent")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val reply = getTransactions(bankId,bankAccount.id,randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
    }
  }

  feature("Information about a transaction"){
    scenario("we get transaction data by using an access token", API1_2, GetTransaction) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      reply.body.extract[TransactionJSON]
    }

    scenario("we will not get transaction data due to a missing token", API1_2, GetTransaction) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
    }

    scenario("we will not get transaction data because user does not have enough privileges", API1_2, GetTransaction) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
    }

    scenario("we will not get transaction data because the account does not exist", API1_2, GetTransaction) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTransactionWithoutToken(bankId, randomString(5), view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
    }

    scenario("we will not get transaction data because the view does not exist", API1_2, GetTransaction) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTransactionWithoutToken(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
    }

    scenario("we will not get transaction data because the transaction does not exist", API1_2, GetTransaction) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getTransactionWithoutToken(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
    }

  }

  feature("We get the narrative of one random transaction"){
    scenario("we will get the narrative of one random transaction", API1_2, GetNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      reply.body.extract[TransactionNarrativeJSON]
    }

    scenario("we will not get the narrative of one random transaction due to a missing token", API1_2, GetNarrative) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getNarrativeForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the narrative of one random transaction because the user does not have enough privileges", API1_2, GetNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getNarrativeForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the narrative of one random transaction because the view does not exist", API1_2, GetNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the narrative of one random transaction because the transaction does not exist", API1_2, GetNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post the narrative for one random transaction"){
    scenario("we will post the narrative for one random transaction", API1_2, PostNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomNarrative = randomString(20)
      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the narrative should be added")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should equal (theNarrativeAfterThePost.narrative)
    }

    scenario("we will not post the narrative for one random transaction due to a missing token", API1_2, PostNarrative) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      When("the request is sent")
      val postReply = postNarrativeForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the narrative should not be added")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should not equal (theNarrativeAfterThePost.narrative)
    }

    scenario("we will not post the narrative for one random transaction because the user does not have enough privileges", API1_2, PostNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      When("the request is sent")
      val postReply = postNarrativeForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the narrative should not be added")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should not equal (theNarrativeAfterThePost.narrative)
    }

    scenario("we will not post the narrative for one random transaction because the view does not exist", API1_2, PostNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      When("the request is sent")
      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomNarrative)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the narrative should not be added")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theNarrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should not equal (theNarrativeAfterThePost.narrative)
    }

    scenario("we will not post the narrative for one random transaction because the transaction does not exist", API1_2, PostNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val randomNarrative = randomString(20)
      When("the request is sent")
      val postReply = postNarrativeForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomNarrative)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the narrative for one random transaction"){
    scenario("we will the narrative for one random transaction", API1_2, PutNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomNarrative = randomString(20)
      val putReply = updateNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the narrative should be changed")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val narrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should equal (narrativeAfterThePost.narrative)
    }

    scenario("we will not update the narrative for one random transaction due to a missing token", API1_2, PutNarrative) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      When("the request is sent")
      val putReply = updateNarrativeForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the narrative should not be changed")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val narrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should not equal (narrativeAfterThePost.narrative)
    }

    scenario("we will not update the narrative for one random transaction because the user does not have enough privileges", API1_2, PutNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      When("the request is sent")
      val putReply = updateNarrativeForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the narrative should not be changed")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val narrativeAfterThePost : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      randomNarrative should not equal (narrativeAfterThePost.narrative)
    }

    scenario("we will not update the narrative for one random transaction because the transaction does not exist", API1_2, PutNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transactionId = randomString(5)
      val randomNarrative = randomString(20)
      When("the request is sent")
      val putReply = updateNarrativeForOneTransaction(bankId, bankAccount.id, view, transactionId, randomNarrative)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the narrative for one random transaction"){
    scenario("we will delete the narrative for one random transaction", API1_2, DeleteNarrative) {
      Given("We will use an access token and will set a narrative first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      When("the delete request is sent")
      val deleteReply = deleteNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the narrative should be null")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val narrativeAfterTheDelete : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      narrativeAfterTheDelete.narrative should equal (null)
    }

    scenario("we will not delete narrative for one random transaction due to a missing token", API1_2, DeleteNarrative) {
      Given("We will not use an access token and will set a narrative first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      When("the delete request is sent")
      val deleteReply = deleteNarrativeForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the public narrative should not be null")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val narrativeAfterTheDelete : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      narrativeAfterTheDelete.narrative should not equal (null)
    }

    scenario("we will not delete the narrative for one random transaction because the user does not have enough privileges", API1_2, DeleteNarrative) {
      Given("We will use an access token and will set a narrative first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomNarrative = randomString(20)
      postNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomNarrative)
      When("the delete request is sent")
      val deleteReply = deleteNarrativeForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      And("the narrative should not be null")
      val getReply = getNarrativeForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val narrativeAfterTheDelete : TransactionNarrativeJSON = getReply.body.extract[TransactionNarrativeJSON]
      narrativeAfterTheDelete.narrative should not equal (null)
    }

    scenario("we will not delete the narrative for one random transaction because the transaction does not exist", API1_2, DeleteNarrative) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "owner"
      val randomNarrative = randomString(20)
      When("the delete request is sent")
      val deleteReply = deleteNarrativeForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We get the comments of one random transaction"){
    scenario("we will get the comments of one random transaction", API1_2, GetComments) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      reply.body.extract[TransactionCommentsJSON]
    }

    scenario("we will not get the comments of one random transaction due to a missing token", API1_2, GetComments) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getCommentsForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the comments of one random transaction because the user does not have enough privileges", API1_2, GetComments) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getCommentsForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the comments of one random transaction because the view does not exist", API1_2, GetComments) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the comments of one random transaction because the transaction does not exist", API1_2, GetComments) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getCommentsForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post a comment for one random transaction"){
    scenario("we will post a comment for one random transaction", API1_2, PostComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[TransactionCommentJSON]
      And("the comment should be added")
      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
      val theComment = theCommentsAfterThePost.find(_.value == randomComment.value)
      theComment.nonEmpty should equal (true)
      theComment.get.user should not equal (null)

    }

    scenario("we will not post a comment for one random transaction due to a missing token", API1_2, PostComment) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      When("the request is sent")
      val postReply = postCommentForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomComment)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the comment should not be added")
      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
      val notFound = theCommentsAfterThePost.find(_.value == randomComment.value) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }


    scenario("we will not post a comment for one random transaction because the user does not have enough privileges", API1_2, PostComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
       val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      When("the request is sent")
      val postReply = postCommentForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomComment)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the comment should not be added")
      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
      val notFound = theCommentsAfterThePost.find(_.value == randomComment.value) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post a comment for one random transaction because the view does not exist", API1_2, PostComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
       val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      When("the request is sent")
      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomComment)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the comment should not be added")
      val getReply = getCommentsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theCommentsAfterThePost = getReply.body.extract[TransactionCommentsJSON].comments
      val notFound = theCommentsAfterThePost.find(_.value == randomComment.value) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post a comment for one random transaction because the transaction does not exist", API1_2, PostComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      When("the request is sent")
      val postReply = postCommentForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomComment)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete a comment for one random transaction"){
    scenario("we will delete a comment for one random transaction", API1_2, DeleteComment) {
      Given("We will use an access token and will set a comment first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      val postedComment = postedReply.body.extract[TransactionCommentJSON]
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedComment.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
    }

    scenario("we will not delete a comment for one random transaction due to a missing token", API1_2, DeleteComment) {
      Given("We will not use an access token and will set a comment first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      val postedComment = postedReply.body.extract[TransactionCommentJSON]
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, postedComment.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a comment for one random transaction because the user does not have enough privileges", API1_2, DeleteComment) {
      Given("We will use an access token and will set a comment first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      val postedComment = postedReply.body.extract[TransactionCommentJSON]
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, postedComment.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a comment for one random transaction because the user did not post the comment", API1_2, DeleteComment) {
      Given("We will use an access token and will set a comment first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "public"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      val postedComment = postedReply.body.extract[TransactionCommentJSON]
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, postedComment.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a comment for one random transaction because the comment does not exist", API1_2, DeleteComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a comment for one random transaction because the transaction does not exist", API1_2, DeleteComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      val postedComment = postedReply.body.extract[TransactionCommentJSON]
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, view, randomString(5), postedComment.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a comment for one random transaction because the view does not exist", API1_2, DeleteComment) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomComment = PostTransactionCommentJSON(randomString(20))
      val postedReply = postCommentForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomComment)
      val postedComment = postedReply.body.extract[TransactionCommentJSON]
      When("the delete request is sent")
      val deleteReply = deleteCommentForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, postedComment.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We get the tags of one random transaction"){
    scenario("we will get the tags of one random transaction", API1_2, GetTags) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      reply.body.extract[TransactionTagsJSON]
    }

    scenario("we will not get the tags of one random transaction due to a missing token", API1_2, GetTags) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTagsForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the tags of one random transaction because the user does not have enough privileges", API1_2, GetTags) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTagsForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the tags of one random transaction because the view does not exist", API1_2, GetTags) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTagsForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the tags of one random transaction because the transaction does not exist", API1_2, GetTags) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getTagsForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post a tag for one random transaction"){
    scenario("we will post a tag for one random transaction", API1_2, PostTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[TransactionTagJSON]
      And("the tag should be added")
      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
      val theTag = theTagsAfterThePost.find(_.value == randomTag.value)
      theTag.nonEmpty should equal (true)
      theTag.get.user should not equal (null)
    }

    scenario("we will not post a tag for one random transaction due to a missing token", API1_2, PostTag) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      When("the request is sent")
      val postReply = postTagForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomTag)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the tag should not be added")
      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
      val notFound = theTagsAfterThePost.find(_.value == randomTag.value) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post a tag for one random transaction because the user does not have enough privileges", API1_2, PostTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
       val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      When("the request is sent")
      val postReply = postTagForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomTag)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the tag should not be added")
      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
      val notFound = theTagsAfterThePost.find(_.value == randomTag.value) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post a tag for one random transaction because the view does not exist", API1_2, PostTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
       val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      When("the request is sent")
      val postReply = postTagForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomTag)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the tag should not be added")
      val getReply = getTagsForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theTagsAfterThePost = getReply.body.extract[TransactionTagsJSON].tags
      val notFound = theTagsAfterThePost.find(_.value == randomTag.value) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post a tag for one random transaction because the transaction does not exist", API1_2, PostTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomTag = PostTransactionTagJSON(randomString(5))
      When("the request is sent")
      val postReply = postTagForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomTag)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete a tag for one random transaction"){
    scenario("we will delete a tag for one random transaction", API1_2, DeleteTag) {
      Given("We will use an access token and will set a tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      val postedTag = postedReply.body.extract[TransactionTagJSON]
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedTag.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
    }

    scenario("we will not delete a tag for one random transaction due to a missing token", API1_2, DeleteTag) {
      Given("We will not use an access token and will set a tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      val postedTag = postedReply.body.extract[TransactionTagJSON]
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, postedTag.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a tag for one random transaction because the user does not have enough privileges", API1_2, DeleteTag) {
      Given("We will use an access token and will set a tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      val postedTag = postedReply.body.extract[TransactionTagJSON]
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, postedTag.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a tag for one random transaction because the user did not post the tag", API1_2, DeleteTag) {
      Given("We will use an access token and will set a tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "public"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      val postedTag = postedReply.body.extract[TransactionTagJSON]
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, postedTag.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a tag for one random transaction because the tag does not exist", API1_2, DeleteTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a tag for one random transaction because the transaction does not exist", API1_2, DeleteTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      val postedTag = postedReply.body.extract[TransactionTagJSON]
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, view, randomString(5), postedTag.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete a tag for one random transaction because the view does not exist", API1_2, DeleteTag) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomTag = PostTransactionTagJSON(randomString(5))
      val postedReply = postTagForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomTag)
      val postedTag = postedReply.body.extract[TransactionTagJSON]
      When("the delete request is sent")
      val deleteReply = deleteTagForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id,  postedTag.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We get the images of one random transaction"){
    scenario("we will get the images of one random transaction", API1_2, GetImages) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      reply.body.extract[TransactionImagesJSON]
    }

    scenario("we will not get the images of one random transaction due to a missing token", API1_2, GetImages) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getImagesForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the images of one random transaction because the user does not have enough privileges", API1_2, GetImages) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getImagesForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the images of one random transaction because the view does not exist", API1_2, GetImages) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getImagesForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the images of one random transaction because the transaction does not exist", API1_2, GetImages) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getImagesForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post an image for one random transaction"){
    scenario("we will post an image for one random transaction", API1_2, PostImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[TransactionImageJSON]
      And("the image should be added")
      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
      val theImage = theImagesAfterThePost.find(_.URL == randomImage.URL)
      theImage.nonEmpty should equal (true)
      theImage.get.user should not equal (null)
    }

    scenario("we will not post an image for one random transaction due to a missing token", API1_2, PostImage) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      When("the request is sent")
      val postReply = postImageForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomImage)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image should not be added")
      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
      val notFound = theImagesAfterThePost.find(_.URL == randomImage.URL) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post an image for one random transaction because the user does not have enough privileges", API1_2, PostImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
       val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      When("the request is sent")
      val postReply = postImageForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomImage)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image should not be added")
      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
      val notFound = theImagesAfterThePost.find(_.URL == randomImage.URL) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post an image for one random transaction because the view does not exist", API1_2, PostImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
       val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      When("the request is sent")
      val postReply = postImageForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomImage)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
      And("the image should not be added")
      val getReply = getImagesForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      val theImagesAfterThePost = getReply.body.extract[TransactionImagesJSON].images
      val notFound = theImagesAfterThePost.find(_.URL == randomImage.URL) match {
        case None => true
        case Some(_) => false
      }
      notFound should equal (true)
    }

    scenario("we will not post an image for one random transaction because the transaction does not exist", API1_2, PostImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      When("the request is sent")
      val postReply = postImageForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomImage)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete an image for one random transaction"){
    scenario("we will delete an image for one random transaction", API1_2, DeleteImage) {
      Given("We will use an access token and will set an image first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      val postedImage = postedReply.body.extract[TransactionImageJSON]
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, postedImage.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
    }

    scenario("we will not delete an image for one random transaction due to a missing token", API1_2, DeleteImage) {
      Given("We will not use an access token and will set an image first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      val postedImage = postedReply.body.extract[TransactionImageJSON]
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, postedImage.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete an image for one random transaction because the user does not have enough privileges", API1_2, DeleteImage) {
      Given("We will use an access token and will set an image first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      val postedImage = postedReply.body.extract[TransactionImageJSON]
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, postedImage.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete an image for one random transaction because the user did not post the image", API1_2, DeleteImage) {
      Given("We will use an access token and will set an image first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "public"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      val postedImage = postedReply.body.extract[TransactionImageJSON]
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, postedImage.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete an image for one random transaction because the image does not exist", API1_2, DeleteImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete an image for one random transaction because the transaction does not exist", API1_2, DeleteImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      val postedImage = postedReply.body.extract[TransactionImageJSON]
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, view, randomString(5), postedImage.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete an image for one random transaction because the view does not exist", API1_2, DeleteImage) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomImage = PostTransactionImageJSON(randomString(5),"http://www.mysuperimage.com")
      val postedReply = postImageForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomImage)
      val postedImage = postedReply.body.extract[TransactionImageJSON]
      When("the delete request is sent")
      val deleteReply = deleteImageForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, postedImage.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We get the where of one random transaction"){
    scenario("we will get the where of one random transaction", API1_2, GetWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the request is sent")
      val reply = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
    }

    scenario("we will not get the where of one random transaction due to a missing token", API1_2, GetWhere) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the request is sent")
      val reply = getWhereForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the where of one random transaction because the user does not have enough privileges", API1_2, GetWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the request is sent")
      val reply = getWhereForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the where of one random transaction because the view does not exist", API1_2, GetWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the request is sent")
      val reply = getWhereForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get the where of one random transaction because the transaction does not exist", API1_2, GetWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We post the where for one random transaction"){
    scenario("we will post the where for one random transaction", API1_2, PostWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 201 code")
      postReply.code should equal (201)
      postReply.body.extract[SuccessMessage]
      And("the where should be posted")
      val location = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id).body.extract[TransactionWhereJSON]
      randomLoc.latitude should equal (location.where.latitude)
      randomLoc.longitude should equal (location.where.longitude)
      location.where.user should not equal (null)
    }

    scenario("we will not post the where for one random transaction because the coordinates don't exist", API1_2, PostWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
      When("the request is sent")
      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the where for a random transaction due to a missing token", API1_2, PostWhere) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postWhereForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the where for a random transaction because the user does not have enough privileges", API1_2, PostWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postWhereForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the where for a random transaction because the view does not exist", API1_2, PostWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply =  postWhereForOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id, randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not post the where for a random transaction because the transaction does not exist", API1_2, PostWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the request is sent")
      val postReply = postWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomLoc)
      Then("we should get a 400 code")
      postReply.code should equal (400)
      And("we should get an error message")
      postReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We update the where for one random transaction"){
    scenario("we will update the where for one random transaction", API1_2, PutWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val randomLoc = randomLocation
      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 200 code")
      putReply.code should equal (200)
      putReply.body.extract[SuccessMessage]
      And("the where should be changed")
      val location = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id).body.extract[TransactionWhereJSON]
      randomLoc.latitude should equal (location.where.latitude)
      randomLoc.longitude should equal (location.where.longitude)
    }

    scenario("we will not update the where for one random transaction because the coordinates don't exist", API1_2, PutWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      var randomLoc = JSONFactory.createLocationPlainJSON(400,200)
      When("the request is sent")
      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the where for a random transaction due to a missing token", API1_2, PutWhere) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      var randomLoc = randomLocation
      When("the request is sent")
      val putReply = updateWhereForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the where for a random transaction because the user does not have enough privileges", API1_2, PutWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updateWhereForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id, randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update the where for a random transaction because the transaction does not exist", API1_2, PutWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the request is sent")
      val putReply = updateWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5), randomLoc)
      Then("we should get a 400 code")
      putReply.code should equal (400)
      And("we should get an error message")
      putReply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("We delete the where for one random transaction"){
    scenario("we will delete the where for one random transaction", API1_2, DeleteWhere) {
      Given("We will use an access token and will set a where tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 204 code")
      deleteReply.code should equal (204)
      And("the where should be null")
      // TODO: (3 scenarios)
      // val locationAfterDelete = getWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id).body.extract[TransactionWhereJSON]
      // locationAfterDelete.where should equal (null)
    }

    scenario("we will not delete the where for a random transaction due to a missing token", API1_2, DeleteWhere) {
      Given("We will not use an access token and will set a where tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteWhereForOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      // And("the where should not be null")
    }

    scenario("we will not delete the where for a random transaction because the user does not have enough privileges", API1_2, DeleteWhere) {
      Given("We will use an access token and will set a where tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteWhereForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
      // And("the where should not be null")
    }

    scenario("we will not delete the where for one random transaction because the user did not post the geo tag", API1_2, DeleteWhere) {
      Given("We will use an access token and will set a where tag first")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = "public"
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val randomLoc = randomLocation
      postWhereForOneTransaction(bankId, bankAccount.id, view, transaction.id, randomLoc)
      When("the delete request is sent")
      val deleteReply = deleteWhereForOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }

    scenario("we will not delete the where for a random transaction because the transaction does not exist", API1_2, DeleteWhere) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val randomLoc = randomLocation
      When("the delete request is sent")
      val deleteReply = deleteWhereForOneTransaction(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      deleteReply.code should equal (400)
    }
  }

  feature("We get the other bank account of a transaction "){
    scenario("we will get the other bank account of a random transaction", API1_2, GetTransactionAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccountOfOneTransaction(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 200 code")
      reply.code should equal (200)
      val accountJson = reply.body.extract[OtherAccountJSON]
      And("some fields should not be empty")
      accountJson.id.nonEmpty should equal (true)
    }

    scenario("we will not get the other bank account of a random transaction due to a missing token", API1_2, GetTransactionAccount) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccountOfOneTransactionWithoutToken(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get get the other bank account of a random transaction because the user does not have enough privileges", API1_2, GetTransactionAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccountOfOneTransactionWithWrongUser(bankId, bankAccount.id, view, transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get get the other bank account of a random transaction because the view does not exist", API1_2, GetTransactionAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      When("the request is sent")
      val reply = getTheOtherBankAccountOfOneTransaction(bankId, bankAccount.id, randomString(5), transaction.id)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not get get the other bank account of a random transaction because the transaction does not exist", API1_2, GetTransactionAccount) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : AccountJSON = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent")
      val reply = getTheOtherBankAccount(bankId, bankAccount.id, view, randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }
}