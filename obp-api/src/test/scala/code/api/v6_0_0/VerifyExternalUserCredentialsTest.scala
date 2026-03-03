package code.api.v6_0_0

import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.{CallContext, ErrorMessages}
import code.api.util.ApiRole.CanVerifyUserCredentials
import code.api.v6_0_0.APIMethods600.Implementations6_0_0
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.setup.DefaultUsers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{ErrorMessage, InboundExternalUser}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

/**
 * Test suite for verifying external user credentials via a mocked connector.
 *
 * This is a separate test class because it swaps the global Connector to mock
 * checkExternalUserCredentials — which would interfere with local-auth tests
 * in VerifyUserCredentialsTest.
 */
class VerifyExternalUserCredentialsTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.verifyUserCredentials))

  val externalProvider = "mock_external_provider"
  val externalUsername = "ext_user_" + randomString(8).toLowerCase
  val externalPassword = "ExternalPassword123!"
  val externalEmail = externalUsername + "@external.example.com"

  // Mock connector that only overrides checkExternalUserCredentials.
  // Accepts one known username+password pair; rejects everything else.
  object MockExternalAuthConnector extends Connector with MdcLoggable {
    implicit override val nameOfConnector = "MockExternalAuthConnector"

    override def checkExternalUserCredentials(
      username: String,
      password: String,
      callContext: Option[CallContext]
    ): Box[InboundExternalUser] = {
      if (username == externalUsername && password == externalPassword) {
        Full(InboundExternalUser(
          aud = "",
          exp = "",
          iat = "",
          iss = externalProvider,
          sub = externalUsername,
          azp = None,
          email = Some(externalEmail),
          emailVerified = Some("true"),
          name = Some("External Test User")
        ))
      } else {
        Empty
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    Connector.connector.default.set(MockExternalAuthConnector)
  }

  override def afterAll(): Unit = {
    Connector.connector.default.set(Connector.buildOne)
    LoginAttempt.resetBadLoginAttempts(externalProvider, externalUsername)
    super.afterAll()
  }

  feature(s"Verify External User Credentials - POST /obp/v6.0.0/users/verify-credentials - $VersionOfApi") {

    scenario("Successfully verify external user credentials via connector", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify valid external credentials")
      val postJson = Map(
        "username" -> externalUsername,
        "password" -> externalPassword,
        "provider" -> externalProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should contain the external user details")
      val json = response.body
      (json \ "username").extract[String] should equal(externalUsername)
      (json \ "provider").extract[String] should equal(externalProvider)
    }

    scenario("Fail to verify external user with wrong password", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      When("We verify external credentials with wrong password")
      val postJson = Map(
        "username" -> externalUsername,
        "password" -> "WrongPassword!",
        "provider" -> externalProvider
      )
      val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        LoginAttempt.resetBadLoginAttempts(externalProvider, externalUsername)
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should include("OBP-20004")
    }

    scenario("External auth failure should not affect local user with same username", ApiEndpoint, VersionOfApi) {
      // Create a local user with the same username as the external user
      val localPassword = "LocalPassword123!"
      val localUser = AuthUser.create
        .email(externalUsername + "@local.example.com")
        .username(externalUsername)
        .password(localPassword)
        .validated(true)
        .firstName("Local")
        .lastName("User")
        .provider(Constant.localIdentityProvider)
        .saveMe()

      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanVerifyUserCredentials.toString)

      try {
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, externalUsername)
        LoginAttempt.resetBadLoginAttempts(externalProvider, externalUsername)

        val localAttemptsBefore = LoginAttempt.getOrCreateBadLoginStatus(
          Constant.localIdentityProvider, externalUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)

        When("We fail to verify external credentials with wrong password")
        val postJson = Map(
          "username" -> externalUsername,
          "password" -> "WrongPassword!",
          "provider" -> externalProvider
        )
        val request = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val response = makePostRequest(request, write(postJson))

        Then("We should get a 401")
        response.code should equal(401)

        And("The local user's bad login attempts should NOT have increased")
        val localAttemptsAfter = LoginAttempt.getOrCreateBadLoginStatus(
          Constant.localIdentityProvider, externalUsername
        ).map(_.badAttemptsSinceLastSuccessOrReset).openOr(0)
        localAttemptsAfter should equal(localAttemptsBefore)

        And("The local user should still authenticate successfully")
        val localPostJson = Map(
          "username" -> externalUsername,
          "password" -> localPassword,
          "provider" -> Constant.localIdentityProvider
        )
        val localRequest = (v6_0_0_Request / "users" / "verify-credentials").POST <@ (user1)
        val localResponse = makePostRequest(localRequest, write(localPostJson))
        localResponse.code should equal(200)
      } finally {
        LoginAttempt.resetBadLoginAttempts(Constant.localIdentityProvider, externalUsername)
        LoginAttempt.resetBadLoginAttempts(externalProvider, externalUsername)
        localUser.delete_!
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }
  }
}
