package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v3_0_0.ViewJsonV300
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

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

  
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val ownerView = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)
  lazy val postAccountAccessJson = PostAccountAccessJsonV400(resourceUser2.userId, PostViewJsonV400("_test_view", false))
  lazy val postBodyViewJson = createViewJson
  
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
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / "account-access" / "revoke").POST
      val response400 = makePostRequest(request400, write(postAccountAccessJson))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 and $ApiEndpoint2 version $VersionOfApi - Authorized access") {
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
    }
  }

  
  
}
