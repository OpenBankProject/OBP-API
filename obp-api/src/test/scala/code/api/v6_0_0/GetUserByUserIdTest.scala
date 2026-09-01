package code.api.v6_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAnyUser
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class GetUserByUserIdTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.getUserByUserId))

  feature(s"Get User by USER_ID - GET /obp/v6.0.0/users/user-id/USER_ID - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "users" / "user-id" / resourceUser1.userId).GET
      val response = makeGetRequest(request)

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate authentication is required")
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request as an authenticated user without the required role")
      val request = (v6_0_0_Request / "users" / "user-id" / resourceUser1.userId).GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 403")
      response.code should equal(403)
      And("The error message should indicate missing role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetAnyUser)
    }

    scenario("Authenticated user with CanGetAnyUser role should succeed", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)

      When("We make the request with the required role")
      val request = (v6_0_0_Request / "users" / "user-id" / resourceUser1.userId).GET <@ (user1)
      val response = try {
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should contain user details")
      (response.body \ "user_id").extract[String] should equal(resourceUser1.userId)
      And("The response should include first_name and last_name fields")
      response.body \ "first_name" should not equal org.json4s.JNothing
      response.body \ "last_name" should not equal org.json4s.JNothing
    }

  }
}
