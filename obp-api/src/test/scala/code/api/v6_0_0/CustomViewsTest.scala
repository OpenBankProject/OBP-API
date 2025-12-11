package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateCustomView, CanGetCustomViews}
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{InvalidCustomViewFormat, InvalidJsonFormat, UserHasMissingRoles}
import code.api.v6_0_0.APIMethods600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.views.system.ViewDefinition
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{BankId, ViewId}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST.JArray
import org.scalatest.Tag

class CustomViewsTest extends V600ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
   * Test tags
   * Example: To run tests with tag "getCustomViews":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getCustomViews))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.createCustomViewManagement))

  feature(s"Test GET /management/custom-views endpoint - $VersionOfApi") {

    scenario("We try to get custom views - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "management" / "custom-views").GET
      val response = makeGetRequest(request)
      Then("We should get a 401 - User Not Logged In")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario("We try to get custom views without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request as user1 without the CanGetCustomViews role")
      val request = (v6_0_0_Request / "management" / "custom-views").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 - Missing Required Role")
      response.code should equal(403)
      And("Error message should indicate missing CanGetCustomViews role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetCustomViews)
    }

    scenario("We try to get custom views with proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We grant the CanGetCustomViews role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCustomViews.toString)
      
      And("We make the request as user1 with the CanGetCustomViews role")
      val request = (v6_0_0_Request / "management" / "custom-views").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 200 - Success")
      response.code should equal(200)
      
      And("Response should contain a views array")
      val json = response.body
      val viewsArray = (json \ "views").children
      
      And("All returned views should be custom views (names starting with underscore)")
      if (viewsArray.nonEmpty) {
        val viewIds = viewsArray.map(view => (view \ "id").values.toString)
        viewIds.foreach { viewId =>
          viewId should startWith("_")
        }
      }
      
      And("All returned views should have is_system = false")
      if (viewsArray.nonEmpty) {
        viewsArray.foreach { view =>
          val isSystem = (view \ "is_system").values
          isSystem should equal(false)
        }
      }
    }

    scenario("We verify custom views are correctly filtered from system views", ApiEndpoint1, VersionOfApi) {
      When("We grant the CanGetCustomViews role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCustomViews.toString)
      
      And("We make the request to get custom views")
      val request = (v6_0_0_Request / "management" / "custom-views").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 200 - Success")
      response.code should equal(200)
      
      And("Response should not contain system views like owner, accountant, auditor")
      val json = response.body
      val viewsArray = (json \ "views").children
      val viewIds = viewsArray.map(view => (view \ "id").values.toString)
      
      viewIds should not contain "owner"
      viewIds should not contain "accountant"
      viewIds should not contain "auditor"
      viewIds should not contain "standard"
    }
  }

  feature(s"Test automatic role guard from ResourceDoc - $VersionOfApi") {

    scenario("Verify that role check is automatic from ResourceDoc configuration", ApiEndpoint1, VersionOfApi) {
      info("This test verifies that the automatic role guard works correctly")
      info("The endpoint should check CanGetCustomViews role automatically")
      info("without explicit hasEntitlement call in the endpoint implementation")
      
      When("A user without the role tries to access the endpoint")
      val requestWithoutRole = (v6_0_0_Request / "management" / "custom-views").GET <@ (user1)
      val responseWithoutRole = makeGetRequest(requestWithoutRole)
      
      Then("The automatic role guard should reject the request")
      responseWithoutRole.code should equal(403)
      responseWithoutRole.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetCustomViews.toString)
      
      When("The same user is granted the required role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCustomViews.toString)
      val requestWithRole = (v6_0_0_Request / "management" / "custom-views").GET <@ (user1)
      val responseWithRole = makeGetRequest(requestWithRole)
      
      Then("The automatic role guard should allow the request")
      responseWithRole.code should equal(200)
      
      info("✓ Automatic role guard from ResourceDoc is working correctly")
    }
  }

  feature(s"Test custom views naming convention - $VersionOfApi") {

    scenario("Verify all custom views follow naming convention", ApiEndpoint1, VersionOfApi) {
      When("We grant the CanGetCustomViews role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCustomViews.toString)
      
      And("We make the request to get custom views")
      val request = (v6_0_0_Request / "management" / "custom-views").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 200 - Success")
      response.code should equal(200)
      
      And("All custom view IDs should start with underscore")
      val json = response.body
      val viewsArray = (json \ "views").children
      
      if (viewsArray.nonEmpty) {
        info(s"Found ${viewsArray.size} custom view(s)")
        viewsArray.foreach { view =>
          val viewId = (view \ "id").values.toString
          info(s"  - Custom view: $viewId")
          viewId should startWith regex "^_.*"
        }
      } else {
        info("No custom views found in the system (this is OK)")
      }
    }
  }

  feature(s"Test POST /management/banks/BANK_ID/accounts/ACCOUNT_ID/views (Management) endpoint - $VersionOfApi") {

    scenario("We try to create a custom view via management endpoint - Anonymous access", ApiEndpoint2, VersionOfApi) {
      When("We make the request without authentication")
      val viewJson = """
        {
          "name": "_test_view",
          "description": "Test view",
          "metadata_view": "owner",
          "is_public": false,
          "which_alias_to_use": "",
          "hide_metadata_if_alias_used": false,
          "allowed_actions": ["can_see_transaction_this_bank_account"]
        }
      """
      val request = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST
      val response = makePostRequest(request, viewJson)
      Then("We should get a 401 - User Not Logged In")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario("We try to create a custom view via management endpoint without proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request as user1 without the CanCreateCustomView role")
      val viewJson = """
        {
          "name": "_test_view",
          "description": "Test view",
          "metadata_view": "owner",
          "is_public": false,
          "which_alias_to_use": "",
          "hide_metadata_if_alias_used": false,
          "allowed_actions": ["can_see_transaction_this_bank_account"]
        }
      """
      val request = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST <@ (user1)
      val response = makePostRequest(request, viewJson)
      Then("We should get a 403 - Missing Required Role")
      response.code should equal(403)
      And("Error message should indicate missing CanCreateCustomView role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateCustomView)
    }

    scenario("We try to create a custom view via management endpoint with proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We grant the CanCreateCustomView role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCustomView.toString)
      
      And("We make the request as user1 with the CanCreateCustomView role")
      val viewJson = """
        {
          "name": "_my_custom_view",
          "description": "My custom view for testing",
          "metadata_view": "owner",
          "is_public": false,
          "which_alias_to_use": "public",
          "hide_metadata_if_alias_used": true,
          "allowed_actions": ["can_see_transaction_this_bank_account", "can_see_transaction_amount"]
        }
      """
      val request = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST <@ (user1)
      val response = makePostRequest(request, viewJson)
      
      Then("We should get a 201 - Created")
      response.code should equal(201)
      
      And("Response should contain the created view")
      val json = response.body
      val viewId = (json \ "id").values.toString
      viewId should equal("_my_custom_view")
      
      And("View should be marked as custom view (is_system = false)")
      val isSystem = (json \ "is_system").values.asInstanceOf[Boolean]
      isSystem should equal(false)
      
      And("View should have the specified description")
      val description = (json \ "description").values.toString
      description should equal("My custom view for testing")
    }

    scenario("We try to create a view with invalid name via management endpoint - should fail", ApiEndpoint2, VersionOfApi) {
      When("We grant the CanCreateCustomView role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCustomView.toString)
      
      And("We try to create a view without underscore prefix")
      val viewJson = """
        {
          "name": "invalid_system_view",
          "description": "This should fail",
          "metadata_view": "owner",
          "is_public": false,
          "which_alias_to_use": "",
          "hide_metadata_if_alias_used": false,
          "allowed_actions": ["can_see_transaction_this_bank_account"]
        }
      """
      val request = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST <@ (user1)
      val response = makePostRequest(request, viewJson)
      
      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      
      And("Error message should indicate invalid custom view format")
      response.body.extract[ErrorMessage].message should include(InvalidCustomViewFormat)
    }

    scenario("We verify automatic role guard from ResourceDoc configuration for management endpoint", ApiEndpoint2, VersionOfApi) {
      info("This test verifies that the automatic role guard works correctly")
      info("The management endpoint should check CanCreateCustomView role automatically")
      
      When("A user without the role tries to create a custom view via management endpoint")
      val viewJson = """
        {
          "name": "_test_auto_guard",
          "description": "Test automatic guard",
          "metadata_view": "owner",
          "is_public": false,
          "which_alias_to_use": "",
          "hide_metadata_if_alias_used": false,
          "allowed_actions": ["can_see_transaction_this_bank_account"]
        }
      """
      val requestWithoutRole = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST <@ (user1)
      val responseWithoutRole = makePostRequest(requestWithoutRole, viewJson)
      
      Then("The automatic role guard should reject the request")
      responseWithoutRole.code should equal(403)
      responseWithoutRole.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateCustomView.toString)
      
      When("The same user is granted the required role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCustomView.toString)
      val requestWithRole = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST <@ (user1)
      val responseWithRole = makePostRequest(requestWithRole, viewJson)
      
      Then("The automatic role guard should allow the request")
      responseWithRole.code should equal(201)
      
      info("✓ Automatic role guard from ResourceDoc is working correctly")
    }

    scenario("We try to create a custom view via management endpoint with invalid JSON", ApiEndpoint2, VersionOfApi) {
      When("We grant the CanCreateCustomView role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCustomView.toString)
      
      And("We send invalid JSON")
      val invalidJson = """
        {
          "name": "_invalid"
        }
      """
      val request = (v6_0_0_Request / "management" / "banks" / testBankId1.value / "accounts" / testAccountId1.value / "views").POST <@ (user1)
      val response = makePostRequest(request, invalidJson)
      
      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      
      And("Error message should indicate invalid JSON format")
      response.body.extract[ErrorMessage].message should include(InvalidJsonFormat)
    }
  }
}