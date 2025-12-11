package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.InvalidStrongPasswordFormat
import code.consumer.Consumers
import code.model.dataAccess.AuthUser
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

/**
 * Test suite for Create User endpoint (POST /obp/v6.0.0/users)
 * 
 * Tests cover:
 * - Successful user creation
 * - Invalid JSON format errors
 * - Missing required fields
 * - Weak password validation
 * - Duplicate username error (OBP-20258)
 * - Field validation errors
 * - Email validation behavior
 */
class CreateUserTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpointCreateUser extends Tag("createUser")

  // Test data
  val randomFirstName = randomString(8).toLowerCase
  val randomLastName = randomString(16).toLowerCase
  val randomUsername = randomString(10).toLowerCase + "@example.com"
  val randomEmail = randomString(10).toLowerCase + "@example.com"
  val strongPassword = "StrongP@ssw0rd123!" // Meets all requirements
  val weakPassword = "weak" // Does not meet requirements
  val longPassword = randomString(100) // Valid - longer than 16 chars

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Enable email validation skip for tests
    setPropsValues("authUser.skipEmailValidation" -> "true")
  }

  override def afterAll(): Unit = {
    // Clean up test users
    AuthUser.find(By(AuthUser.username, randomUsername)).map(_.delete_!)
    setPropsValues("authUser.skipEmailValidation" -> "false")
    super.afterAll()
  }

  feature(s"Create User - POST /obp/v6.0.0/users - $ApiVersion.v6_0_0") {

    scenario("Successfully create a new user with all valid fields", ApiEndpointCreateUser, VersionOfApi) {
      val uniqueUsername = randomString(15).toLowerCase + "@example.com"
      val uniqueEmail = randomString(15).toLowerCase + "@example.com"
      
      When("We create a new user with valid data")
      val createUserJson = Map(
        ("email", uniqueEmail),
        ("username", uniqueUsername),
        ("password", strongPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 201 Created response")
      response.code should equal(201)

      And("The response should contain user details")
      val json = response.body
      (json \ "user_id").extract[String] should not be empty
      (json \ "email").extract[String] should equal(uniqueEmail)
      (json \ "username").extract[String] should equal(uniqueUsername)
      (json \ "provider").extract[String] should not be empty
      
      // Clean up
      AuthUser.find(By(AuthUser.username, uniqueUsername)).map(_.delete_!)
    }

    scenario("Successfully create user with long password (>16 chars)", ApiEndpointCreateUser, VersionOfApi) {
      val uniqueUsername = randomString(15).toLowerCase + "@example.com"
      val uniqueEmail = randomString(15).toLowerCase + "@example.com"
      
      When("We create a user with a long password")
      val createUserJson = Map(
        ("email", uniqueEmail),
        ("username", uniqueUsername),
        ("password", longPassword),
        ("first_name", "Jane"),
        ("last_name", "Smith")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 201 Created response")
      response.code should equal(201)
      
      // Clean up
      AuthUser.find(By(AuthUser.username, uniqueUsername)).map(_.delete_!)
    }

    scenario("Fail to create user - duplicate username returns OBP-20258", ApiEndpointCreateUser, VersionOfApi) {
      val uniqueUsername = randomString(15).toLowerCase + "@example.com"
      val uniqueEmail = randomString(15).toLowerCase + "@example.com"
      
      Given("A user already exists with a specific username")
      val createUserJson1 = Map(
        ("email", uniqueEmail),
        ("username", uniqueUsername),
        ("password", strongPassword),
        ("first_name", "First"),
        ("last_name", "User")
      )
      val request1 = (v6_0_0_Request / "users").POST
      val response1 = makePostRequest(request1, write(createUserJson1))
      response1.code should equal(201)

      When("We try to create another user with the same username")
      val createUserJson2 = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", uniqueUsername), // Same username
        ("password", strongPassword),
        ("first_name", "Second"),
        ("last_name", "User")
      )
      val request2 = (v6_0_0_Request / "users").POST
      val response2 = makePostRequest(request2, write(createUserJson2))

      Then("We should get a 409 Conflict response")
      response2.code should equal(409)

      And("The error should be OBP-20258 (DuplicateUsername)")
      val errorMessage = response2.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-20258")
      errorMessage should include("Duplicate Username")
      errorMessage should not include("OBP-10001")
      errorMessage should not include("Incorrect json format")
      
      // Clean up
      AuthUser.find(By(AuthUser.username, uniqueUsername)).map(_.delete_!)
    }

    scenario("Fail to create user - invalid JSON format", ApiEndpointCreateUser, VersionOfApi) {
      When("We send invalid JSON")
      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, "{ invalid json }")

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be OBP-10001 (InvalidJsonFormat)")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-10001")
      errorMessage should include("Incorrect json format")
    }

    scenario("Fail to create user - missing required field (email)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user without email field")
      val createUserJson = Map(
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", strongPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
        // Missing "email"
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be OBP-10001 (InvalidJsonFormat)")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-10001")
    }

    scenario("Fail to create user - missing required field (username)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user without username field")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("password", strongPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
        // Missing "username"
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be OBP-10001 (InvalidJsonFormat)")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-10001")
    }

    scenario("Fail to create user - missing required field (password)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user without password field")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("first_name", "John"),
        ("last_name", "Doe")
        // Missing "password"
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be OBP-10001 (InvalidJsonFormat)")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-10001")
    }

    scenario("Fail to create user - missing required field (first_name)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user without first_name field")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", strongPassword),
        ("last_name", "Doe")
        // Missing "first_name"
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be OBP-10001 (InvalidJsonFormat)")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-10001")
    }

    scenario("Fail to create user - missing required field (last_name)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user without last_name field")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", strongPassword),
        ("first_name", "John")
        // Missing "last_name"
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be OBP-10001 (InvalidJsonFormat)")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-10001")
    }

    scenario("Fail to create user - weak password (too short)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with a weak password")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", weakPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be InvalidStrongPasswordFormat")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include(InvalidStrongPasswordFormat)
      errorMessage should not include("OBP-10001")
    }

    scenario("Fail to create user - password missing uppercase letter (10-16 chars)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with password missing uppercase")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", "lowercase123!"),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be InvalidStrongPasswordFormat")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include(InvalidStrongPasswordFormat)
    }

    scenario("Fail to create user - password missing special character (10-16 chars)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with password missing special character")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", "Password123"),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be InvalidStrongPasswordFormat")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include(InvalidStrongPasswordFormat)
    }

    scenario("Fail to create user - password missing digit (10-16 chars)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with password missing digit")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", "Password!@#$"),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be InvalidStrongPasswordFormat")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include(InvalidStrongPasswordFormat)
    }

    scenario("Fail to create user - password missing lowercase letter (10-16 chars)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with password missing lowercase")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", "UPPERCASE123!"),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be InvalidStrongPasswordFormat")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include(InvalidStrongPasswordFormat)
    }

    scenario("Fail to create user - empty username", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with empty username")
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", ""),
        ("password", strongPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should indicate validation failure")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-")
    }

    scenario("Fail to create user - empty email", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with empty email")
      val createUserJson = Map(
        ("email", ""),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", strongPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should indicate validation failure")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include("OBP-")
    }

    scenario("Fail to create user - password exceeds max length (>512 chars)", ApiEndpointCreateUser, VersionOfApi) {
      When("We create a user with password exceeding 512 characters")
      val tooLongPassword = randomString(520)
      val createUserJson = Map(
        ("email", randomString(15).toLowerCase + "@example.com"),
        ("username", randomString(15).toLowerCase + "@example.com"),
        ("password", tooLongPassword),
        ("first_name", "John"),
        ("last_name", "Doe")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 400 Bad Request response")
      response.code should equal(400)

      And("The error should be InvalidStrongPasswordFormat")
      val errorMessage = response.body.extract[ErrorMessageV600].message
      errorMessage should include(InvalidStrongPasswordFormat)
    }

    scenario("Successfully create user - password exactly 17 chars (no special requirements)", ApiEndpointCreateUser, VersionOfApi) {
      val uniqueUsername = randomString(15).toLowerCase + "@example.com"
      val uniqueEmail = randomString(15).toLowerCase + "@example.com"
      val password17Chars = "a" * 17 // Simple password, 17 chars
      
      When("We create a user with 17 character password")
      val createUserJson = Map(
        ("email", uniqueEmail),
        ("username", uniqueUsername),
        ("password", password17Chars),
        ("first_name", "Test"),
        ("last_name", "User")
      )

      val request = (v6_0_0_Request / "users").POST
      val response = makePostRequest(request, write(createUserJson))

      Then("We should get a 201 Created response")
      response.code should equal(201)
      
      And("The user should be created successfully")
      (response.body \ "username").extract[String] should equal(uniqueUsername)
      
      // Clean up
      AuthUser.find(By(AuthUser.username, uniqueUsername)).map(_.delete_!)
    }

    scenario("Create multiple users with different usernames", ApiEndpointCreateUser, VersionOfApi) {
      val users = List(
        (randomString(15).toLowerCase + "@example.com", "User1"),
        (randomString(15).toLowerCase + "@example.com", "User2"),
        (randomString(15).toLowerCase + "@example.com", "User3")
      )
      
      users.foreach { case (username, lastName) =>
        When(s"We create user with username $username")
        val createUserJson = Map(
          ("email", randomString(15).toLowerCase + "@example.com"),
          ("username", username),
          ("password", strongPassword),
          ("first_name", "Test"),
          ("last_name", lastName)
        )

        val request = (v6_0_0_Request / "users").POST
        val response = makePostRequest(request, write(createUserJson))

        Then("We should get a 201 Created response")
        response.code should equal(201)
        
        // Clean up
        AuthUser.find(By(AuthUser.username, username)).map(_.delete_!)
      }
    }
  }
}

case class ErrorMessageV600(message: String)