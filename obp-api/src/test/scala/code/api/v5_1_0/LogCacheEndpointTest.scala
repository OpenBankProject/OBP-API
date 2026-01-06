package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetSystemLogCacheAll,CanGetSystemLogCacheInfo}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST._
import org.scalatest.Tag

class LogCacheEndpointTest extends V510ServerSetup {
  
  /**
   * Test tags
   * Example: To run tests with tag "logCacheEndpoint":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.logCacheInfoEndpoint))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Missing entitlement") {
    scenario("We will call the endpoint with user credentials but without proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanGetSystemLogCacheAll)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message contains (UserHasMissingRoles) shouldBe (true)
      response.body.extract[ErrorMessage].message contains CanGetSystemLogCacheInfo.toString() shouldBe (true)
      response.body.extract[ErrorMessage].message contains CanGetSystemLogCacheAll.toString() shouldBe (true)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access without pagination") {
    scenario("We get log cache without pagination parameters", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request to get log cache")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1)
      val response = makeGetRequest(request)
      
      Then("We should get a successful response")
      response.code should equal(200)
      val json = response.body.extract[JObject]
      
      And("The response should contain log entries")
      (json \ "entries") should not be JNothing
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with limit parameter") {
    scenario("We get log cache with limit parameter only", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with limit parameter")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("limit", "5"))
      val response = makeGetRequest(request)
      
      Then("We should get a successful response")
      response.code should equal(200)
      val json = response.body.extract[JObject]
      
      And("The response should contain limited log entries")
      val entries = (json \ "entries").extract[JArray]
      entries.values.asInstanceOf[List[_]].size should be <= 5
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with offset parameter") {
    scenario("We get log cache with offset parameter only", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with offset parameter")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("offset", "2"))
      val response = makeGetRequest(request)
      
      Then("We should get a successful response")
      response.code should equal(200)
      val json = response.body.extract[JObject]
      
      And("The response should contain log entries starting from offset")
      (json \ "entries") should not be JNothing
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with both parameters") {
    scenario("We get log cache with both limit and offset parameters", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with both limit and offset parameters")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("limit", "3"), ("offset", "1"))
      val response = makeGetRequest(request)
      
      Then("We should get a successful response")
      response.code should equal(200)
      val json = response.body.extract[JObject]
      
      And("The response should contain paginated log entries")
      val entries = (json \ "entries").extract[JArray]
      entries.values.asInstanceOf[List[_]].size should be <= 3
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Edge cases") {
    scenario("We get error with zero limit (invalid parameter)", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with zero limit")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("limit", "0"))
      val response = makeGetRequest(request)
      
      Then("We should get a not found response since endpoint does not exist")
      response.code should equal(400)
      val json = response.body.extract[JObject]
      
      And("The response should contain the correct error message")
      val message = (json \ "message").extract[String]
      message should include("wrong value for obp_limit parameter")
    }

    scenario("We get log cache with large offset", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with very large offset")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("offset", "10000"))
      val response = makeGetRequest(request)
      
      Then("We should get a successful response")
      response.code should equal(200)
      val json = response.body.extract[JObject]
      
      And("The response should not fail")
      val entries = (json \ "entries").extract[JArray]
      entries.values.asInstanceOf[List[_]].size should be >= 0
    }

    scenario("We get log cache with minimum valid limit", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with minimum valid limit (1)")
      val request = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("limit", "1"))
      val response = makeGetRequest(request)
      
      Then("We should get a successful response")
      response.code should equal(200)
      val json = response.body.extract[JObject]
      
      And("The response should contain at most 1 entry")
      val entries = (json \ "entries").extract[JArray]
      entries.values.asInstanceOf[List[_]].size should be <= 1
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Different log levels") {
    scenario("We test different log levels with pagination", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make requests to different log levels with pagination")
      val logLevels = List("debug", "info", "warning", "error", "all")
      
      logLevels.foreach { logLevel =>
        val request = (v5_1_0_Request / "system" / "log-cache" / logLevel).GET <@(user1) <<? List(("limit", "2"), ("offset", "0"))
        val response = makeGetRequest(request)
        
        Then(s"We should get successful response for log level $logLevel")
        response.code should equal(200)
        val json = response.body.extract[JObject]
        
        And("The response should have the correct structure")
        (json \ "entries") should not be JNothing
      }
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Invalid log level") {
    scenario("We get error for invalid log level", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We make a request with invalid log level")
      val request = (v5_1_0_Request / "system" / "log-cache" / "invalid_level").GET <@(user1)
      val response = makeGetRequest(request)
      
      Then("We should get a not found response since endpoint does not exist")
      response.code should equal(404)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Invalid parameters") {
    scenario("We test invalid pagination parameters", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with proper entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemLogCacheAll.toString)
      
      When("We test with non-numeric limit parameter")
      val requestInvalidLimit = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("limit", "abc"))
      val responseInvalidLimit = makeGetRequest(requestInvalidLimit)
      
      Then("We should get a not found response since endpoint does not exist")
      responseInvalidLimit.code should equal(400)
      
      When("We test with non-numeric offset parameter")
      val requestInvalidOffset = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("offset", "xyz"))
      val responseInvalidOffset = makeGetRequest(requestInvalidOffset)
      
      Then("We should get a not found response since endpoint does not exist")
      responseInvalidOffset.code should equal(400)

      When("We test with negative limit parameter")
      val requestNegativeLimit = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("limit", "-1"))
      val responseNegativeLimit = makeGetRequest(requestNegativeLimit)
      
      Then("We should get a not found response since endpoint does not exist")
      responseNegativeLimit.code should equal(400)

      When("We test with negative offset parameter")
      val requestNegativeOffset = (v5_1_0_Request / "system" / "log-cache" / "info").GET <@(user1) <<? List(("offset", "-1"))
      val responseNegativeOffset = makeGetRequest(requestNegativeOffset)
      
      Then("We should get a not found response since endpoint does not exist")
      responseNegativeOffset.code should equal(400)
    }
  }
}