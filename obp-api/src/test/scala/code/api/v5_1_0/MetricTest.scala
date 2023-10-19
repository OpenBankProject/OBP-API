package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanReadAggregateMetrics}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_0_0.AggregateMetricJSON
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class MetricTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.getAggregateMetrics))
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "management" / "aggregate-metrics").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanReadAggregateMetrics)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanReadAggregateMetrics)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadAggregateMetrics.toString)
      val requestRoot = (v5_1_0_Request / "users"  / "current" ).GET <@ (user1)
      makeGetRequest(requestRoot)
      makeGetRequest(requestRoot)
      makeGetRequest(requestRoot)
      val requestBanks = (v5_1_0_Request / "banks" ).GET <@ (user1)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)
      makeGetRequest(requestBanks)

      {
        val requestRoot = (v5_1_0_Request / "users"  / "current").GET <@ (user2)
        makeGetRequest(requestRoot)
        makeGetRequest(requestRoot)
        makeGetRequest(requestRoot)
        val requestBanks = (v5_1_0_Request / "banks").GET <@ (user2)
        makeGetRequest(requestBanks)
        makeGetRequest(requestBanks)
        makeGetRequest(requestBanks)
        makeGetRequest(requestBanks)
      }

      {
        val requestRoot = (v5_1_0_Request / "users"  / "current").GET <@ (user3)
        makeGetRequest(requestRoot)
        makeGetRequest(requestRoot)
        makeGetRequest(requestRoot)
        val requestBanks = (v5_1_0_Request / "banks").GET <@ (user3)
        makeGetRequest(requestBanks)
        makeGetRequest(requestBanks)
        makeGetRequest(requestBanks)
        makeGetRequest(requestBanks)
      }

      When("We make a request v5.1.0")
      val request = (v5_1_0_Request / "management" / "aggregate-metrics").GET<@(user1) <<? List(("include_app_names", testConsumer.name.get))
      val response = makeGetRequest(req = request)
      Then("We get successful response")
      response.code should equal(200)
      val aggregateMetricJSON = response.body.extract[AggregateMetricJSON]
      aggregateMetricJSON.count shouldBe(7)

      When("We make a request v5.1.0")
      val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET<@(user1) <<? List(("include_app_names", s"${testConsumer.name.get},${testConsumer2.name.get}"))
      val response2 = makeGetRequest(request2)
      Then("We get successful response")
      response2.code should equal(200)
      request2.toRequest
      val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
      aggregateMetricJSON2.count shouldBe (15)

      {
        When("We make a request v5.1.0")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("include_app_names", s"${testConsumer.name.get},${testConsumer2.name.get},${testConsumer3.name.get}"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (23)
      }

      {
        Then("we test the include_implemented_by_partial_functions params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("include_implemented_by_partial_functions", "getBanks"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (12)
      }

      {
        Then("we test the include_implemented_by_partial_functions params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("include_implemented_by_partial_functions", "getBanks,getCurrentUser"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (21)
      }

      {
        Then("we test the include_url_patterns params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("include_url_patterns", "%users%"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (9)
      }

      {
        Then("we test the include_url_patterns params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("include_url_patterns", "%users%,%current%"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (9)
      }

      {
        Then("we test the from_date params, we set it 2222, so the result will be always 0")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("from_date", "2222-01-10T01:20:02.900Z"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (0)
      }

      {
        Then("we test the from_date params, we set it 0000, so the result will be always 0")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("to_date", "0000-01-10T01:20:02.900Z"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (0)
      }
      
      {
        Then("we test the consumer_id params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("consumer_id", s"${testConsumer3.id.get}"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe(7)
      }

      {
        Then("we test the consumer_id params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("consumer_id", s"${testConsumer2.id.get}"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (7)
      }
      
      {
        Then("we test the user_id params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("user_id", s"${resourceUser3.userId}"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (7)
      }
      
      {
        Then("we test the user_id params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("user_id", s"{${resourceUser2.userId}}"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (0)
      }
      
      {
        Then("we test the anon params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("anon", s"true"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (0)
      }
      
      {
        Then("we test the anon params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("anon", s"false"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count > 25 shouldBe (true)
      }
      
      {
        Then("we test the url params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("url", "/obp/v5.1.0/banks"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (12)
      }
      
      {
        Then("we test the app_name params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("app_name", s"${testConsumer2.name.get}"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (7)
      }
      {
        Then("we test the app_name params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("app_name", "test5"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (0)
      }
      {
        Then("we test the implemented_by_partial_function params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("implemented_by_partial_function", "getBanks"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count shouldBe (12)
      }
      {
        Then("we test the implemented_in_version params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("implemented_in_version", "v5.1.0"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count > (21) should be (true)
      }
      {
        Then("we test the implemented_in_version params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("implemented_in_version", "v5.0.0"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count should be (0)
      }
      {
        Then("we test the verb params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("verb", "GET"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count > (21) should be (true)
      }
      {
        Then("we test the verb params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("verb", "POST"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count should be (0)
      }
      {
        Then("we test the verb params")
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? List(("correlation_id", "123"))
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count should be (0)
      }


      {
        Then("we test all params")
        val params = List(
          ("consumer_id", s"${testConsumer.id.get}"),
          ("user_id", s"${resourceUser1.userId}"),
          ("anon", "false"),
          ("url", "/obp/v5.1.0/banks"),
          ("app_name", s"${testConsumer.name.get}"),
          ("implemented_by_partial_function", "getBanks"),
          ("implemented_in_version", "v5.1.0"),
          ("verb", "GET"),
          ("include_implemented_by_partial_functions", "getBanks,getCurrentUser"),
          ("include_app_names", s"${testConsumer.name.get},${testConsumer2.name.get},${testConsumer3.name.get}"),
          ("include_url_patterns", "%banks%"),
        )
        val request2 = (v5_1_0_Request / "management" / "aggregate-metrics").GET <@ (user1) <<? params
        val response2 = makeGetRequest(request2)
        Then("We get successful response")
        response2.code should equal(200)
        request2.toRequest
        val aggregateMetricJSON2 = response2.body.extract[AggregateMetricJSON]
        aggregateMetricJSON2.count > (0) shouldBe(true )
      }

    }
  }
  
}