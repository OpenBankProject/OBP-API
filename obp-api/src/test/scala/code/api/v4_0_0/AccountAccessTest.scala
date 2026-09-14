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
import code.api.util.ApiRole
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.AuthenticatedUserIsRequired
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

  
  
}
