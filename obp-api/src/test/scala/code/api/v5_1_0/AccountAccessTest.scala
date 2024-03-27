package code.api.v5_1_0

import code.api.Constant.{SYSTEM_AUDITOR_VIEW_ID, SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID, SYSTEM_OWNER_VIEW_ID}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJsonV300
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{UserLacksPermissionCanGrantAccessToViewForTargetAccount, UserNotLoggedIn}
import code.api.v3_0_0.ViewJsonV300
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.{PostAccountAccessJsonV400, PostViewJsonV400}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AccountAccessTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.grantUserAccessToViewById))

  
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val ownerView = SYSTEM_OWNER_VIEW_ID
  lazy val managerCustomView = SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID
  lazy val postAccountAccessJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400("_test_view", false))
  lazy val postBodyViewJson = createViewJsonV300.toCreateViewJson
  
  def createAnAccount(bankId: String, user: Option[(Consumer,Token)]): CreateAccountResponseJsonV310 = {
    val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
    val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" ).POST <@(user1)
    val response510 = makePostRequest(request510, write(addAccountJson))
    Then("We should get a 201")
    
    response510.code should equal(201)
    response510.body.extract[CreateAccountResponseJsonV310]
  }
  
  def createViewForAnAccount(bankId: String, accountId: String): ViewJsonV300 = {
    createViewViaEndpoint(bankId, accountId, postBodyViewJson, user1)
  }

  feature(s"test $ApiEndpoint1  Authorized access") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / ownerView /"account-access" / "grant").POST
      val response510 = makePostRequest(request510, write(postAccountAccessJson))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    
    scenario("We will call the endpoint with user credentials and system view, but try to grant custom view access", VersionOfApi, ApiEndpoint1) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      val view = createViewForAnAccount(bankId, account.account_id)
      val postJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400(view.id, view.is_system))
      When("We send the request")
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / account.account_id / ownerView / "account-access" / "grant").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString.contains(UserLacksPermissionCanGrantAccessToViewForTargetAccount)
    }
    
    scenario("We will call the endpoint with user credentials and managerCustomView view, but try to grant system view access", VersionOfApi, ApiEndpoint1) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      val postJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400(SYSTEM_AUDITOR_VIEW_ID, true))
      When("We send the request")
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / account.account_id / managerCustomView / "account-access" / "grant").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString.contains(UserLacksPermissionCanGrantAccessToViewForTargetAccount)
    }
    
    scenario("We will call the endpoint with user credentials and system view permission", VersionOfApi, ApiEndpoint1) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      val postJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400(SYSTEM_AUDITOR_VIEW_ID, true))
      When("We send the request")
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / account.account_id / ownerView / "account-access" / "grant").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 201 and check the response body")
      response.code should equal(201)
      response.body.extract[ViewJsonV300]
    }
    
    scenario("We will call the endpoint with user credentials and custom view permission", VersionOfApi, ApiEndpoint1) {
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val account = try {
        createAnAccount(bankId, user1)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      val view = createViewForAnAccount(bankId, account.account_id)
      val postJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400(view.id, view.is_system))
      When("We send the request")
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / account.account_id / managerCustomView / "account-access" / "grant").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 201 and check the response body")
      response.code should equal(201)
      response.body.extract[ViewJsonV300]
    }
  }

  
  
}
