package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanLockUser
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotFoundByUsername, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class LockUserTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.lockUser))
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "USERNAME" / "locks").POST
      val response400 = makePostRequest(request400, "")
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "USERNAME" / "locks").POST <@(user1)
      val response400 = makePostRequest(request400, "")
      Then("error should be " + UserHasMissingRoles + CanLockUser)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanLockUser)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with proper Role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      val username = "USERNAME"
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanLockUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / username / "locks").POST <@(user1)
      val response400 = makePostRequest(request400, "")
      Then("We should get a 404")
      response400.code should equal(404)
      response400.body.extract[ErrorMessage].message should be (s"$UserNotFoundByUsername($username)")
    }
  }
  
  
}
