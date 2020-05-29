package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.ApiRole.{CanGetEntitlementsForAnyBank, CanGetEntitlementsForAnyUserAtAnyBank, CanGetEntitlementsForOneBank}
import code.api.util.ErrorMessages.{UserHasMissingRoles, _}
import code.api.util.{ApiRole, ErrorMessages}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.api.util.APIUtil.OAuth._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class EntitlementTests extends V400ServerSetupAsync with DefaultUsers {

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
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getEntitlementsForBank))

  feature("Assuring that endpoint getEntitlements works as expected - v4.0.0") {

    scenario("We try to get entitlements without login - getEntitlements", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET
      val responseGet = makeGetRequestAsync(requestGet)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet map { r =>
          r.code should equal(401)
          r.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
      }
    }

    scenario("We try to get entitlements without credentials - getEntitlements", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequestAsync(requestGet)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanGetEntitlementsForAnyUserAtAnyBank entitlement required")
      responseGet map { r =>
          r.code should equal(403)
          r.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank)
      }
    }

    scenario("We try to get entitlements with credentials - getEntitlements", ApiEndpoint1, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementsForAnyUserAtAnyBank.toString)
      And("We make the request")
      val requestGet = (v4_0_0_Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequestAsync(requestGet)
      Then("We should get a 200")
      responseGet map { r =>
          r.code should equal(200)
      }
    }

    scenario("We try to get entitlements without roles - getEntitlementsForBank", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / testBankId1.value / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequestAsync(requestGet)
      Then("We should get a 403")

      responseGet map { r =>
        r.code should equal(403)
        r.body.extract[ErrorMessage].message contains(CanGetEntitlementsForOneBank.toString()) should be (true)
        r.body.extract[ErrorMessage].message contains(CanGetEntitlementsForAnyBank.toString) should be (true)
      }
    }

    scenario("We try to get entitlements with CanGetEntitlementsForOneBank role - getEntitlementsForBank", ApiEndpoint2, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanGetEntitlementsForOneBank.toString)
      And("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / testBankId1.value / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequestAsync(requestGet)
      Then("We should get a 200")
      responseGet map { r =>
        r.body.extract[EntitlementsJsonV400]
        r.code should equal(200)
      }
    }

    scenario("We try to get entitlements with CanGetEntitlementsForAnyBank role - getEntitlementsForBank", ApiEndpoint2, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementsForAnyBank.toString)
      And("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / testBankId1.value / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequestAsync(requestGet)
      Then("We should get a 200")
      responseGet map { r =>
        r.body.extract[EntitlementsJsonV400]
        r.code should equal(200)
      }
    }
  }


 }