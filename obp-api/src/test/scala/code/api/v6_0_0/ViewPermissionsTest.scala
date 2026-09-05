package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetViewPermissionsAtAllBanks
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class ViewPermissionsTest extends V600ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
   * Test tags
   * Example: To run tests with tag "getViewPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getViewPermissions))

  feature(s"Test GET /management/view-permissions endpoint - $VersionOfApi") {

    scenario("We try to get view permissions - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "management" / "view-permissions").GET
      val response = makeGetRequest(request)
      Then("We should get a 401 - User Not Logged In")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("We try to get view permissions without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request as user1 without the CanGetViewPermissionsAtAllBanks role")
      val request = (v6_0_0_Request / "management" / "view-permissions").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 - Missing Required Role")
      response.code should equal(403)
      And("Error message should indicate missing CanGetViewPermissionsAtAllBanks role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetViewPermissionsAtAllBanks)
    }

    scenario("We try to get view permissions with proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We grant the CanGetViewPermissionsAtAllBanks role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetViewPermissionsAtAllBanks.toString)
      
      And("We make the request as user1 with the CanGetViewPermissionsAtAllBanks role")
      val request = (v6_0_0_Request / "management" / "view-permissions").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 200 - Success")
      response.code should equal(200)
      
      And("Response should contain a permissions array")
      val json = response.body
      val permissionsArray = (json \ "permissions").children
      permissionsArray.size should be > 0
      
      And("Each permission should have permission and category fields")
      permissionsArray.foreach { permission =>
        (permission \ "permission").values.toString should not be empty
        (permission \ "category").values.toString should not be empty
      }
      
      And("Permissions should include standard view permissions")
      val permissionNames = permissionsArray.map(p => (p \ "permission").values.toString)
      permissionNames should contain("can_see_transaction_amount")
      permissionNames should contain("can_see_bank_account_balance")
      permissionNames should contain("can_create_custom_view")
      permissionNames should contain("can_grant_access_to_views")
      
      And("Permissions should have appropriate categories")
      val categories = permissionsArray.map(p => (p \ "category").values.toString).distinct
      categories.size should be > 0
    }

    scenario("Verify all permission constants are included", ApiEndpoint1, VersionOfApi) {
      When("We grant the CanGetViewPermissionsAtAllBanks role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetViewPermissionsAtAllBanks.toString)
      
      And("We make the request as user1")
      val request = (v6_0_0_Request / "management" / "view-permissions").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("Response should include all key permissions")
      val json = response.body
      val permissionNames = (json \ "permissions").children.map(p => (p \ "permission").values.toString)
      
      // Transaction permissions
      permissionNames should contain("can_see_transaction_this_bank_account")
      permissionNames should contain("can_see_transaction_other_bank_account")
      permissionNames should contain("can_see_transaction_metadata")
      permissionNames should contain("can_see_transaction_description")
      
      // Account permissions
      permissionNames should contain("can_see_bank_account_owners")
      permissionNames should contain("can_see_bank_account_iban")
      permissionNames should contain("can_see_bank_account_number")
      permissionNames should contain("can_update_bank_account_label")
      
      // Counterparty permissions
      permissionNames should contain("can_see_other_account_iban")
      permissionNames should contain("can_add_counterparty")
      permissionNames should contain("can_delete_counterparty")
      
      // Metadata permissions
      permissionNames should contain("can_see_comments")
      permissionNames should contain("can_add_comment")
      permissionNames should contain("can_see_tags")
      permissionNames should contain("can_add_tag")
      
      // Transaction Request permissions
      permissionNames should contain("can_add_transaction_request_to_own_account")
      permissionNames should contain("can_add_transaction_request_to_any_account")
      permissionNames should contain("can_see_transaction_requests")
      
      // View Management permissions
      permissionNames should contain("can_create_custom_view")
      permissionNames should contain("can_delete_custom_view")
      permissionNames should contain("can_update_custom_view")
      permissionNames should contain("can_see_available_views_for_bank_account")
      
      // Access Control permissions
      permissionNames should contain("can_grant_access_to_views")
      permissionNames should contain("can_revoke_access_to_views")
      permissionNames should contain("can_grant_access_to_custom_views")
      permissionNames should contain("can_revoke_access_to_custom_views")
    }
  }
}