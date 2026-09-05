package code.api.v2_1_0

import com.openbankproject.commons.model.ErrorMessage
import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v2_0_0.JSONFactory200.UsersJsonV200
import code.entitlement.Entitlement

class UserTests extends V210ServerSetup {

  Feature("Assuring that endpoint Get all Users works as expected - v2.1.0") {

    Scenario("We try to get all roles without credentials - Get all Users") {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      responseGet.code should equal(401)
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.AuthenticatedUserIsRequired)

    }

    Scenario("We try to get all roles with credentials but no roles- Get all Users") {
      When("We make the request")
      val requestGet = (v2_1Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(403)
      And("We should get a message: " + ErrorMessages.UserHasMissingRoles)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }
  
  
    Scenario(s"We try to get all roles with credentials with ${ApiRole.canGetAnyUser} roles- Get all Users") {
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