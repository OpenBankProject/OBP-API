package code.api

import code.api.test.{APIResponse, ServerSetup}
import code.api.util.ErrorMessages
import code.model.dataAccess.OBPUser
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import dispatch._
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.util.Helpers._

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
  val invalidUsernamePasswordHeader = ("Authorization", ("DirectLogin username=\"jane@example.com\", " +
    "password=\"the-password-of-jane\", consumer_key=%s").format(KEY))
  val invalidConsumerKeyHeader = ("Authorization", ("DirectLogin username=%s, " +
    "password=%s, consumer_key=%s").format(EMAIL, PASSWORD, "invalid"))
  val validHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(EMAIL, PASSWORD, KEY))

  val invalidUsernamePasswordHeaders = List(accessControlOriginHeader, invalidUsernamePasswordHeader)
  val invalidConsumerKeyHeaders = List(accessControlOriginHeader, invalidConsumerKeyHeader)
  val validHeaders = List(accessControlOriginHeader, validHeader)

  def directLoginRequest = baseRequest / "my" / "logins" / "direct"

  feature("DirectLogin") {
    scenario("we try to login without an Authorization header") {
      Given("The application is running")
      When("the request is sent")
      val request = directLoginRequest.GET
      val response = makeGetRequest(request, List(accessControlOriginHeader))

      Then("We should get a 401")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.DirectLoginMissingParameters)
    }

    scenario("we try to login with an invalid username/password") {
      Given("The application is running")
      When("the request is sent")
      val request = directLoginRequest.GET
      val response = makeGetRequest(request, invalidUsernamePasswordHeaders)

      Then("We should get a 401")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidLoginCredentials)
    }

    scenario("we try to login with a missing DirectLogin header") {
      Given("The application is running")
      When("the request is sent")
      val request = directLoginRequest.GET
      val response = makeGetRequest(request)

      Then("We should get a 400")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.DirectLoginMissingParameters)
    }

    scenario("we try to login with DirectLogin but the application is not registered") {
      Given("The application is running")
      When("the request is sent")
      val request = directLoginRequest.GET
      val response = makeGetRequest(request, invalidConsumerKeyHeaders)

      Then("We should get a 401")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidLoginCredentials)
    }

    scenario("we try to login with a valid DirectLogin header") {
      Given("The application is running")
      When("the request is sent")
      val request = directLoginRequest.GET
      val response = makeGetRequest(request, validHeaders)

      Then("We should get a 200 and a token")
      response.code should equal(200)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
        case _ => fail("Expected a token")
      }
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