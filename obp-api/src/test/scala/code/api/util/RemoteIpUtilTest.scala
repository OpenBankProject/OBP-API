package code.api.util

import code.setup.ServerSetup

/**
 * This suite checks how RemoteIpUtil decides a request's client address: the TCP peer by default,
 * the forwarding header when trust.proxy.enabled is true, and, with trust.proxy.peers set, the
 * header only from those peers.
 */
class RemoteIpUtilTest extends ServerSetup {

  private def headers(values: (String, String)*): String => Option[String] =
    name => values.find(_._1.equalsIgnoreCase(name)).map(_._2)

  feature("RemoteIpUtil.resolve") {

    scenario("with trust off, the TCP peer is the client, and a forwarding header is noted but ignored") {
      setPropsValues("trust.proxy.enabled" -> "false")
      val r = RemoteIpUtil.resolve("10.0.0.5", headers("X-Real-IP" -> "203.0.113.9"))
      r.clientIp shouldBe "10.0.0.5"
      r.forwardingHeaderPresent shouldBe true
      r.headerHonoured shouldBe false
    }

    scenario("with trust on and no peer list, the header is believed from anyone") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Real-IP", "trust.proxy.peers" -> "")
      val r = RemoteIpUtil.resolve("198.51.100.77", headers("X-Real-IP" -> "203.0.113.9"))
      r.clientIp shouldBe "203.0.113.9"
      r.headerHonoured shouldBe true
    }

    scenario("with a peer list, the header is believed only from those peers") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Real-IP", "trust.proxy.peers" -> "10.0.0.0/8, 2001:db8::/32")
      RemoteIpUtil.resolve("10.1.2.3", headers("X-Real-IP" -> "203.0.113.9")).clientIp shouldBe "203.0.113.9"
      RemoteIpUtil.resolve("[2001:db8::5]", headers("X-Real-IP" -> "203.0.113.9")).clientIp shouldBe "203.0.113.9"

      val direct = RemoteIpUtil.resolve("198.51.100.77", headers("X-Real-IP" -> "203.0.113.9"))
      direct.clientIp shouldBe "198.51.100.77"
      direct.headerFromUntrustedPeer shouldBe true
      direct.headerHonoured shouldBe false
    }

    scenario("X-Forwarded-For gives its leftmost address, and IPv6 comes back without brackets, in canonical form") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Forwarded-For", "trust.proxy.peers" -> "")
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "203.0.113.9, 10.0.0.1")).clientIp shouldBe "203.0.113.9"
      setPropsValues("trust.proxy.enabled" -> "false")
      RemoteIpUtil.resolve("[2001:DB8:0:0:0:0:0:1]", headers()).clientIp shouldBe "2001:db8::1"
    }
  }
}
