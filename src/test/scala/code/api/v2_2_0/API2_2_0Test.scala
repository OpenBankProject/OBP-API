/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
package code.api.v2_2_0


import _root_.net.liftweb.json.JsonAST.JObject
import _root_.net.liftweb.json.Serialization.write
import code.api.util.APIUtil.OAuth._
import code.api.v1_2._
import code.api.{APIResponse, DefaultUsers, User1AllPrivileges}
import code.model.{Consumer => OBPConsumer, Token => OBPToken, _}
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Helpers._
import org.scalatest._

import scala.util.Random._


class API2_2_0Test extends User1AllPrivileges with V220ServerSetup with DefaultUsers {

  implicit val dateFormats = net.liftweb.json.DefaultFormats

  val viewFileds = List(
    "can_see_transaction_this_bank_account","can_see_transaction_other_bank_account",
    "can_see_transaction_metadata","can_see_transaction_label","can_see_transaction_amount",
    "can_see_transaction_type","can_see_transaction_currency","can_see_transaction_start_date",
    "can_see_transaction_finish_date","can_see_transaction_balance","can_see_comments",
    "can_see_narrative","can_see_tags","can_see_images","can_see_bank_account_owners",
    "can_see_bank_account_type","can_see_bank_account_balance","can_see_bank_account_currency",
    "can_see_bank_account_label","can_see_bank_account_national_identifier",
    "can_see_bank_account_swift_bic","can_see_bank_account_iban","can_see_bank_account_number",
    "can_see_bank_account_bank_name","can_see_other_account_national_identifier",
    "can_see_other_account_swift_bic","can_see_other_account_iban",
    "can_see_other_account_bank_name","can_see_other_account_number",
    "can_see_other_account_metadata","can_see_other_account_kind","can_see_more_info",
    "can_see_url","can_see_image_url","can_see_open_corporates_url","can_see_corporate_location",
    "can_see_physical_location","can_see_public_alias","can_see_private_alias","can_add_more_info",
    "can_add_url","can_add_image_url","can_add_open_corporates_url","can_add_corporate_location",
    "can_add_physical_location","can_add_public_alias","can_add_private_alias",
    "can_delete_corporate_location","can_delete_physical_location","can_edit_narrative",
    "can_add_comment","can_delete_comment","can_add_tag","can_delete_tag","can_add_image",
    "can_delete_image","can_add_where_tag","can_see_where_tag","can_delete_where_tag", "can_create_counterparty"
    )

  /************************* test tags ************************/

  /**
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */

  object API2_2 extends Tag("api2.2.0")
  object APIInfo extends Tag("apiInfo")
  object GetHostedBanks extends Tag("hostedBanks")
  object GetHostedBank extends Tag("getHostedBank")
  object GetViews extends Tag("getViews")
  object PostView extends Tag("postView")
  object PutView extends Tag("putView")



  /********************* API test methods ********************/
  val emptyJSON : JObject =
    ("error" -> "empty List")
  val errorAPIResponse = new APIResponse(400,emptyJSON)




  def randomBank : String = {
    val banksJson = getBanksInfo.body.extract[BanksJSON]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }

  def randomPrivateAccount(bankId : String) : code.api.v1_2.AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[code.api.v1_2.AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomViewsIdsToGrant(bankId : String, accountId : String) : List[String]= {
    //get the view ids of the available views on the bank accounts
    val viewsIds = getAccountViews(bankId, accountId, user1).body.extract[ViewsJSONV220].views.map(_.id)
    //choose randomly some view ids to grant
    val (viewsIdsToGrant, _) = viewsIds.splitAt(nextInt(viewsIds.size) + 1)
    viewsIdsToGrant
  }

  def randomView(isPublic: Boolean, alias: String) : CreateViewJSON = {
    CreateViewJSON(
      name = randomString(3),
      description = randomString(3),
      is_public = isPublic,
      which_alias_to_use=alias,
      hide_metadata_if_alias_used=false,
      allowed_actions = viewFileds
    )
  }


  def getAPIInfo : APIResponse = {
    val request = v2_2Request
    makeGetRequest(request)
  }

  def getBanksInfo : APIResponse  = {
    val request = v2_2Request / "banks"
    makeGetRequest(request)
  }

  def getBankInfo(bankId : String) : APIResponse  = {
    val request = v2_2Request / "banks" / bankId
    makeGetRequest(request)
  }

  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def getAccountViews(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v2_2Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def postView(bankId: String, accountId: String, view: CreateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v2_2Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
    makePostRequest(request, write(view))
  }

  def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v2_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).PUT <@(consumerAndToken)
    makePutRequest(request, write(view))
  }



/************************ the tests ************************/
  feature("base line URL works"){
    scenario("we get the api information", API2_2, APIInfo) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getAPIInfo
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val apiInfo = reply.body.extract[APIInfoJSON]
      apiInfo.version should equal ("2.2.0")
/*      apiInfo.git_commit.nonEmpty should equal (true)*/
    }
  }

  feature("Information about the hosted banks"){
    scenario("we get the hosted banks information", API2_2, GetHostedBanks) {
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
    scenario("we get the hosted bank information", API2_2, GetHostedBank) {
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getBankInfo(randomBank)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val bankInfo = reply.body.extract[code.api.v1_2_1.BankJSON]
      bankInfo.id.nonEmpty should equal (true)
    }

    scenario("we don't get the hosted bank information", API2_2, GetHostedBank) {
      Given("We will not use an access token and request a random bankId")
      When("the request is sent")
      val reply = getBankInfo(randomString(5))
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  def assertViewExistsWithCondition(accJson: AccountsJSONV220, cond: ViewJSONV220 => Boolean): Unit = {
    val exists = accJson.accounts.exists(acc => acc.views_available.exists(cond))
    exists should equal(true)
  }

  def assertAllAccountsHaveAViewWithCondition(accJson: AccountsJSONV220, cond: ViewJSONV220 => Boolean): Unit = {
    val forAll = accJson.accounts.forall(acc => acc.views_available.exists(cond))
    forAll should equal(true)
  }

  def assertAccountsFromOneBank(accJson : AccountsJSONV220) : Unit = {
    accJson.accounts.size should be > 0
    val theBankId = accJson.accounts.head.bank_id
    theBankId should not be ("")

    accJson.accounts.foreach(acc => acc.bank_id should equal (theBankId))
  }

  def assertNoDuplicateAccounts(accJson : AccountsJSONV220) : Unit = {
    //bankId : String, accountId: String
    type AccountIdentifier = (String, String)
    //unique accounts have unique bankId + accountId
    val accountIdentifiers : Set[AccountIdentifier] = {
      accJson.accounts.map(acc => (acc.bank_id, acc.id)).toSet
    }
    //if they are all unique, the set will contain the same number of elements as the list
    accJson.accounts.size should equal(accountIdentifiers.size)
  }


  feature("List of the views of specific bank account - v2.2.0"){
    scenario("We will get the list of the available views on a bank account", API2_2, GetViews) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id, user1)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      reply.body.extract[ViewsJSONV220]
    }

    scenario("We will not get the list of the available views on a bank account due to missing token", API2_2, GetViews) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id, None)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not get the list of the available views on a bank account due to insufficient privileges", API2_2, GetViews) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id, user3)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }
  feature("Create a view on a bank account - v2.2.0"){
    scenario("we will create a view on a bank account", API2_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val viewsBefore = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJSONV220].views
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, user1)
      Then("we should get a 201 code")
      reply.code should equal (201)
      reply.body.extract[ViewJSONV220]
      And("we should get a new view")
      val viewsAfter = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJSONV220].views
      viewsBefore.size should equal (viewsAfter.size -1)
    }

    scenario("We will not create a view on a bank account due to missing token", API2_2, PostView) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, None)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view on a bank account due to insufficient privileges", API2_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, user3)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view because the bank account does not exist", API2_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, randomString(3), view, user1)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view because the view already exists", API2_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      postView(bankId, bankAccount.id, view, user1)
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, user1)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Update a view on a bank account - v2.2.0") {

    val updatedViewDescription = "aloha"
    val updatedAliasToUse = "public"
    val allowedActions = List("can_see_images", "can_delete_comment")

    def viewUpdateJson(originalView : ViewJSONV220) = {
      //it's not perfect, assumes too much about originalView (i.e. randomView(true, ""))
      new UpdateViewJSON(
        description = updatedViewDescription,
        is_public = !originalView.is_public,
        which_alias_to_use = updatedAliasToUse,
        hide_metadata_if_alias_used = !originalView.hide_metadata_if_alias_used,
        allowed_actions = allowedActions
      )
    }

    def someViewUpdateJson() = {
      new UpdateViewJSON(
        description = updatedViewDescription,
        is_public = true,
        which_alias_to_use = updatedAliasToUse,
        hide_metadata_if_alias_used = true,
        allowed_actions = allowedActions
      )
    }

    scenario("we will update a view on a bank account", API2_2, PutView) {
      Given("A view exists")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      val creationReply = postView(bankId, bankAccount.id, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJSONV220 = creationReply.body.extract[ViewJSONV220]
      createdView.can_see_images should equal(true)
      createdView.can_delete_comment should equal(true)
      createdView.can_delete_physical_location should equal(true)
      createdView.can_edit_owner_comment should equal(true)
      createdView.description should not equal(updatedViewDescription)
      createdView.is_public should equal(true)
      createdView.hide_metadata_if_alias_used should equal(false)

      When("We use a valid access token and valid put json")
      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), user1)
      Then("We should get back the updated view")
      reply.code should equal (200)
      val updatedView = reply.body.extract[ViewJSONV220]
      updatedView.can_see_images should equal(true)
      updatedView.can_delete_comment should equal(true)
      updatedView.can_delete_physical_location should equal(false)
      updatedView.can_edit_owner_comment should equal(false)
      updatedView.description should equal(updatedViewDescription)
      updatedView.is_public should equal(false)
      updatedView.hide_metadata_if_alias_used should equal(true)
    }

    scenario("we will not update a view that doesn't exist", API2_2, PutView) {
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)

      Given("a view does not exist")
      val nonExistantViewId = "asdfasdfasdfasdfasdf"
      val getReply = getAccountViews(bankId, bankAccount.id, user1)
      getReply.code should equal (200)
      val views : ViewsJSONV220 = getReply.body.extract[ViewsJSONV220]
      views.views.foreach(v => v.id should not equal(nonExistantViewId))

      When("we try to update that view")
      val reply = putView(bankId, bankAccount.id, nonExistantViewId, someViewUpdateJson(), user1)
      Then("We should get a 404")
      reply.code should equal(404)
    }

    scenario("We will not update a view on a bank account due to missing token", API2_2, PutView) {
      Given("A view exists")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      val creationReply = postView(bankId, bankAccount.id, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJSONV220 = creationReply.body.extract[ViewJSONV220]

      When("we don't use an access token")
      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), None)
      Then("we should get a 400")
      reply.code should equal(400)

      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update a view on a bank account due to insufficient privileges", API2_2, PutView) {
      Given("A view exists")
      val bankId = randomBank
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val view = randomView(true, "")
      val creationReply = postView(bankId, bankAccount.id, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJSONV220 = creationReply.body.extract[ViewJSONV220]

      When("we try to update a view without having sufficient privileges to do so")
      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), user3)
      Then("we should get a 400")
      reply.code should equal(400)

      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }


}
