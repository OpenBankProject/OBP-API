/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v6_0_0

import code.api.Constant.localIdentityProvider
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages._
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.api.v3_0_0.UserJsonV300
import code.consumer.Consumers
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.setup.{APIResponse, TestPasswordConfig}
import code.userlocks.UserLocksProvider
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import org.scalatest.{BeforeAndAfter, Tag}

class DirectLoginV600Test extends V600ServerSetup with BeforeAndAfter {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.directLoginEndpoint))

  val KEY = randomString(40).toLowerCase
  val SECRET = randomString(40).toLowerCase
  val EMAIL = randomString(10).toLowerCase + "@example.com"
  val USERNAME = "username with spaces"
  // Test passwords - using TestPasswordConfig utility to avoid SonarCloud hard-coded credential warnings
  val NO_EXISTING_PW = TestPasswordConfig.INVALID_PASSWORD
  val VALID_PW = TestPasswordConfig.VALID_PASSWORD

  val KEY_DISABLED = randomString(40).toLowerCase
  val SECRET_DISABLED = randomString(40).toLowerCase
  val EMAIL_DISABLED = randomString(10).toLowerCase + "@example.com"
  val USERNAME_DISABLED = randomString(10).toLowerCase
  val PASSWORD_DISABLED = randomString(20)

  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")

  val invalidUsernamePasswordHeader = ("DirectLogin", "username=invaliduser, password=invalid, consumer_key=" + KEY)
  val invalidUsernamePasswordCharaterHeader = ("DirectLogin", "username=ABC-DEF#, password=ksksk, consumer_key=" + KEY)
  val validUsernameInvalidPasswordHeader = ("DirectLogin", "username=%s, password=invalid, consumer_key=%s".format(USERNAME, KEY))
  val invalidConsumerKeyHeader = ("DirectLogin", "username=%s, password=%s, consumer_key=invalidkey".format(USERNAME, VALID_PW))
  val validDeprecatedHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".format(USERNAME, VALID_PW, KEY))
  val validHeader = ("DirectLogin", "username=%s, password=%s, consumer_key=%s".format(USERNAME, VALID_PW, KEY))
  val disabledConsumerValidHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(USERNAME_DISABLED, PASSWORD_DISABLED, KEY_DISABLED))

  val invalidUsernamePasswordCharaterHeaders = List(accessControlOriginHeader, invalidUsernamePasswordCharaterHeader)
  val invalidUsernamePasswordHeaders = List(accessControlOriginHeader, invalidUsernamePasswordHeader)
  val validUsernameInvalidPasswordHeaders = List(accessControlOriginHeader, validUsernameInvalidPasswordHeader)
  val invalidConsumerKeyHeaders = List(accessControlOriginHeader, invalidConsumerKeyHeader)
  val validHeaders = List(accessControlOriginHeader, validHeader, ("Authorization", "Basic 123456"))
  val validDeprecatedHeaders = List(accessControlOriginHeader, validDeprecatedHeader)
  val disabledConsumerKeyHeaders = List(accessControlOriginHeader, disabledConsumerValidHeader)

  def directLoginV600Request = v6_0_0_Request / "my" / "logins" / "direct"

  before {
    if (AuthUser.find(By(AuthUser.username, USERNAME)).isEmpty)
      AuthUser.create.
        email(EMAIL).
        username(USERNAME).
        password(VALID_PW).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe()

    if (Consumers.consumers.vend.getConsumerByConsumerKey(KEY).isEmpty)
      Consumers.consumers.vend.createConsumer(
        Some(KEY), Some(SECRET), Some(true), Some("test application"), None, Some("description"), Some("eveline@example.com"), None,None,None,None,None).openOrThrowException(attemptedToOpenAnEmptyBox)


    if (AuthUser.find(By(AuthUser.username, USERNAME_DISABLED)).isEmpty)
      AuthUser.create.
        email(EMAIL_DISABLED).
        username(USERNAME_DISABLED).
        password(PASSWORD_DISABLED).
        validated(true).
        firstName(randomString(10)).
        lastName(randomString(10)).
        saveMe()

    if (Consumers.consumers.vend.getConsumerByConsumerKey(KEY_DISABLED).isEmpty)
      Consumers.consumers.vend.createConsumer(
        Some(KEY_DISABLED), Some(SECRET_DISABLED), Some(false), Some("disabled test application"), None, Some("disabled description"), Some("disabled@example.com"), None,None,None,None,None).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  feature("DirectLogin v6.0.0") {
    scenario("Invalid auth header", ApiEndpoint1, VersionOfApi) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //val consumers =  OBPConsumer.findAll()
      //assert(registeredApplication(KEY) == true)

      When("we try to login without an Authorization header")
      val request = directLoginV600Request
      val response = makePostRequestAdditionalHeader(request, "", List(accessControlOriginHeader))

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.MissingDirectLoginHeader)
    }

    scenario("Invalid credentials", ApiEndpoint1, VersionOfApi) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("we try to login with an invalid username/password")
      val request = directLoginV600Request
      val response = makePostRequestAdditionalHeader(request, "", invalidUsernamePasswordHeaders)

      Then("We should get a 401 - Unauthorized")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidLoginCredentials)
    }

    scenario("Invalid Characters", ApiEndpoint1, VersionOfApi) {
      When("we try to login with an invalid username Characters and invalid password Characters")
      val request = directLoginV600Request
      val response = makePostRequestAdditionalHeader(request, "", invalidUsernamePasswordCharaterHeaders)

      Then("We should get a 400 - Invalid Characters")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.InvalidValueCharacters)
    }

    scenario("valid Username, invalid password, login in too many times. The username will be locked", ApiEndpoint1, VersionOfApi) {
      When("login with an valid username and invalid password, failed more than 5 times.")
      val request = directLoginV600Request
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

    scenario("Consumer API key is disabled", ApiEndpoint1, VersionOfApi) {
      Given("The app we are testing is registered and disabled")
      When("We try to login with username/password")
      val request = directLoginV600Request
      val response = makePostRequestAdditionalHeader(request, "", disabledConsumerKeyHeaders)
      Then("We should get a 401")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidConsumerKey)
    }

    scenario("Missing DirectLogin header", ApiEndpoint1, VersionOfApi) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("we try to login with a missing DirectLogin header")
      val request = directLoginV600Request
      val response = makePostRequest(request,"")

      Then("We should get a 400 - Bad Request")
      response.code should equal(400)
      assertResponse(response, ErrorMessages.MissingDirectLoginHeader)
    }

    scenario("Login without consumer key", ApiEndpoint1, VersionOfApi) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the consumer key is invalid")
      val request = directLoginV600Request
      val response = makePostRequestAdditionalHeader(request, "", invalidConsumerKeyHeaders)

      Then("We should get a 401 - Unauthorized")
      response.code should equal(401)
      assertResponse(response, ErrorMessages.InvalidConsumerKey)
    }

    scenario("Login with correct everything! - Deprecated Header", ApiEndpoint1, VersionOfApi) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginV600Request
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
      val request2 = v6_0_0_Request / "my" / "accounts"
      val response2 = makeGetRequest(request2, validHeadersWithToken)

      Then("We should get a 200 - OK and an empty list of accounts")
      response2.code should equal(200)
      response2.body match {
        case JObject(List(JField(accounts,JArray(List())))) =>
        case _ => fail("Expected empty list of accounts")
      }

      When("when we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = v6_0_0_Request / "users" / "current"
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
    
    scenario("Login with correct everything!", ApiEndpoint1, VersionOfApi) {

      //setupUserAndConsumer

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginV600Request
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
      val request2 = v6_0_0_Request / "my" / "accounts"
      val response2 = makeGetRequest(request2, validHeadersWithToken)

      Then("We should get a 200 - OK and an empty list of accounts")
      response2.code should equal(200)
      response2.body match {
        case JObject(List(JField(accounts,JArray(List())))) =>
        case _ => fail("Expected empty list of accounts")
      }

      When("when we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = v6_0_0_Request / "users" / "current"
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
    
    scenario("Login with correct everything and use props local_identity_provider", ApiEndpoint1, VersionOfApi) {

      setPropsValues("local_identity_provider"-> code.api.Constant.HostName)

      Given("the app we are testing is registered and active")
      Then("We should be able to find it")
      //assert(registeredApplication(KEY) == true)

      When("the header and credentials are good")
      val request = directLoginV600Request
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
      val request2 = v6_0_0_Request / "my" / "accounts"
      val response2 = makeGetRequest(request2, validHeadersWithToken)

      Then("We should get a 200 - OK and an empty list of accounts")
      response2.code should equal(200)
      response2.body match {
        case JObject(List(JField(accounts,JArray(List())))) =>
        case _ => fail("Expected empty list of accounts")
      }

      When("when we use the token to get current user and it should work - New Style")
      val requestCurrentUserNewStyle = v6_0_0_Request / "users" / "current"
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

    scenario("Login with correct everything but the user is locked", ApiEndpoint1, VersionOfApi) {
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
        saveMe()

      When("the header and credentials are good")
      lazy val response = makePostRequestAdditionalHeader(directLoginV600Request, "", List(accessControlOriginHeader, header))
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
      lazy val requestCurrentUserNewStyle = v6_0_0_Request / "users" / "current"
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

    scenario("Test the last issued token is valid as well as a previous one", ApiEndpoint1, VersionOfApi) {

      When("The header and credentials are good")
      val request = directLoginV600Request
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
      val requestCurrentUserNewStyle = v6_0_0_Request / "users" / "current"
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

    scenario("Test DirectLogin header value is case insensitive", ApiEndpoint1, VersionOfApi) {

      When("The header and credentials are good")
      val request = directLoginV600Request
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
      val requestCurrentUserNewStyle = v6_0_0_Request / "users" / "current"
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