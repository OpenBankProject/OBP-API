package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanDeleteAccountCascade
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class DeleteAccountCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteAccountCascade))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))


  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "accounts" / bankAccount.id).DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "accounts" / bankAccount.id).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteAccountCascade)
    }
  }
  feature(s"test $ApiEndpoint1 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      And("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(addAccountJson))
      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty

      val postBodyView = createViewJson.copy(name = "_cascade_delete", metadata_view = "_cascade_delete", is_public = false)
      createViewViaEndpoint(bankId, account.account_id, postBodyView, user1)
      
      createAccountAttributeViaEndpoint(
        bankId,
        account.account_id,
        "REQUIRED_CHALLENGE_ANSWERS",
        "2",
        "INTEGER"
      )

      grantUserAccessToViewViaEndpoint(
        bankId,
        account.account_id,
        resourceUser2.userId,
        user1,
        PostViewJsonV400(view_id = "owner", is_system = true)
      )

      createWebhookViaEndpoint(
        bankId,
        account.account_id,
        resourceUser1.userId,
        user1
      )

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      And("We make a delete cascade request v4.0.0")
      val deleteRequest400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "accounts" / account.account_id).DELETE <@(user1)
      val deleteResponse400 = makeDeleteRequest(deleteRequest400)
      Then("We should get a 200")
      deleteResponse400.code should equal(200)

      When("We try to delete one more time")
      makeDeleteRequest(request400).code should equal(404)
    }
  }
  

}
