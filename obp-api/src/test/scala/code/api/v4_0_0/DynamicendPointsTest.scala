package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.ExampleValue
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class DynamicEndpointsTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createDynamicEndpoint))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getDynamicEndpoints))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getDynamicEndpoint))
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints").POST
      val response400 = makePostRequest(request400, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.toString contains("dynamic_endpoint_id") should be (true)
      responseWithRole.body.toString contains("swagger_string") should be (true)
      responseWithRole.body.toString contains("Portus EVS sandbox demo API") should be (true)
      responseWithRole.body.toString contains("content user-friendly error message") should be (true)
      responseWithRole.body.toString contains("create user successful and return created user object") should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").GET<@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetDynamicEndpoints.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEndpoint.toString)

      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)

      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints").GET<@ (user1)
      val response400 = makeGetRequest(request400)
      response400.code should be (200)
      response400.body.toString contains("Portus EVS sandbox demo API") should be (true)
      response400.body.toString contains("content user-friendly error message") should be (true)
      response400.body.toString contains("create user successful and return created user object") should be (true)
      
    }
  }  
  
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints"/ "some-id").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints" /"some-id").GET<@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val postDynamicEndpointRequestBodyExample = ExampleValue.dynamicEndpointRequestBodyExample

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST<@ (user1)
      val response = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetDynamicEndpoint.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEndpoint.toString)
      
      val responseWithRole = makePostRequest(request, write(postDynamicEndpointRequestBodyExample))
      Then("We should get a 201")
      responseWithRole.code should equal(201)

      val id = responseWithRole.body.\\("dynamic_endpoint_id").values.get("dynamic_endpoint_id").head.toString

      val request400 = (v4_0_0_Request / "management" / "dynamic-endpoints" /id).GET<@ (user1)
      val response400 = makeGetRequest(request400)
      response400.code should be (200)
      response400.body.toString contains("dynamic_endpoint_id") should be (true)
      response400.body.toString contains("swagger_string") should be (true)
      response400.body.toString contains("Portus EVS sandbox demo API") should be (true)
      response400.body.toString contains("content user-friendly error message") should be (true)
      response400.body.toString contains("create user successful and return created user object") should be (true)
      
    }
  }  
}
