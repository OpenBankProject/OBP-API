package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole.CanCreateStandingOrderAtOneBank
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.{NoViewPermission, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class StandingOrderTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createStandingOrder))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.createStandingOrderManagement))

  lazy val postStandingOrderJsonV400 = SwaggerDefinitionsJSON.postStandingOrderJsonV400
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val view = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "standing-order").POST
      val response400 = makePostRequest(request400, write(postStandingOrderJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "standing-order").POST <@(user1)
      val response400 = makePostRequest(request400, write(postStandingOrderJsonV400))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should startWith(NoViewPermission)
    }
  }
  

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / bankId / "accounts" / bankAccount.id / "standing-order").POST
      val response400 = makePostRequest(request400, "")
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / bankId / "accounts" / bankAccount.id / "standing-order").POST <@(user1)
      val response400 = makePostRequest(request400, write(postStandingOrderJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateStandingOrderAtOneBank)
    }
  }
  
}
