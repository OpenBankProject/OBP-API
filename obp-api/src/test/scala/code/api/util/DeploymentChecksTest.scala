package code.api.util

import code.api.util.RemoteIpUtil.Resolution
import code.setup.ServerSetup
import code.telemetry.TrafficSources
import code.telemetry.TrafficSources.Note

/**
 * This suite feeds TrafficSources with chosen traffic and checks that DeploymentChecks finds the
 * set-up mistakes it is meant to find.
 */
class DeploymentChecksTest extends ServerSetup {

  private def check(id: String) = DeploymentChecks.run().find(_.id == id).getOrElse(fail(s"no check $id"))

  private def through(peer: String, header: Boolean) = Resolution(peer, peer, forwardingHeaderPresent = header, headerHonoured = false, headerFromUntrustedPeer = false)

  private def anonymous = new Note

  feature("Deployment Checks") {

    scenario("too little traffic is reported as such, not guessed about") {
      TrafficSources.clear()
      setPropsValues("trust.proxy.enabled" -> "false")
      check("check_client_address_forwarding").status shouldBe "INFO"
    }

    scenario("a proxy passing on addresses that OBP-API ignores is an error") {
      TrafficSources.clear()
      setPropsValues("trust.proxy.enabled" -> "false")
      (1 to 30).foreach(_ => TrafficSources.record(anonymous, through("10.0.0.5", header = true), 200, 3))
      val forwarding = check("check_client_address_forwarding")
      forwarding.status shouldBe "ERROR"
      forwarding.message should include("trust.proxy.enabled is false")
      Then("and one private address carrying all the traffic is flagged too")
      check("check_address_concentration").status shouldBe "WARNING"
    }

    scenario("believing the header from anyone is a warning; a peer list clears it") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.peers" -> "")
      check("check_trusted_proxy_peers").status shouldBe "WARNING"
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.peers" -> "10.0.0.0/8")
      TrafficSources.clear()
      check("check_trusted_proxy_peers").status shouldBe "OK"
      setPropsValues("trust.proxy.enabled" -> "false", "trust.proxy.peers" -> "")
    }

    scenario("an application calling for many users from one address is named") {
      TrafficSources.clear()
      (1 to 6).foreach { i =>
        val note = new Note
        note.consumerId = Some("consumer-explorer")
        note.consumerName = Some("Explorer probe")
        note.userId = Some(s"user-$i")
        TrafficSources.record(note, through("10.0.0.9", header = false), 200, 3)
      }
      val applications = check("check_applications_pass_on_addresses")
      applications.status shouldBe "WARNING"
      applications.evidence.map(_._2).mkString should include("Explorer probe")
    }

    scenario("a DEBUG root log level is an error") {
      val root = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[ch.qos.logback.classic.Logger]
      val original = root.getLevel
      try {
        root.setLevel(ch.qos.logback.classic.Level.DEBUG)
        check("check_root_log_level").status shouldBe "ERROR"
        root.setLevel(ch.qos.logback.classic.Level.INFO)
        check("check_root_log_level").status shouldBe "OK"
      } finally root.setLevel(original)
    }

    scenario("limits outside OBP-API are a manual item, never guessed") {
      check("check_edge_rate_limits").status shouldBe "MANUAL"
    }
  }
}
