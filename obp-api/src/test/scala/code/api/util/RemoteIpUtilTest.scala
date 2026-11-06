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

    scenario("X-Forwarded-For is read from the right: the first address not in trust.proxy.peers is the client") {
      // NGINX 10.0.0.2, API Explorer II 10.0.0.3, Opey 10.0.0.4 and OBP-MCP 10.0.0.5 are trusted.
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Forwarded-For", "trust.proxy.peers" -> "10.0.0.0/24")
      val chain = "6.6.6.6, 203.0.113.9, 10.0.0.2, 10.0.0.3, 10.0.0.4"
      val r = RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> chain))
      r.clientIp shouldBe "203.0.113.9"
      r.headerHonoured shouldBe true
    }

    scenario("X-Forwarded-For: a caller that is not trusted becomes the client, whatever it wrote to its left") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Forwarded-For", "trust.proxy.peers" -> "10.0.0.0/24")
      // An MCP client at 198.51.100.7 asks OBP-MCP (trusted) to pass on a made-up chain; OBP-MCP appends the caller.
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "6.6.6.6, 10.0.0.2, 198.51.100.7")).clientIp shouldBe "198.51.100.7"
    }

    scenario("X-Forwarded-For: an entry that is not an address stops the walk at the nearest trusted hop") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Forwarded-For", "trust.proxy.peers" -> "10.0.0.0/24")
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "203.0.113.9, unknown, 10.0.0.2")).clientIp shouldBe "10.0.0.2"
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "203.0.113.9, unknown")).clientIp shouldBe "10.0.0.5"
    }

    scenario("X-Forwarded-For: when every entry is trusted, the leftmost is the client; IPv6 entries are canonicalised") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Forwarded-For", "trust.proxy.peers" -> "10.0.0.0/24, 2001:db8::/32")
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "10.0.0.9, 10.0.0.2")).clientIp shouldBe "10.0.0.9"
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "2001:DB8:0:0:0:0:0:1, [2001:db8::2]")).clientIp shouldBe "2001:db8::1"
    }

    scenario("X-Forwarded-For with no peer list gives its leftmost address, and IPv6 comes back without brackets, in canonical form") {
      setPropsValues("trust.proxy.enabled" -> "true", "trust.proxy.header" -> "X-Forwarded-For", "trust.proxy.peers" -> "")
      RemoteIpUtil.resolve("10.0.0.5", headers("X-Forwarded-For" -> "203.0.113.9, 10.0.0.1")).clientIp shouldBe "203.0.113.9"
      setPropsValues("trust.proxy.enabled" -> "false")
      RemoteIpUtil.resolve("[2001:DB8:0:0:0:0:0:1]", headers()).clientIp shouldBe "2001:db8::1"
    }
  }
}
