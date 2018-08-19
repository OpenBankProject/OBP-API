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
package code.api.v2_2_0


import _root_.net.liftweb.json.Serialization.write
import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJson
import code.api.util.APIUtil.OAuth._
import code.api.v1_2._
import code.api.v1_2_1.UpdateViewJsonV121
import code.model.CreateViewJson
import code.setup.{APIResponse, DefaultUsers, User1AllPrivileges}
import net.liftweb.util.Helpers._
import org.scalatest._

import scala.util.Random._


class API2_2_0Test extends User1AllPrivileges with V220ServerSetup with DefaultUsers {

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

  //System view, owner
  val postBodySystemViewJson = createViewJson.copy(name="owner")
  
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

  def randomView(isPublic: Boolean, alias: String) = createViewJson


  def getBanksInfo : APIResponse  = {
    val request = v2_2Request / "banks"
    makeGetRequest(request)
  }

  def getBankInfo(bankId : String) : APIResponse  = {
    val request = v2_2Request / "banks" / bankId
    makeGetRequest(request)
  }

  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2_1Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def getAccountViews(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v2_2Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v2_2Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
    makePostRequest(request, write(view))
  }

  def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJsonV121, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
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
      apiInfo.version should equal ("v2.2.0")
/*      apiInfo.git_commit.nonEmpty should equal (true)*/
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
      val bankAccountId = randomPrivateAccountId(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccountId, user1)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      reply.body.extract[ViewsJSONV220]
    }

    scenario("We will not get the list of the available views on a bank account due to missing token", API2_2, GetViews) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccountId = randomPrivateAccountId(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccountId, None)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not get the list of the available views on a bank account due to insufficient privileges", API2_2, GetViews) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccountId = randomPrivateAccountId(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccountId, user3)
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
      val bankAccountId = randomPrivateAccountId(bankId)
      val viewsBefore = getAccountViews(bankId, bankAccountId, user1).body.extract[ViewsJSONV220].views
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccountId, view, user1)
      Then("we should get a 201 code")
      reply.code should equal (201)
      reply.body.extract[ViewJSONV220]
      And("we should get a new view")
      val viewsAfter = getAccountViews(bankId, bankAccountId, user1).body.extract[ViewsJSONV220].views
      viewsBefore.size should equal (viewsAfter.size -1)
    }

    scenario("We will not create a view on a bank account due to missing token", API2_2, PostView) {
      Given("We will not use an access token")
      val bankId = randomBank
      val bankAccountId = randomPrivateAccountId(bankId)
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccountId, view, None)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view on a bank account due to insufficient privileges", API2_2, PostView) {
      Given("We will use an access token")
      val bankId = randomBank
      val bankAccountId = randomPrivateAccountId(bankId)
      val view = randomView(true, "")
      When("the request is sent")
      val reply = postView(bankId, bankAccountId, view, user3)
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
      val bankAccountId = randomPrivateAccountId(bankId)
      val view = randomView(true, "")
      postView(bankId, bankAccountId, view, user1)
      When("the request is sent")
      val reply = postView(bankId, bankAccountId, view, user1)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  
    scenario("can not create the System View") {
      Given("The BANK_ID, ACCOUNT_ID, Login user, views")
      val bankId = randomBankId
      val bankAccountId = randomPrivateAccountId(bankId)
      When("the request is sent")
      val reply = postView(bankId, bankAccountId, postBodySystemViewJson, user1)
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
      UpdateViewJsonV121(
        description = updatedViewDescription,
        is_public = !originalView.is_public,
        which_alias_to_use = updatedAliasToUse,
        hide_metadata_if_alias_used = !originalView.hide_metadata_if_alias_used,
        allowed_actions = allowedActions
      )
    }

    def someViewUpdateJson() = {
      UpdateViewJsonV121(
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
      val bankAccountId = randomPrivateAccountId(bankId)
      val view = randomView(true, "")
      val creationReply = postView(bankId, bankAccountId, view, user1)
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
      val reply = putView(bankId, bankAccountId, createdView.id, viewUpdateJson(createdView), user1)
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
      val bankAccountId = randomPrivateAccountId(bankId)

      Given("a view does not exist")
      val nonExistantViewId = "asdfasdfasdfasdfasdf"
      val getReply = getAccountViews(bankId, bankAccountId, user1)
      getReply.code should equal (200)
      val views : ViewsJSONV220 = getReply.body.extract[ViewsJSONV220]
      views.views.foreach(v => v.id should not equal(nonExistantViewId))

      When("we try to update that view")
      val reply = putView(bankId, bankAccountId, nonExistantViewId, someViewUpdateJson(), user1)
      Then("We should get a 400")
      reply.code should equal(400)
    }

    scenario("We will not update a view on a bank account due to missing token", API2_2, PutView) {
      Given("A view exists")
      val bankId = randomBank
      val bankAccountId = randomPrivateAccountId(bankId)
      val view = randomView(true, "")
      val creationReply = postView(bankId, bankAccountId, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJSONV220 = creationReply.body.extract[ViewJSONV220]

      When("we don't use an access token")
      val reply = putView(bankId, bankAccountId, createdView.id, viewUpdateJson(createdView), None)
      Then("we should get a 400")
      reply.code should equal(400)

      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update a view on a bank account due to insufficient privileges", API2_2, PutView) {
      Given("A view exists")
      val bankId = randomBank
      val bankAccountId = randomPrivateAccountId(bankId)
      val view = randomView(true, "")
      val creationReply = postView(bankId, bankAccountId, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJSONV220 = creationReply.body.extract[ViewJSONV220]

      When("we try to update a view without having sufficient privileges to do so")
      val reply = putView(bankId, bankAccountId, createdView.id, viewUpdateJson(createdView), user3)
      Then("we should get a 400")
      reply.code should equal(400)

      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  
    scenario("we can not update a System view on a bank account") {
      val bankId = randomBankId
      val bankAccountId = randomPrivateAccountId(bankId)
    
      val updateViewJSON = UpdateViewJsonV121(
        description = "good",
        is_public =false,
        which_alias_to_use ="",
        hide_metadata_if_alias_used= false,
        allowed_actions= Nil
      )
    
      When("We use a valid access token and valid put json")
      val reply = putView(bankId, bankAccountId, "owner", updateViewJSON, user1)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }
  
  feature("Get Message Docs - v2.2.0"){
    scenario("Get Message Docs - kafka_vJune2017") {
      val request = (v2_2Request / "message-docs" / "kafka_vJune2017" )
      val response: APIResponse = makeGetRequest(request)
  
      response.code should be (200)
    }
  }
  
}
