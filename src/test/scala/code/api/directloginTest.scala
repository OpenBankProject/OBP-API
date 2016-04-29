package code.api

import code.api.test.{APIResponse, ServerSetup}
import code.api.util.ErrorMessages
import code.model.dataAccess.OBPUser
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import dispatch._
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.util.Helpers._

import code.api.DirectLogin.registeredApplication




class directloginTest extends ServerSetup {

  val KEY = randomString(20)
  val SECRET = randomString(20)
  val EMAIL = randomString(10)
  val PASSWORD = randomString(20)

  val testConsumer =
    OBPConsumer.create.
      name("test application").
      isActive(true).
      key(KEY).
      secret(SECRET).
      saveMe

  val user =
    OBPUser.create.
      email(EMAIL).
      password(PASSWORD).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")

  val invalidUsernamePasswordHeader = ("Authorization", ("DirectLogin username=\"does-not-exist@example.com\", " +
    "password=\"no-good-password\", consumer_key=%s").format(KEY))

  val invalidConsumerKeyHeader = ("Authorization", ("DirectLogin username=%s, " +
    "password=%s, consumer_key=%s").format(EMAIL, PASSWORD, "invalid"))

  val validHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(EMAIL, PASSWORD, KEY))

  val invalidUsernamePasswordHeaders = List(accessControlOriginHeader, invalidUsernamePasswordHeader)

  val invalidConsumerKeyHeaders = List(accessControlOriginHeader, invalidConsumerKeyHeader)

  val validHeaders = List(accessControlOriginHeader, validHeader)

  def directLoginRequest = baseRequest / "my" / "logins" / "direct"

  feature("DirectLogin") {
    scenario("Invalid auth header") {
      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      assert(registeredApplication(KEY) == true)

      When("we try to login without an Authorization header")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", List(accessControlOriginHeader))

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.DirectLoginMissingParameters)
    }

    scenario("Invalid credentials") {
      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      assert(registeredApplication(KEY) == true)

      When("we try to login with an invalid username/password")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", invalidUsernamePasswordHeaders)

      Then("We should get a 401 - Unauthorized")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidLoginCredentials)
    }

    scenario("Missing DirecLogin header") {

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      assert(registeredApplication(KEY) == true)

      When("we try to login with a missing DirectLogin header")
      val request = directLoginRequest
      val response = makePostRequest(request,"")

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.DirectLoginMissingParameters)
    }

    scenario("Login without consumer key") {

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      assert(registeredApplication(KEY) == true)

      When("the consumer key is invalid")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", invalidConsumerKeyHeaders)

      Then("We should get a 401 - Unauthorized")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidLoginCredentials)
    }

    scenario("Login with correct everything!") {

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validHeaders)

      Then("We should get a 200 - OK and a token")
      response.code should equal(200)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
        case _ => fail("Expected a token")
      }

      // TODO Check that we are logged in. TODO Add an endpoint like /me that returns the currently logged in user.
//      When("when we use the token it should work")
//      val request2 = directLoginRequest
//      val response2 = makePostRequestAdditionalHeader(request2, "", validHeaders)
//
//      Then("We should get a 200 - OK and a token")


    }
  }

  private def assertResponse(response: APIResponse, expectedErrorMessage: String): Unit = {
    response.body match {
      case JObject(List(JField(name, JString(value)))) =>
        name should equal("error")
        value should startWith(expectedErrorMessage)
      case _ => fail("Expected an error message")
    }
  }
}