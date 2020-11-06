package code.api.v2_1_0

import code.api.ResourceDocs1_4_0.ResourceDocs220
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetEntitlementsForAnyUserAtAnyBank, CanGetEntitlementsForAnyUserAtOneBank}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{ApiRole, ErrorMessages}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag


class EntitlementTests extends V210ServerSetup with DefaultUsers {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v2_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(ResourceDocs220.Implementations2_1_0.getEntitlementsByBankAndUser))
  object ApiEndpoint2 extends Tag(nameOf(ResourceDocs220.Implementations2_1_0.getRoles))

  feature("Assuring that endpoint getRoles works as expected - v2.1.0") {

    scenario("We try to get all roles without credentials - getRoles", VersionOfApi, ApiEndpoint2) {
      When("We make the request")
      val requestGet = (v2_1Request / "roles").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      responseGet.code should equal(401)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get all roles with credentials - getRoles", VersionOfApi, ApiEndpoint2) {
      When("We make the request")
      val requestGet = (v2_1Request / "roles").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }
  }

  feature("Assuring that endpoint getEntitlementsByBankAndUser works as expected - v2.1.0") {

    scenario("We try to get entitlements without login - getEntitlementsByBankAndUser", VersionOfApi, ApiEndpoint1) {
      When("We make the request")
      val requestGet = (v2_1Request / "banks" / testBankId1.value / "users" / resourceUser1.userId / "entitlements").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      responseGet.code should equal(401)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get entitlements without credentials - getEntitlementsByBankAndUser", VersionOfApi, ApiEndpoint1) {
      When("We make the request")
      val requestGet = (v2_1Request / "banks" / testBankId1.value / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      val requiredEntitlements = CanGetEntitlementsForAnyUserAtOneBank ::
        CanGetEntitlementsForAnyUserAtAnyBank::
        Nil
      val requiredEntitlementsTxt = requiredEntitlements.mkString(" or ")
      And("We should get a message: " + s"$requiredEntitlementsTxt entitlements required")
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + requiredEntitlementsTxt)
    }

    scenario("We try to get entitlements with credentials - getEntitlementsByBankAndUser", VersionOfApi, ApiEndpoint1) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementsForAnyUserAtAnyBank.toString)
      And("We make the request")
      val requestGet = (v2_1Request / "banks" / testBankId1.value / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }
  }
}