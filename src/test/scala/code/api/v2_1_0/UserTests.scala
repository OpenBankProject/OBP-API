package code.api.v2_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v2_0_0.JSONFactory200.UsersJsonV200
import code.entitlement.Entitlement
import code.setup.User1AllPrivileges
import net.liftweb.json.JsonAST._


/**
 * Created by markom on 10/14/16.
 */
class UserTests extends V210ServerSetup with User1AllPrivileges {

  feature("Assuring that endpoint Get all Users works as expected - v2.1.0") 
  {

    scenario("We try to get all roles without credentials - Get all Users") {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      val error = for { JObject(o) <- responseGet.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      error should contain (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get all roles with credentials but no roles- Get all Users") 
    {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(403)
      val error = for { JObject(o) <- responseGet.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.UserHasMissingRoles)
      error.toString contains (ErrorMessages.UserHasMissingRoles) should be (true)
    }
  
  
    scenario(s"We try to get all roles with credentials with ${ApiRole.canGetAnyUser} roles- Get all Users")
    {
      When(s"We first grant the ${ApiRole.canGetAnyUser} to the User1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString())
      
      val requestGet = (v2_1Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      responseGet.body.extract[UsersJsonV200]
    }
    
  }
  
}