package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages._
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JArray, JObject}
import org.scalatest.Tag

/** GET /obp/v7.0.0/management/rate-limiter-config: the three limiters, in check order, each with its 429 code. */
class RateLimitersTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations7_0_0.getRateLimiterConfig))

  private def v7 = baseRequest / "obp" / "v7.0.0"
  private def str(json: org.json4s.JValue, field: String): String = (json \ field).values.toString

  feature("Get Rate Limiters") {
    scenario("unauthenticated is 401", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest(v7 / "management" / "rate-limiter-config")
      response.code should equal(401)
      response.body.toString should include(AuthenticatedUserIsRequired.split(":").head)
    }

    scenario("without CanGetConfig is 403", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest((v7 / "management" / "rate-limiter-config").GET <@ (user1))
      response.code should equal(403)
      response.body.toString should include(UserHasMissingRoles)
      response.body.toString should include(ApiRole.canGetConfig.toString)
    }

    scenario("with CanGetConfig the three limiters come back in check order with distinct 429 codes", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetConfig.toString)
      val response = makeGetRequest((v7 / "management" / "rate-limiter-config").GET <@ (user1))
      response.code should equal(200)

      val limiters = (response.body \ "rate_limiters").asInstanceOf[JArray].arr
      limiters.map(l => str(l, "name")) should equal(List("self_service", "authentication", "consumer"))
      limiters.map(l => str(l, "order")) should equal(List("1", "2", "3"))
      limiters.map(l => str(l, "error_code")) should equal(List("OBP-10060", "OBP-10061", "OBP-10018"))
      limiters.foreach { l =>
        List("shadow", "enforce") should contain(str(l, "mode"))
        (l \ "limits").asInstanceOf[JArray].arr should not be empty
      }

      Then("the self-service limiter lists its scopes but not login, which belongs to the authentication limiter")
      val selfServiceScopes = (limiters.head \ "limits").asInstanceOf[JArray].arr.map(l => str(l, "scope"))
      selfServiceScopes should contain allOf ("signup", "password_reset", "consumer_registration")
      selfServiceScopes should not contain "login"

      Then("the authentication limiter reports an ip and an account window")
      val authScopes = (limiters(1) \ "limits").asInstanceOf[JArray].arr.map(l => str(l, "scope"))
      authScopes should equal(List("ip", "account"))

      Then("the consumer limiter reports the props defaults and the anonymous ceiling")
      val consumerRows = (limiters(2) \ "limits").asInstanceOf[JArray].arr
      consumerRows.map(l => str(l, "scope")) should equal(List("consumer_default", "anonymous"))
      (consumerRows.last \ "per_hour").values.toString.toLong should be > 0L
    }
  }
}
