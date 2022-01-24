package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.users.UserAttribute
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class UserAttributesTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createCurrentUserAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getCurrentUserAttributes))


  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val postUserAttributeJsonV400 = SwaggerDefinitionsJSON.userAttributeJsonV400

  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "user" / "attributes").POST
      val response400 = makePostRequest(request400, write(postUserAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "user" / "attributes").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postUserAttributeJsonV400))
      Then("We should get a 201")
      response400.code should equal(201)
      val jsonResponse = response400.body.extract[UserAttributeResponseJsonV400]
      jsonResponse.name should be ("ROLE")
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "user" / "attributes").GET
      val response400 = makePostRequest(request400, write(postUserAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "user" / "attributes").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postUserAttributeJsonV400))
      Then("We should get a 201")
      response400.code should equal(201)
      When("We make a request v4.0.0")
      val getRequest400 = (v4_0_0_Request / "my" / "user" / "attributes").GET <@ (user1)
      val getResponse400 = makeGetRequest(getRequest400)
      Then("We should get a 200")
      getResponse400.code should equal(200)
      val jsonResponse = getResponse400.body.extract[UserAttributesResponseJson]
      jsonResponse.user_attributes.exists(_.name =="ROLE") should be (true)
    }
  }
  
  
}
