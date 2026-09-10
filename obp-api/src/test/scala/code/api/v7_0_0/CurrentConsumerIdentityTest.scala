package code.api.v7_0_0

import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.ApplicationNotIdentified
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.CurrentConsumerIdentityJsonV700
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/** GET /obp/v7.0.0/consumers/current/identity: the caller's own Consumer, no Role, nothing sensitive. */
class CurrentConsumerIdentityTest extends V600ServerSetup {
  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getCurrentConsumerIdentity")

  Feature(s"test $ApiEndpoint1 version $VersionOfApi") {
    Scenario("Without any credentials the application cannot be identified", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest((v7_0_0_Request / "consumers" / "current" / "identity").GET)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ApplicationNotIdentified)
    }

    Scenario("A logged-in user gets the identity of the Consumer they called with, and nothing else", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest((v7_0_0_Request / "consumers" / "current" / "identity").GET <@ (user1))
      Then("We should get a 200")
      response.code should equal(200)
      val identity = response.body.extract[CurrentConsumerIdentityJsonV700]
      identity.consumer_id should equal(testConsumer.consumerId)
      identity.consumer_name should equal(testConsumer.name)
      And("the body carries only the two identity fields")
      response.body.asInstanceOf[org.json4s.JObject].obj.map(_._1).toSet should equal(Set("consumer_id", "consumer_name"))
    }

    Scenario("A different user sees their own Consumer", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest((v7_0_0_Request / "consumers" / "current" / "identity").GET <@ (user2))
      response.code should equal(200)
      response.body.extract[CurrentConsumerIdentityJsonV700].consumer_id should equal(testConsumer2.consumerId)
    }
  }
}
