package code.api

import code.api.Constant.localIdentityProvider
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages._
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_0_0.UserJsonV300
import code.consumer.Consumers
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.setup.{APIResponse, ServerSetup}
import code.userlocks.UserLocksProvider
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.scalatest.{BeforeAndAfter, Tag}



class DirectLoginTest extends ServerSetup with BeforeAndAfter {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi200 extends Tag(ApiVersion.v2_0_0.toString)
  object VersionOfApi300 extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations2_0_0.getCurrentUser))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.getCurrentUser))

  val KEY = randomString(40).toLowerCase
  val SECRET = randomString(40).toLowerCase
  val EMAIL = randomString(10).toLowerCase + "@example.com"
  val USERNAME = "username with spaces"
  //these are password, but sonarcloud: "password" detected here, make sure this is not a hard-coded credential.
  val NO_EXISTING_PW = "notExistingPassword"
  val VALID_PW = """G!y"k9GHD$D"""

  val KEY_DISABLED = randomString(40).toLowerCase
  val SECRET_DISABLED = randomString(40).toLowerCase
  val EMAIL_DISABLED = randomString(10).toLowerCase + "@example.com"
  val USERNAME_DISABLED = randomString(10).toLowerCase
  val PASSWORD_DISABLED = randomString(20)

  before {
    if (AuthUser.find(By(AuthUser.username, USERNAME)).isEmpty)
      AuthUser.create.
        email(EMAIL).
        username(USERNAME).
        password(VALID_PW).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe

    if (Consumers.consumers.vend.getConsumerByConsumerKey(KEY).isEmpty)
      Consumers.consumers.vend.createConsumer(Some(KEY), Some(SECRET), Some(true), Some("test application"), None, Some("description"), Some("eveline@example.com"), None, None).openOrThrowException(attemptedToOpenAnEmptyBox)


    if (AuthUser.find(By(AuthUser.username, USERNAME_DISABLED)).isEmpty)
      AuthUser.create.
        email(EMAIL_DISABLED).
        username(USERNAME_DISABLED).
        password(PASSWORD_DISABLED).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe

    if (Consumers.consumers.vend.getConsumerByConsumerKey(KEY_DISABLED).isEmpty)
      Consumers.consumers.vend.createConsumer(Some(KEY_DISABLED), Some(SECRET_DISABLED), Some(false), Some("test application disabled"), None, Some("description"), Some("eveline@example.com"), None, None).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")

  val invalidUsernamePasswordHeader = ("Authorization", ("DirectLogin username=\"notExistingUser\", " +
    "password=%s, consumer_key=%s").format(NO_EXISTING_PW, KEY))

  val invalidUsernamePasswordCharaterHeader = ("Authorization", ("DirectLogin username=\" a#s \", " +
    "password=%s, consumer_key=%s").format(NO_EXISTING_PW, KEY))

  val validUsernameInvalidPasswordHeader = ("Authorization", ("DirectLogin username=%s," +
    "password=%s, consumer_key=%s").format(USERNAME, NO_EXISTING_PW, KEY))

  val invalidConsumerKeyHeader = ("Authorization", ("DirectLogin username=%s, " +
    "password=%s, consumer_key=%s").format(USERNAME, VALID_PW, "invalid"))

  val validDeprecatedHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(USERNAME, VALID_PW, KEY))

  val validHeader = ("DirectLogin", "username=%s, password=%s, consumer_key=%s".
    format(USERNAME, VALID_PW, KEY))

  val disabledConsumerValidHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(USERNAME_DISABLED, PASSWORD_DISABLED, KEY_DISABLED))

  val invalidUsernamePasswordCharaterHeaders = List(accessControlOriginHeader, invalidUsernamePasswordCharaterHeader)

  val invalidUsernamePasswordHeaders = List(accessControlOriginHeader, invalidUsernamePasswordHeader)

  val validUsernameInvalidPasswordHeaders = List(accessControlOriginHeader, validUsernameInvalidPasswordHeader)

  val invalidConsumerKeyHeaders = List(accessControlOriginHeader, invalidConsumerKeyHeader)

  val validHeaders = List(accessControlOriginHeader, validHeader, ("Authorization", "Basic 123456"))
  val validDeprecatedHeaders = List(accessControlOriginHeader, validDeprecatedHeader)

  val disabledConsumerKeyHeaders = List(accessControlOriginHeader, disabledConsumerValidHeader)

  def directLoginRequest = baseRequest / "my" / "logins" / "direct"

  feature("DirectLogin") {
    scenario("Invalid auth header") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //val consumers =  OBPConsumer.findAll()
      //assert(registeredApplication(KEY) == true)

      When("we try to login without an Authorization header")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", List(accessControlOriginHeader))

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.MissingDirectLoginHeader)
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
      LoginAttempt.resetBadLoginAttempts(localIdentityProvider, USERNAME)
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

    scenario("Missing DirectLogin header") {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("we try to login with a missing DirectLogin header")
      val request = directLoginRequest
      val response = makePostRequest(request,"")

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.MissingDirectLoginHeader)
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

    scenario("Login with correct everything! - Deprecated Header", ApiEndpoint1, ApiEndpoint2) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validDeprecatedHeaders)
      var token = "INVALID"
      Then("We should get a 201 - OK and a token")
      response.code should equal(201)
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

      When("when we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = baseRequest / "obp" / "v3.0.0" / "users" / "current"
      val responseCurrentUserNewStyle = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserNewStyle.code should equal(200)
      val currentUserNewStyle = responseCurrentUserNewStyle.body.extract[UserJsonV300]
      currentUserNewStyle.username shouldBe USERNAME
      
      When("when we use the token to get current user and it should work - Old Style")
      val requestCurrentUserOldStyle = baseRequest / "obp" / "v2.0.0" / "users" / "current"
      val responseCurrentUserOldStyle = makeGetRequest(requestCurrentUserOldStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserOldStyle.code should equal(200)
      val currentUserOldStyle = responseCurrentUserOldStyle.body.extract[UserJsonV300]
      currentUserOldStyle.username shouldBe USERNAME

      currentUserNewStyle.username shouldBe currentUserOldStyle.username
    }
    
    scenario("Login with correct everything!", ApiEndpoint1, ApiEndpoint2) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validHeaders)
      var token = "INVALID"
      Then("We should get a 201 - OK and a token")
      response.code should equal(201)
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

      When("when we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = baseRequest / "obp" / "v3.0.0" / "users" / "current"
      val responseCurrentUserNewStyle = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserNewStyle.code should equal(200)
      val currentUserNewStyle = responseCurrentUserNewStyle.body.extract[UserJsonV300]
      currentUserNewStyle.username shouldBe USERNAME
      
      When("when we use the token to get current user and it should work - Old Style")
      val requestCurrentUserOldStyle = baseRequest / "obp" / "v2.0.0" / "users" / "current"
      val responseCurrentUserOldStyle = makeGetRequest(requestCurrentUserOldStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserOldStyle.code should equal(200)
      val currentUserOldStyle = responseCurrentUserOldStyle.body.extract[UserJsonV300]
      currentUserOldStyle.username shouldBe USERNAME

      currentUserNewStyle.username shouldBe currentUserOldStyle.username
    } 
    
    scenario("Login with correct everything and use props local_identity_provider", ApiEndpoint1, ApiEndpoint2) {

      setPropsValues("local_identity_provider"-> Constant.HostName)

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validHeaders)
      var token = "INVALID"
      Then("We should get a 201 - OK and a token")
      response.code should equal(201)
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

      When("when we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = baseRequest / "obp" / "v3.0.0" / "users" / "current"
      val responseCurrentUserNewStyle = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserNewStyle.code should equal(200)
      val currentUserNewStyle = responseCurrentUserNewStyle.body.extract[UserJsonV300]
      currentUserNewStyle.username shouldBe USERNAME
      
      When("when we use the token to get current user and it should work - Old Style")
      val requestCurrentUserOldStyle = baseRequest / "obp" / "v2.0.0" / "users" / "current"
      val responseCurrentUserOldStyle = makeGetRequest(requestCurrentUserOldStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserOldStyle.code should equal(200)
      val currentUserOldStyle = responseCurrentUserOldStyle.body.extract[UserJsonV300]
      currentUserOldStyle.username shouldBe USERNAME

      currentUserNewStyle.username shouldBe currentUserOldStyle.username
    }

    scenario("Login with correct everything but the user is locked", ApiEndpoint1, ApiEndpoint2) {
      lazy val username = "firstname.lastname"
      lazy val header = ("DirectLogin", "username=%s, password=%s, consumer_key=%s".
        format(username, VALID_PW, KEY))
      
      // Delete the user
      AuthUser.findAll(By(AuthUser.username, username)).map(_.delete_!())
      // Create the user
      AuthUser.create.
        email(EMAIL).
        username(username).
        password(VALID_PW).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe

      When("the header and credentials are good")
      lazy val response = makePostRequestAdditionalHeader(directLoginRequest, "", List(accessControlOriginHeader, header))
      var token = ""
      Then("We should get a 201 - OK and a token")
      response.code should equal(201)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
          token = value
        case _ => fail("Expected a token")
      }
      
      When("when we use the token it should work")
      lazy val headerWithToken = ("DirectLogin", "token=%s".format(token))
      lazy val validHeadersWithToken = List(accessControlOriginHeader, headerWithToken)

      // Lock the user in order to test functionality
      UserLocksProvider.lockUser(localIdentityProvider, username)

      When("when we use the token to get current user and it should NOT work due to locked user - New Style")
      lazy val requestCurrentUserNewStyle = baseRequest / "obp" / "v3.0.0" / "users" / "current"
      lazy val responseCurrentUserNewStyle = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 401")
      responseCurrentUserNewStyle.code should equal(401)
      responseCurrentUserNewStyle.body.extract[ErrorMessage].message should include(ErrorMessages.UsernameHasBeenLocked)

      When("when we use the token to get current user and it should NOT work due to locked user - Old Style")
      lazy val requestCurrentUserOldStyle = baseRequest / "obp" / "v2.0.0" / "users" / "current"
      lazy val responseCurrentUserOldStyle = makeGetRequest(requestCurrentUserOldStyle, validHeadersWithToken)
      And("We should get a 400")
      responseCurrentUserOldStyle.code should equal(400)
      responseCurrentUserOldStyle.body.extract[ErrorMessage].message should include(ErrorMessages.UsernameHasBeenLocked)
    }



    scenario("Test the last issued token is valid as well as a previous one", ApiEndpoint2) {

      When("The header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validHeaders)
      var token = ""
      Then("We should get a 201 - OK and a token")
      response.code should equal(201)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
          token = value
        case _ => fail("Expected a token")
      }

      val headerWithToken = ("DirectLogin", "token=%s".format(token))
      val validHeadersWithToken = List(accessControlOriginHeader, headerWithToken)
      When("When we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = baseRequest / "obp" / "v3.0.0" / "users" / "current"
      val responseCurrentUserNewStyle = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserNewStyle.code should equal(200)
      val currentUserNewStyle = responseCurrentUserNewStyle.body.extract[UserJsonV300]
      currentUserNewStyle.username shouldBe USERNAME

      When("When we issue a new token")
      makePostRequestAdditionalHeader(request, "", validHeaders)
      Then("The previous one should be valid")
      val secondResponse = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      secondResponse.code should equal(200)
      // assertResponse(failedResponse, DirectLoginInvalidToken)


    }


    scenario("Test DirectLogin header value is case insensitive", ApiEndpoint2) {

      When("The header and credentials are good")
      val request = directLoginRequest
      val response = makePostRequestAdditionalHeader(request, "", validHeaders)
      var token = ""
      Then("We should get a 201 - OK and a token")
      response.code should equal(201)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
          token = value
        case _ => fail("Expected a token")
      }

      val headerWithToken = ("dIreCtLoGin", "token=%s".format(token))
      val validHeadersWithToken = List(accessControlOriginHeader, headerWithToken)
      When("When we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = baseRequest / "obp" / "v3.0.0" / "users" / "current"
      val responseCurrentUserNewStyle = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      responseCurrentUserNewStyle.code should equal(200)
      val currentUserNewStyle = responseCurrentUserNewStyle.body.extract[UserJsonV300]
      currentUserNewStyle.username shouldBe USERNAME

      When("When we issue a new token")
      makePostRequestAdditionalHeader(request, "", validHeaders)
      Then("The previous one should be valid")
      val secondResponse = makeGetRequest(requestCurrentUserNewStyle, validHeadersWithToken)
      And("We should get a 200")
      secondResponse.code should equal(200)
      // assertResponse(failedResponse, DirectLoginInvalidToken)


    }


  }

  private def assertResponse(response: APIResponse, expectedErrorMessage: String): Unit = {
    response.body.extract[ErrorMessage].message should startWith(expectedErrorMessage)
  }
  
}