package code.api

import code.api.util.ErrorMessages
import code.loginattempts.LoginAttempt
import code.model.dataAccess.OBPUser
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.scalatest.BeforeAndAfter




class directloginTest extends ServerSetup with BeforeAndAfter {

  val KEY = randomString(40).toLowerCase
  val SECRET = randomString(40).toLowerCase
  val EMAIL = randomString(10).toLowerCase + "@example.com"
  val USERNAME = randomString(10).toLowerCase
  val PASSWORD = randomString(20)

  val KEY_DISABLED = randomString(40).toLowerCase
  val SECRET_DISABLED = randomString(40).toLowerCase
  val EMAIL_DISABLED = randomString(10).toLowerCase + "@example.com"
  val USERNAME_DISABLED = randomString(10).toLowerCase
  val PASSWORD_DISABLED = randomString(20)

  before {
    if (OBPUser.find(By(OBPUser.username, USERNAME)).isEmpty)
      OBPUser.create.
        email(EMAIL).
        username(USERNAME).
        password(PASSWORD).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe

    if (OBPConsumer.find(By(OBPConsumer.key, KEY)).isEmpty)
      OBPConsumer.create.
        name("test application").
        isActive(true).
        key(KEY).
        secret(SECRET).
        saveMe


    if (OBPUser.find(By(OBPUser.username, USERNAME_DISABLED)).isEmpty)
      OBPUser.create.
        email(EMAIL_DISABLED).
        username(USERNAME_DISABLED).
        password(PASSWORD_DISABLED).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe

    if (OBPConsumer.find(By(OBPConsumer.key, KEY_DISABLED)).isEmpty)
      OBPConsumer.create.
        name("Test application disabled").
        isActive(false).
        key(KEY_DISABLED).
        secret(SECRET_DISABLED).
        saveMe
  }

  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")

  val invalidUsernamePasswordHeader = ("Authorization", ("DirectLogin username=\"notExistingUser\", " +
    "password=\"notExistingPassword\", consumer_key=%s").format(KEY))

  val invalidUsernamePasswordCharaterHeader = ("Authorization", ("DirectLogin username=\" a#s \", " +
    "password=\"no-good-password\", consumer_key=%s").format(KEY))

  val validUsernameInvalidPasswordHeader = ("Authorization", ("DirectLogin username=%s," +
    "password=\"notExistingPassword\", consumer_key=%s").format(USERNAME, KEY))

  val invalidConsumerKeyHeader = ("Authorization", ("DirectLogin username=%s, " +
    "password=%s, consumer_key=%s").format(USERNAME, PASSWORD, "invalid"))

  val validHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(USERNAME, PASSWORD, KEY))

  val disabledConsumerValidHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(USERNAME_DISABLED, PASSWORD_DISABLED, KEY_DISABLED))

  val invalidUsernamePasswordCharaterHeaders = List(accessControlOriginHeader, invalidUsernamePasswordCharaterHeader)

  val invalidUsernamePasswordHeaders = List(accessControlOriginHeader, invalidUsernamePasswordHeader)

  val validUsernameInvalidPasswordHeaders = List(accessControlOriginHeader, validUsernameInvalidPasswordHeader)

  val invalidConsumerKeyHeaders = List(accessControlOriginHeader, invalidConsumerKeyHeader)

  val validHeaders = List(accessControlOriginHeader, validHeader)

  val disabledConsumerKeyHeaders = List(accessControlOriginHeader, disabledConsumerValidHeader)

  def directLoginRequest = baseRequest / "my" / "logins" / "direct"

  feature("DirectLogin") {
    scenario("Invalid auth header") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      val consumers =  OBPConsumer.findAll()
      //assert(registeredApplication(KEY) == true)

      When("we try to login without an Authorization header")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", List(accessControlOriginHeader))

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.DirectLoginMissingParameters)
    }

    scenario("Invalid credentials") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("we try to login with an invalid username/password")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", invalidUsernamePasswordHeaders)

      Then("We should get a 401 - Unauthorized")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidLoginCredentials)
    }

    scenario("Invalid Characters") {
      When("we try to login with an invalid username Characters and invalid password Characters")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", invalidUsernamePasswordCharaterHeaders)

      Then("We should get a 400 - Invalid Characters")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.InvalidValueCharacters)
    }

    scenario("valid Username, invalid password, login in too many times. The username will be locked") {
      When("login with an valid username and invalid password, failed more than 5 times.")
      val request = directLoginRequest
      var response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)

      response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)
      response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)
      response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)
      response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)
      response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)
      response = makePostRequestAdditionalHeader(request, "", validUsernameInvalidPasswordHeaders)

      Then("We should get a 401 - the username has been locked")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.UsernameHasBeenLocked)

      Then("We login in with the valid username and valid passpord, the username still be locked ")
      response = makePostRequestAdditionalHeader(request, "", validHeaders)
      Then("We should get a 401 - the username has been locked")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.UsernameHasBeenLocked)

      Then("We unlock the username")
      LoginAttempt.resetBadLoginAttempts(USERNAME)
    }

    scenario("Consumer API key is disabled") {
      Given("The app we are testing is registered and disabled")
      When("We try to login with username/password")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", disabledConsumerKeyHeaders)
      Then("We should get a 401")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidConsumerKey)
    }

    scenario("Missing DirecLogin header") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("we try to login with a missing DirectLogin header")
      val request = directLoginRequest
      val response = makePostRequest(request,"")

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.DirectLoginMissingParameters)
    }

    scenario("Login without consumer key") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the consumer key is invalid")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", invalidConsumerKeyHeaders)

      Then("We should get a 401 - Unauthorized")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidConsumerKey)
    }

    scenario("Login with correct everything!") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validHeaders)
      var token = "INVALID"
      Then("We should get a 200 - OK and a token")
      response.code should equal(200)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
          token = value
        case _ => fail("Expected a token")
      }

      // TODO Check that we are logged in. TODO Add an endpoint like /me that returns the currently logged in user.
      When("when we use the token it should work")
      val headerWithToken = ("Authorization", "DirectLogin token=%s".format(token))
      val validHeadersWithToken = List(accessControlOriginHeader, headerWithToken)
      val request2 = baseRequest / "obp" / "v2.0.0" / "my" / "accounts"
      val response2 = makeGetRequest(request2, validHeadersWithToken)

      Then("We should get a 200 - OK and an empty list of accounts")
      response2.code should equal(200)
      response2.body match {
        case JArray(List()) =>
        case _ => fail("Expected empty list of accounts")
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