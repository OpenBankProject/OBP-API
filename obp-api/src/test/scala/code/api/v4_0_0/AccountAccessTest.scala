/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v4_0_0

import org.json4s._
import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJsonV300
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, CannotRevokeOwnAccountAccess, UserLacksPermissionCanGrantAccessToViewForTargetAccount, ViewNotFound}
import code.views.Views
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, View}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import code.api.v3_0_0.ViewJsonV300
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import net.liftweb.common.Box
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.concurrent.TimeUnit

class AccountAccessTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.grantUserAccessToView))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.revokeUserAccessToView))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.createUserWithAccountAccess))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.revokeGrantUserAccessToViews))

  
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val ownerView = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)
  lazy val postAccountAccessJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400("_test_view", false))
  lazy val postBodyViewJson = createViewJsonV300.toCreateViewJson
  
  def createAnAccount(bankId: String, user: Option[(Consumer,Token)]): CreateAccountResponseJsonV310 = {
    val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" ).POST <@(user1)
    val response400 = makePostRequest(request400, write(addAccountJson))
    Then("We should get a 201")
    
    response400.code should equal(201)
    response400.body.extract[CreateAccountResponseJsonV310]
  }
  
  def createViewForAnAccount(bankId: String, accountId: String): ViewJsonV300 = {
    createViewViaEndpoint(bankId, accountId, postBodyViewJson, user1)
  }

  // gives resourceUser2 only a new system view, which may revoke itself and grant just the views in canGrant
  def grantUser2ALimitedSystemView(bankId: String, accountId: String, canGrant: List[String]): View = {
    val name = "limited" + APIUtil.generateUUID().replace("-", "").toLowerCase
    val view = Await.result(Views.views.vend.createSystemView(postBodyViewJson.copy(
      name = name, is_public = false,
      allowed_actions = (postBodyViewJson.allowed_actions ++ List(code.api.Constant.CAN_GRANT_ACCESS_TO_VIEWS, code.api.Constant.CAN_REVOKE_ACCESS_TO_VIEWS)).distinct,
      can_grant_access_to_views = Some(canGrant), can_revoke_access_to_views = Some(List(name)))), Duration(30, TimeUnit.SECONDS))
      .openOrThrowException("the limited system view should be created")
    Views.views.vend.grantAccessToSystemView(BankId(bankId), AccountId(accountId), view, resourceUser2)
    view
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / "account-access" / "grant").POST
      val response400 = makePostRequest(request400, write(postAccountAccessJson))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / "account-access" / "revoke").POST
      val response400 = makePostRequest(request400, write(postAccountAccessJson))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 and $ApiEndpoint2 and $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("A user cannot revoke their own access", VersionOfApi, ApiEndpoint2) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      When("The caller asks to revoke their own access to the owner view")
      val postJson = PostAccountAccessJsonV400(resourceUser1.userId, PostViewJsonV400(code.api.Constant.SYSTEM_OWNER_VIEW_ID, true))
      val requestRevoke = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access" / "revoke").POST <@ (user1)
      val responseRevoke = makePostRequest(requestRevoke, write(postJson))
      Then("We should get a 403")
      responseRevoke.code should equal(403)
      responseRevoke.body.extract[ErrorMessage].message should include(CannotRevokeOwnAccountAccess)
    }

    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {

      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      val view = createViewForAnAccount(bankId, account.account_id)
      val postJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400(view.id, view.is_system))
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access" / "grant").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 201 and check the response body")
      response.code should equal(201)
      response.body.extract[ViewJsonV300]
      
      When("We send the request")
      val requestRevoke = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access" / "revoke").POST <@ (user1)
      val responseRevoke = makePostRequest(requestRevoke, write(postJson))
      Then("We should get a 201 and check the response body")
      responseRevoke.code should equal(201)
      responseRevoke.body.extract[RevokedJsonV400]
      
      {
        val postCreateUserJson = PostCreateUserAccountAccessJsonV400(resourceUser2.userId, "dauth."+resourceUser2.provider, List(PostViewJsonV400(view.id, view.is_system)))
        When("We send the request")
        val request = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "user-account-access").POST <@ (user1)
        val response = makePostRequest(request, write(postCreateUserJson))
        Then("We should get a 201 and check the response body")
        response.code should equal(201)
        val views = response.body.extract[List[ViewJsonV300]]
        views.length
      }
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - views the caller may not grant, or that do not exist") {
    scenario("A user with a limited view cannot swap it for the owner view", VersionOfApi, ApiEndpoint4) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
      val limitedView = grantUser2ALimitedSystemView(bankId, account.account_id, Nil)

      When("The holder of the limited view asks for the owner view instead")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access").PUT <@ (user2)
      val response = makePutRequest(request, write(PostRevokeGrantAccountAccessJsonV400(List(code.api.Constant.SYSTEM_OWNER_VIEW_ID))))
      Then("We should get a 403, and the user keeps only the limited view")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should include(UserLacksPermissionCanGrantAccessToViewForTargetAccount)
      Views.views.vend.permission(BankIdAccountId(BankId(bankId), AccountId(account.account_id)), resourceUser2)
        .map(_.views.map(_.viewId.value)).openOr(Nil) should equal(List(limitedView.viewId.value))
    }

    scenario("An unknown view id is refused before the caller's access is revoked", VersionOfApi, ApiEndpoint4) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
      val missingViewId = "missing" + APIUtil.generateUUID().replace("-", "").toLowerCase
      val limitedView = grantUser2ALimitedSystemView(bankId, account.account_id, List(missingViewId))

      When("The user asks for a view that does not exist")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / account.account_id / "account-access").PUT <@ (user2)
      val response = makePutRequest(request, write(PostRevokeGrantAccountAccessJsonV400(List(missingViewId))))
      Then("We should get a 404, and the user keeps the limited view")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should include(ViewNotFound)
      Views.views.vend.permission(BankIdAccountId(BankId(bankId), AccountId(account.account_id)), resourceUser2)
        .map(_.views.map(_.viewId.value)).openOr(Nil) should equal(List(limitedView.viewId.value))
    }
  }

  
  
}
