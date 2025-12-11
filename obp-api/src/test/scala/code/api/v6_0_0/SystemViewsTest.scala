package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetSystemViews
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.APIMethods600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class SystemViewsTest extends V600ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
   * Test tags
   * Example: To run tests with tag "getSystemViews":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getSystemViews))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.getSystemViewById))

  feature(s"Test GET /management/system-views endpoint - $VersionOfApi") {

    scenario("We try to get system views - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "management" / "system-views").GET
      val response = makeGetRequest(request)
      Then("We should get a 401 - User Not Logged In")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario("We try to get system views without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request as user1 without the CanGetSystemViews role")
      val request = (v6_0_0_Request / "management" / "system-views").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 - Missing Required Role")
      response.code should equal(403)
      And("Error message should indicate missing CanGetSystemViews role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetSystemViews)
    }

    scenario("We try to get system views with proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We grant the CanGetSystemViews role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemViews.toString)
      
      And("We make the request as user1 with the CanGetSystemViews role")
      val request = (v6_0_0_Request / "management" / "system-views").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 200 - Success")
      response.code should equal(200)
      
      And("Response should contain a views array")
      val json = response.body
      val viewsArray = (json \ "views").children
      viewsArray.size should be > 0
      
      And("Views should include system views like owner, accountant, auditor")
      val viewIds = viewsArray.map(view => (view \ "id").values.toString)
      viewIds should contain("owner")
    }
  }

  feature(s"Test automatic role guard from ResourceDoc - $VersionOfApi") {

    scenario("Verify that role check is automatic from ResourceDoc configuration", ApiEndpoint1, VersionOfApi) {
      info("This test verifies that the automatic role guard works correctly")
      info("The endpoint should check CanGetSystemViews role automatically")
      info("without explicit hasEntitlement call in the endpoint implementation")
      
      When("A user without the role tries to access the endpoint")
      val requestWithoutRole = (v6_0_0_Request / "management" / "system-views").GET <@ (user1)
      val responseWithoutRole = makeGetRequest(requestWithoutRole)
      
      Then("The automatic role guard should reject the request")
      responseWithoutRole.code should equal(403)
      responseWithoutRole.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetSystemViews)

      When("The same user is granted the required role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemViews.toString)
      val requestWithRole = (v6_0_0_Request / "management" / "system-views").GET <@ (user1)
      val responseWithRole = makeGetRequest(requestWithRole)
      
      Then("The automatic role guard should allow the request")
      responseWithRole.code should equal(200)
      
      info("✓ Automatic role guard from ResourceDoc is working correctly")
    }
  }

  feature(s"Test GET /management/system-views/VIEW_ID endpoint - $VersionOfApi") {

    scenario("We try to get a system view by ID - Anonymous access", ApiEndpoint2, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "management" / "system-views" / "owner").GET
      val response = makeGetRequest(request)
      Then("We should get a 401 - User Not Logged In")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario("We try to get a system view by ID without proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request as user1 without the CanGetSystemViews role")
      val request = (v6_0_0_Request / "management" / "system-views" / "owner").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 - Missing Required Role")
      response.code should equal(403)
      And("Error message should indicate missing CanGetSystemViews role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetSystemViews)
    }

    scenario("We try to get a system view by ID with proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We grant the CanGetSystemViews role to user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemViews.toString)
      
      And("We make the request as user1 with the CanGetSystemViews role for owner view")
      val request = (v6_0_0_Request / "management" / "system-views" / "owner").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 200 - Success")
      response.code should equal(200)
      
      And("Response should contain the owner view details")
      val json = response.body
      val viewId = (json \ "id").values.toString
      viewId should equal("owner")
      
      And("View should be marked as system view")
      val isSystem = (json \ "is_system").values.asInstanceOf[Boolean]
      isSystem should equal(true)
      
      And("View should have permissions defined")
      val canSeeBalance = (json \ "can_see_bank_account_balance").values.asInstanceOf[Boolean]
      canSeeBalance should be(true)
    }

    scenario("We try to get different system views by ID - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We have the CanGetSystemViews role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemViews.toString)
      
      And("We request the accountant view")
      val requestAccountant = (v6_0_0_Request / "management" / "system-views" / "accountant").GET <@ (user1)
      val responseAccountant = makeGetRequest(requestAccountant)
      
      Then("We should get a 200 - Success")
      responseAccountant.code should equal(200)
      val accountantViewId = (responseAccountant.body \ "id").values.toString
      accountantViewId should equal("accountant")
      
      And("We request the auditor view")
      val requestAuditor = (v6_0_0_Request / "management" / "system-views" / "auditor").GET <@ (user1)
      val responseAuditor = makeGetRequest(requestAuditor)
      
      Then("We should get a 200 - Success")
      responseAuditor.code should equal(200)
      val auditorViewId = (responseAuditor.body \ "id").values.toString
      auditorViewId should equal("auditor")
    }

    scenario("We try to get a non-existent system view by ID - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We have the CanGetSystemViews role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemViews.toString)
      
      And("We request a non-existent view")
      val request = (v6_0_0_Request / "management" / "system-views" / "non-existent-view").GET <@ (user1)
      val response = makeGetRequest(request)
      
      Then("We should get a 400 or 404 error")
      response.code should (equal(400) or equal(404))
      
      And("Error message should indicate system view not found")
      response.body.extract[ErrorMessage].message should include("view")
    }
  }

}