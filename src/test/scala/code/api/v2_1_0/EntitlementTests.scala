package code.api.v2_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetEntitlementsForAnyUserAtAnyBank, CanGetEntitlementsForAnyUserAtOneBank}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{ApiRole, ErrorMessages}
import code.entitlement.Entitlement
import code.setup.DefaultUsers



/**
 * Created by markom on 10/14/16.
 */
class EntitlementTests extends V210ServerSetup with DefaultUsers {

  feature("Assuring that endpoint getRoles works as expected - v2.1.0") {

    scenario("We try to get all roles without credentials - getRoles") {
      When("We make the request")
      val requestGet = (v2_1Request / "roles").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get all roles with credentials - getRoles") {
      When("We make the request")
      val requestGet = (v2_1Request / "roles").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }
  }

  feature("Assuring that endpoint getEntitlementsByBankAndUser works as expected - v2.1.0") {

    scenario("We try to get entitlements without login - getEntitlementsByBankAndUser") {
      When("We make the request")
      val requestGet = (v2_1Request / "banks" / testBankId1.value / "users" / resourceUser1.userId / "entitlements").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get entitlements without credentials - getEntitlementsByBankAndUser") {
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

    scenario("We try to get entitlements with credentials - getEntitlementsByBankAndUser") {
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