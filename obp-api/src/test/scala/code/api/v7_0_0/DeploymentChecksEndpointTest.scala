package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetConfig
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/** This suite checks GET /obp/v7.0.0/management/system/diagnostics/deployment. */
class DeploymentChecksEndpointTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("getDeploymentChecks")

  private def deployment = v7_0_0_Request / "management" / "system" / "diagnostics" / "deployment"

  feature(s"Get Deployment Checks - $VersionOfApi") {

    scenario("anonymous is 401, without CanGetConfig is 403", ApiEndpoint, VersionOfApi) {
      val anonymous = makeGetRequest(deployment.GET)
      anonymous.code should equal(401)
      anonymous.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
      val noRole = makeGetRequest(deployment.GET <@ (user1))
      noRole.code should equal(403)
      noRole.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetConfig)
    }

    scenario("with CanGetConfig every check comes back with a status and a basis", ApiEndpoint, VersionOfApi) {
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConfig.toString)
      val response = try makeGetRequest(deployment.GET <@ (user1)) finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
      response.code should equal(200)
      val checks = response.body.extract[DeploymentChecksJsonV700]
      checks.checks.map(_.id) should contain allOf ("check_client_address_forwarding", "check_trusted_proxy_peers",
        "check_applications_pass_on_addresses", "check_root_log_level", "check_edge_rate_limits")
      checks.checks.foreach { c =>
        List("OK", "INFO", "WARNING", "ERROR", "MANUAL") should contain(c.status)
        List("observed", "configured", "manual") should contain(c.basis)
      }
      checks.errors should equal(checks.checks.count(_.status == "ERROR"))
    }
  }
}
