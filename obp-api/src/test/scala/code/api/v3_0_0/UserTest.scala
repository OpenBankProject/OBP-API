package code.api.v3_0_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ApiVersion, ErrorMessages}
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v2_0_0.JSONFactory200.UsersJsonV200
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag


class UserTest extends V300ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.getUsers))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.getUser))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_0_0.getUserByUserId))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_0_0.getUserByUsername))

  
  feature("Assuring that endpoint Get all Users works as expected - v3.0.0") 
  {

    scenario("We try to get all roles without credentials - Get all Users", VersionOfApi, ApiEndpoint1) {
      When("We make the request")
      val requestGet = (v3_0Request / "users").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.body.extract[ErrorMessage].message should equal (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get all roles with credentials but no roles- Get all Users", VersionOfApi, ApiEndpoint1) 
    {
      When("We make the request")
      val requestGet = (v3_0Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(403)
      And("We should get a message: " + UserHasMissingRoles + CanGetAnyUser)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }
  
  
    scenario(s"We try to get all roles with credentials with ${ApiRole.canGetAnyUser} roles- Get all Users", VersionOfApi, ApiEndpoint1)
    {
      When(s"We first grant the ${ApiRole.canGetAnyUser} to the User1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString())
      
      val requestGet = (v3_0Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      responseGet.body.extract[UsersJsonV200]
    }
    
  }
  
  
  feature("Assuring that Get users by email and Get user by USER_ID works as expected - v3.0.0") 
  {

    scenario("We try to get user data by email without required role " + CanGetAnyUser, VersionOfApi, ApiEndpoint2){

      When("We have to find it by endpoint getUsersByEmail")
      val requestGet = (v3_0Request / "users" / "email" / "some@email.com"/ "terminator").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      org.scalameta.logger.elem(responseGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We try to get all user data without required role " + CanGetAnyUser, VersionOfApi, ApiEndpoint1){

      When("We have to find it by endpoint getUsers")
      val requestGet = (v3_0Request / "users").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We try to get user data by USER_ID without required role " + CanGetAnyUser, VersionOfApi, ApiEndpoint3){

      When("We have to find it by endpoint getUsersByUserId")
      val requestGet = (v3_0Request / "users" / "user_id" / "Arbitrary USER_ID value").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We try to get user data by USERNAME without required role " + CanGetAnyUser, VersionOfApi, ApiEndpoint4){

      When("We have to find it by endpoint getUsersByUsername")
      val requestGet = (v3_0Request / "users" / "username" / "Arbitrary USERNAE value").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We create an user and get it by EMAIL and USER_ID", VersionOfApi, ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4) {

      When("We create a new user")
      val firstName = randomString(8).toLowerCase
      val lastName = randomString(16).toLowerCase
      val userName = randomString(10).toLowerCase
      val email = randomString(10).toLowerCase + "@bar.ai"
      val password = randomString(20)
      val params = Map("email" -> email,
        "username" -> userName,
        "password" -> password,
        "first_name" -> firstName,
        "last_name" -> lastName)

      val request = (v3_0Request / "users").POST
      val response = makePostRequest(request, write(params))
      Then("we should get a 201 created code")
      response.code should equal(201)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, ApiRole.CanGetAnyUser.toString)

      Then("We have to find it by endpoint getUsersByEmail")
      val requestGet = (v3_0Request / "users" / "email" / email / "terminator").GET <@ (user2)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 200")
      responseGet.code should equal(200)
      val user_id = compactRender(response.body \ "user_id").replaceAll("\"", "")

      Then("We try to find the user by USER_ID")
      val requestGet1 = (v3_0Request / "users" / "user_id" / user_id).GET <@ (user2)
      val responseGet1 = makeGetRequest(requestGet1)

      And("We should get a 200")
      responseGet1.code should equal(200)

      And("Email has to be the same")
      compactRender(responseGet1.body \ "email").replaceAll("\"", "") should equal(email)

      Then("We try to find the user by USERNAME")
      val username = compactRender(responseGet1.body \ "username").replaceAll("\"", "")
      val requestGet2 = (v3_0Request / "users" / "username" / username).GET <@ (user2)
      val responseGet2 = makeGetRequest(requestGet2)

      And("We should get a 200")
      responseGet2.code should equal(200)
      And("Email has to be the same")
      compactRender(responseGet2.body \ "email").replaceAll("\"", "") should equal(email)
    }

  }

}
