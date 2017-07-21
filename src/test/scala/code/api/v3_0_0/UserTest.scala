package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString


class UserTest extends V300ServerSetup with DefaultUsers {

  feature("Assuring that Get users by email and Get user by USER_ID works as expected - v3.0.0") {

    scenario("We try to get user data by email without required role " + CanGetAnyUser){

      When("We have to find it by endpoint getUsersByEmail")
      val requestGet = (v3_0Request / "users" / "email" / "some@email.com"/ "terminator").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 400")
      responseGet.code should equal(400)
      compactRender(responseGet.body \ "error").replaceAll("\"", "") should equal(UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We try to get user data by USER_ID without required role " + CanGetAnyUser){

      When("We have to find it by endpoint getUsersByEmail")
      val requestGet = (v3_0Request / "users" / "user_id" / "Arbitrary USER_ID value").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 400")
      responseGet.code should equal(400)
      compactRender(responseGet.body \ "error").replaceAll("\"", "") should equal(UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We try to get user data by USERNAME without required role " + CanGetAnyUser){

      When("We have to find it by endpoint getUsersByEmail")
      val requestGet = (v3_0Request / "users" / "username" / "Arbitrary USERNAE value").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 400")
      responseGet.code should equal(400)
      compactRender(responseGet.body \ "error").replaceAll("\"", "") should equal(UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("We create an user and get it by EMAIL and USER_ID") {

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

      val request = (v2_0Request / "users").POST
      val response = makePostRequest(request, write(params))
      Then("we should get a 201 created code")
      response.code should equal(201)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)

      Then("We have to find it by endpoint getUsersByEmail")
      val requestGet = (v3_0Request / "users" / "email" / email / "terminator").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 200")
      responseGet.code should equal(200)
      val user_id = compactRender(response.body \ "user_id").replaceAll("\"", "")

      Then("We try to find the user by USER_ID")
      val requestGet1 = (v3_0Request / "users" / "user_id" / user_id).GET <@ (user1)
      val responseGet1 = makeGetRequest(requestGet1)

      And("We should get a 200")
      responseGet1.code should equal(200)

      And("Email has to be the same")
      compactRender(responseGet1.body \ "email").replaceAll("\"", "") should equal(email)

      Then("We try to find the user by USERNAME")
      val username = compactRender(responseGet1.body \ "username").replaceAll("\"", "")
      val requestGet2 = (v3_0Request / "users" / "username" / username).GET <@ (user1)
      val responseGet2 = makeGetRequest(requestGet2)

      And("We should get a 200")
      responseGet2.code should equal(200)
      And("Email has to be the same")
      compactRender(responseGet2.body \ "email").replaceAll("\"", "") should equal(email)
    }

  }

}
