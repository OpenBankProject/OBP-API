package code.api.v2_0_0

import code.api.util.ApiRole.CanGetEntitlementsForAnyUserAtAnyBank
import code.api.util.{ApiRole, ErrorMessages}
import code.entitlement.Entitlement
import net.liftweb.json.JsonAST._
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.UserDoesNotHaveRole
import code.setup.DefaultUsers


/**
  * Created by markom on 10/14/16.
  */
class EntitlementTests extends V200ServerSetup with DefaultUsers {

   override def beforeAll() {
     super.beforeAll()
   }

   override def afterAll() {
     super.afterAll()
   }

  feature("Assuring that endpoint getEntitlements works as expected - v2.0.0") {

    scenario("We try to get entitlements without login - getEntitlements") {
      When("We make the request")
      val requestGet = (v2_0Request / "users" / resourceUser1.userId / "entitlements").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We must get a 400")
      responseGet.code must equal(400)
      val error = for { JObject(o) <- responseGet.body; JField("error", JString(error)) <- o } yield error
      And("We must get a message: " + ErrorMessages.UserNotLoggedIn)
      error must contain (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get entitlements without credentials - getEntitlements") {
      When("We make the request")
      val requestGet = (v2_0Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We must get a 400")
      responseGet.code must equal(400)
      val error = for { JObject(o) <- responseGet.body; JField("error", JString(error)) <- o } yield error
      And("We must get a message: " + s"$CanGetEntitlementsForAnyUserAtAnyBank entitlement required")
      error must contain (UserDoesNotHaveRole + CanGetEntitlementsForAnyUserAtAnyBank)
    }

    scenario("We try to get entitlements with credentials - getEntitlements") {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementsForAnyUserAtAnyBank.toString)
      And("We make the request")
      val requestGet = (v2_0Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We must get a 200")
      responseGet.code must equal(200)
    }
  }


 }