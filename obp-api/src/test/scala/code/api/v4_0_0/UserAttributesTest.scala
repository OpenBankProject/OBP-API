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
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.updateCurrentUserAttribute))


  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val batteryLevel = "BATTERY_LEVEL"
  lazy val postUserAttributeJsonV400 = SwaggerDefinitionsJSON.userAttributeJsonV400.copy(name = batteryLevel)
  lazy val putUserAttributeJsonV400 = SwaggerDefinitionsJSON.userAttributeJsonV400.copy(name = "ROLE_2")

  

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
      jsonResponse.name should be (batteryLevel)
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "user" / "attributes").GET
      val response400 = makePostRequest(request400, write(postUserAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
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
      jsonResponse.user_attributes.exists(_.name == batteryLevel) should be (true)
    }
  }



  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "my" / "user" / "attributes" / "USER_ATTRIBUTE_ID").PUT
      val response400 = makePutRequest(request400, write(putUserAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
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
      jsonResponse.user_attributes.exists(_.name == batteryLevel) should be (true)

      When("We make a request v4.0.0")
      val userAttributeId = response400.body.extract[UserAttributeResponseJsonV400].user_attribute_id
      val putRequest400 = (v4_0_0_Request / "my" / "user" / "attributes" / userAttributeId).PUT <@ (user1)
      val putResponse400 = makePutRequest(putRequest400, write(putUserAttributeJsonV400))
      Then("We should get a 200")
      putResponse400.code should equal(200)
      putResponse400.body.extract[UserAttributeResponseJsonV400].name equals  ("ROLE_2")
    }
  }
  
}
