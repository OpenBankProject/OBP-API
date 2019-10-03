package code.api.v4_0_0

import code.api.ErrorMessage
import code.api.util.ApiRole.CanGetEntitlementsForAnyUserAtAnyBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, _}
import code.api.util.{ApiRole, ApiVersion, ErrorMessages}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.api.util.APIUtil.OAuth._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class EntitlementTests extends V400ServerSetup with DefaultUsers {

   override def beforeAll() {
     super.beforeAll()
   }

   override def afterAll() {
     super.afterAll()
   }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getEntitlements))

  feature("Assuring that endpoint getEntitlements works as expected - v4.0.0") {

    scenario("We try to get entitlements without login - getEntitlements", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)
    }

    scenario("We try to get entitlements without credentials - getEntitlements", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 40")
      responseGet.code should equal(403)
      And("We should get a message: " + s"$CanGetEntitlementsForAnyUserAtAnyBank entitlement required")
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank)
    }

    scenario("We try to get entitlements with credentials - getEntitlements", ApiEndpoint1, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementsForAnyUserAtAnyBank.toString)
      And("We make the request")
      val requestGet = (v4_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }
  }


 }