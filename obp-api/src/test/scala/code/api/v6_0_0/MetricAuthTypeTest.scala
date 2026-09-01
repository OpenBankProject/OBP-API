package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanReadMetrics
import code.entitlement.Entitlement
import code.metrics.MetricBatchWriter
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Tests the auth_type field on metric rows (v6.0.0): the authentication SCHEME of each
 * call — never the credential. Uses the url-filter isolation pattern (write_metrics is
 * JVM-wide; other suites also land rows).
 *
 * The harness's <@ (user1) authenticates via DirectLogin, so this suite's rows must
 * carry auth_type "DirectLogin"; the invariant "Consent only with a consent reference"
 * is asserted over whatever rows the filter returns.
 */
class MetricAuthTypeTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag("getMetrics")

  private val KnownAuthTypes =
    Set("Consent", "OAuth2", "OAuth1", "DirectLogin", "GatewayLogin", "DAuth", "Anonymous", "Other")

  feature(s"test auth_type on metric rows version $VersionOfApi") {
    scenario("Rows carry the scheme that authenticated them", ApiEndpoint1, VersionOfApi) {
      setPropsValues("write_metrics" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanReadMetrics.toString)

      val trafficUrl = "/obp/v5.1.0/banks"
      val requestBanks = (v5_1_0_Request / "banks").GET <@ (user1)
      (1 to 3).foreach(_ => makeGetRequest(requestBanks))

      MetricBatchWriter.flush()

      When("We fetch the metric rows for the traffic url")
      val request = (v6_0_0_Request / "management" / "metrics").GET <@ (user1) <<? List(
        ("url", trafficUrl),
        ("limit", "10"))
      val response = makeGetRequest(request)
      Then("Every row has a known auth_type, and ours are DirectLogin")
      response.code should equal(200)
      val metrics = (response.body \ "metrics").children
      metrics should not be empty
      metrics.foreach { m =>
        val authType = (m \ "auth_type").extractOpt[String]
        withClue(s"row: $m ") {
          authType.isDefined shouldBe true
          KnownAuthTypes should contain(authType.get)
          // Scheme/reference consistency: "Consent" implies a consent reference and vice versa.
          val consentRef = (m \ "consent_reference_id").extractOpt[String]
          (authType.get == "Consent") shouldBe consentRef.isDefined
        }
      }
      metrics.map(m => (m \ "auth_type").extractOpt[String]).flatten.toSet shouldBe Set("DirectLogin")
    }
  }
}
