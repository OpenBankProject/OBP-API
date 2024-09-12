package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateUserInvitation, CanGetUserInvitation}
import code.api.util.ErrorMessages.{CannotGetUserInvitation, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class UserInvitationTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createUserInvitation))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getUserInvitationAnonymous))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getUserInvitation))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getUserInvitations))
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST <@(user1)
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("error should be " + UserHasMissingRoles + CanCreateUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanCreateUserInvitation)
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").POST <@(user1)
      val postJson = PostUserInvitationAnonymousJsonV400(secret_key = 0L)
      val response400 = makePostRequest(request400, write(postJson))
      Then("error should be " + CannotGetUserInvitation)
      response400.code should equal(404)
      response400.body.extract[ErrorMessage].message should be(CannotGetUserInvitation)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations" / "secret-link").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations" / "secret-link").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetUserInvitation)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetUserInvitation)
    }
  }
  
  
}
