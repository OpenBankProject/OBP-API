package code.api.v2_1_0


import code.api.util.APIUtil.OAuth._
import code.api.DefaultUsers
import net.liftweb.json.JsonAST._
import code.api.util.{ErrorMessages}



/**
 * Created by markom on 10/14/16.
 */
class EntitlementTests extends V210ServerSetup with DefaultUsers {

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint getRoles works as expected - v2.1.0") {

    scenario("We try to get all roles without credentials - getRoles") {
      When("We make the request")
      val requestGet = (v2_1Request / "roles").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 400")
      responseGet.code should equal(400)
      val error = for { JObject(o) <- responseGet.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      error should contain (ErrorMessages.UserNotLoggedIn)

    }

    scenario("We try to get all roles with credentials - getRoles") {
      When("We make the request")
      val requestGet = (v2_1Request / "roles").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
    }
  }
}