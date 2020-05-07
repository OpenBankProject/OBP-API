package code.api.v2_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v2_0_0.JSONFactory200.UsersJsonV200
import code.entitlement.Entitlement

class UserTests extends V210ServerSetup {

  feature("Assuring that endpoint Get all Users works as expected - v2.1.0") 
  {

    scenario("We try to get all roles without credentials - Get all Users") {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      responseGet.code should equal(401)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get all roles with credentials but no roles- Get all Users") 
    {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(403)
      And("We should get a message: " + ErrorMessages.UserHasMissingRoles)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
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