package code.api.v2_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetEntitlementsForAnyUserAtAnyBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, _}
import code.api.util.{ApiRole, ErrorMessages}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import net.liftweb.json.Serialization.write

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
      Then("We should get a 401")
      responseGet.code should equal(401)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get entitlements without roles - getEntitlements") {
      When("We make the request")
      val requestGet = (v2_0Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      And("We should get a message: " + s"$CanGetEntitlementsForAnyUserAtAnyBank entitlement required")
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank)
    }

    scenario("We try to get entitlements with roles - getEntitlements") {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementsForAnyUserAtAnyBank.toString)
      And("We make the request")
      val requestGet = (v2_0Request / "users" / resourceUser1.userId / "entitlements").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }

    scenario("We try to delete some entitlement - deleteEntitlement") {
      When("We add required entitlement")
      val ent = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString).openOrThrowException(attemptedToOpenAnEmptyBox)
      And("We make the request")
      val requestDelete = (v2_0Request / "users" / resourceUser1.userId / "entitlement" / ent.entitlementId).DELETE <@ (user1)
      And("We grant the role to the user")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canDeleteEntitlementAtAnyBank.toString)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)
    }

    scenario("We try to create entitlement - addEntitlement-canCreateEntitlementAtOneBank") {
      val requestBody = SwaggerDefinitionsJSON.createEntitlementJSON
      And("We make the request")
      val requestPost = (v2_0Request / "users" / resourceUser1.userId / "entitlements").POST <@ (user1)
      And("We grant the role to the user")
      val responsePost = makePostRequest(requestPost , write(requestBody))

      Then("We should get a 403")
      responsePost.code should equal(403)
      responsePost.body.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the canCreateEntitlementAtOneBank role")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateEntitlementAtOneBank.toString)
      
      Then("We call addEntitlement with canCreateEntitlementAtOneBank, but wrong bankId .")
      val responsePost2 = makePostRequest(requestPost , write(requestBody))
      responsePost2.code should equal(403)

      Then("We call addEntitlement with canCreateEntitlementAtOneBank.")
      val requestBody2 = SwaggerDefinitionsJSON.createEntitlementJSON.copy(bank_id = testBankId1.value)
      val responsePost3 = makePostRequest(requestPost , write(requestBody2))
      
      Then("We should get a 201")
      responsePost3.code should equal(201)
      responsePost3.body.extract[EntitlementJSON].bank_id should equal(testBankId1.value) 
    }

    scenario("We try to create entitlement - addEntitlement-canCreateEntitlementAtAnyBank") {
      val requestBody = SwaggerDefinitionsJSON.createEntitlementJSON.copy(bank_id = testBankId1.value)
      And("We make the request")
      val requestPost = (v2_0Request / "users" / resourceUser1.userId / "entitlements").POST <@ (user1)
      And("We grant the role to the user")
      val responsePost = makePostRequest(requestPost , write(requestBody))

      Then("We should get a 403")
      responsePost.code should equal(403)
      responsePost.body.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the canCreateEntitlementAtOneBank role")
      Entitlement.entitlement.vend.addEntitlement("wrongbankId", resourceUser1.userId, ApiRole.canCreateEntitlementAtOneBank.toString)

      Then("We call addEntitlement with canCreateEntitlementAtOneBank, but wrong bankId .")
      val responsePost2 = makePostRequest(requestPost , write(requestBody))
      responsePost2.code should equal(403)

      Then("We call addEntitlement with canCreateEntitlementAtOneBank, but correct bankId .")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateEntitlementAtAnyBank.toString)
      val responsePost3 = makePostRequest(requestPost , write(requestBody))

      Then("We should get a 201")
      responsePost3.code should equal(201)
      responsePost3.body.extract[EntitlementJSON].bank_id should equal(testBankId1.value)
    }
  }


 }