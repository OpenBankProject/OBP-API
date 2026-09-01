package code.api.v6_0_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postBankJson600
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateBank
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

class BankTests extends V600ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.createBank))

  feature(s"Assuring that endpoint createBank works as expected - $VersionOfApi") {

    scenario("We try to consume endpoint createBank - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val request = (v6_0_0_Request / "banks").POST
      val response = makePostRequest(request, write(postBankJson600))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("We try to consume endpoint createBank without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val request = (v6_0_0_Request / "banks").POST <@ (user1)
      val response = makePostRequest(request, write(postBankJson600))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanCreateBank entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateBank)
    }

    scenario("Successfully create a bank with a 16-character bank_id (max length)", ApiEndpoint1, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateBank.toString)

      // Generate a 16-character bank_id (maximum allowed by checkOptionalShortString validation)
      val longBankId = "bank." + randomString(11).toLowerCase // 5 + 11 = 16 characters

      When("We create a bank with a 16-character bank_id")
      val postJson = PostBankJson600(
        bank_id = longBankId,
        bank_code = "test_code",
        full_name = Some("Test Bank with Long ID"),
        logo = Some("https://example.com/logo.png"),
        website = Some("https://example.com"),
        bank_routings = None
      )
      val request = (v6_0_0_Request / "banks").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 201")
      response.code should equal(201)

      And("The response should contain the bank with the 16-character bank_id")
      val responseJson = response.body
      (responseJson \ "bank_id").extract[String] should equal(longBankId)
      (responseJson \ "bank_id").extract[String].length should equal(16)
    }

    scenario("Fail to create a bank with bank_id exceeding 16 characters", ApiEndpoint1, VersionOfApi) {
      // Add the required entitlement
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateBank.toString)

      // Generate a 17-character bank_id (exceeds maximum of 16)
      val tooLongBankId = "bank." + randomString(12).toLowerCase // 5 + 12 = 17 characters

      When("We try to create a bank with a 17-character bank_id")
      val postJson = PostBankJson600(
        bank_id = tooLongBankId,
        bank_code = "test_code",
        full_name = Some("Test Bank with Too Long ID"),
        logo = Some("https://example.com/logo.png"),
        website = Some("https://example.com"),
        bank_routings = None
      )
      val request = (v6_0_0_Request / "banks").POST <@ (user1)
      val response = try {
        makePostRequest(request, write(postJson))
      } finally {
        // Clean up entitlement
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 400")
      response.code should equal(400)

      And("The error message should indicate BANK_ID validation failed")
      response.body.extract[ErrorMessage].message should include("BANK_ID")
    }

    scenario("Return 409 when creating a bank whose bank_id already exists", ApiEndpoint1, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateBank.toString)

      val bankId = "bank." + randomString(11).toLowerCase
      val postJson = PostBankJson600(
        bank_id = bankId,
        bank_code = "test_code",
        full_name = Some("Test Bank for Duplicate"),
        logo = Some("https://example.com/logo.png"),
        website = Some("https://example.com"),
        bank_routings = None
      )
      val request = (v6_0_0_Request / "banks").POST <@ (user1)

      try {
        Given("a bank with this bank_id already exists")
        val firstResponse = makePostRequest(request, write(postJson))
        firstResponse.code should equal(201)

        When("We POST the same bank_id again")
        val secondResponse = makePostRequest(request, write(postJson))

        Then("We should get a 409")
        secondResponse.code should equal(409)

        And("The error message should be bankIdAlreadyExists")
        secondResponse.body.extract[ErrorMessage].message should equal(ErrorMessages.bankIdAlreadyExists)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }
    }
  }

}