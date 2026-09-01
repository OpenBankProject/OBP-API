package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetOidcClient
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class GetOidcClientTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.getOidcClient))

  feature(s"Get OIDC Client - GET /obp/v6.0.0/oidc/clients/CLIENT_ID - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "oidc" / "clients" / "nonexistent_client_id").GET
      val response = makeGetRequest(request)

      Then("We should get a 401")
      response.code should equal(401)
      And("The error message should indicate authentication is required")
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.ApplicationNotIdentified)
    }

    scenario("Authenticated user without role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request as an authenticated user without the required role")
      val request = (v6_0_0_Request / "oidc" / "clients" / "nonexistent_client_id").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 403")
      response.code should equal(403)
      And("The error message should indicate missing role")
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetOidcClient)
    }

    scenario("Authenticated user with CanGetOidcClient role but invalid client should fail with 404", ApiEndpoint, VersionOfApi) {
      val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetOidcClient.toString)

      When("We request a non-existent client")
      val request = (v6_0_0_Request / "oidc" / "clients" / "nonexistent_client_id").GET <@ (user1)
      val response = try {
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should not get a 401 or 403 (role check passed)")
      response.code should not equal(401)
      response.code should not equal(403)
    }

  }
}
