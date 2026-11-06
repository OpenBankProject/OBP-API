/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.util

import code.telemetry.{Telemetry, TrafficSources}

import scala.util.Try

/**
 * This object checks how this OBP-API instance and the applications in front of it are set up, as
 * far as can be told from inside: its props, and the traffic of the last 15 minutes.
 *
 * It exists because the protections against scans and floods (per-IP limits, IP penalties, the
 * busiest-callers view) are only as good as the client address OBP-API sees, and a wrong proxy or
 * application setup makes them useless or harmful without any error anywhere: every caller looks
 * like the proxy, or a caller can name any address it likes. Those mistakes show in the traffic, so
 * they can be found without asking anyone how the deployment was built.
 *
 * Each check says whether it was worked out from traffic (`observed`), from props (`configured`),
 * or cannot be seen from inside OBP-API at all (`manual`), and never guesses: with too little
 * traffic to judge, an observed check says so.
 */
object DeploymentChecks {

  final case class Check(
    id: String,
    title: String,
    area: String,
    basis: String,   // observed | configured | manual
    status: String,  // OK | INFO | WARNING | ERROR | MANUAL
    message: String,
    evidence: List[(String, String)],
    props: List[String]
  )

  val WindowMinutes = 15
  /** Fewer requests than this in the window is too little traffic to judge an observed check. */
  val MinRequestsToJudge = 20L
  /** An application seen for at least this many users from a single address is not passing on addresses. */
  val UsersFromOneAddress = 5

  private def percent(part: Long, whole: Long): String = if (whole == 0) "0%" else f"${part * 100.0 / whole}%.0f%%"

  private def isPrivate(address: String): Boolean =
    Try(com.google.common.net.InetAddresses.forString(address)).toOption.exists { a =>
      a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
        (a.getAddress.length == 16 && (a.getAddress()(0) & 0xfe) == 0xfc) // IPv6 unique local, fc00::/7
    }

  def run(): List[Check] = clientAddressChecks ++ applicationChecks ++ rateLimitChecks ++ observabilityChecks

  // ===== Client addresses =====

  private def clientAddressChecks: List[Check] = {
    val trustEnabled = APIUtil.getPropsAsBoolValue("trust.proxy.enabled", false)
    val header = APIUtil.getPropsValue("trust.proxy.header", "X-Real-IP")
    val peersConfigured = RemoteIpUtil.trustedPeers.nonEmpty
    val minutes = TrafficSources.forwarding(WindowMinutes)
    val requests = minutes.map(_.requests).sum
    val withHeader = minutes.map(_.withForwardingHeader).sum
    val untrusted = minutes.map(_.headerFromUntrustedPeer).sum
    val peers = minutes.flatMap(_.peers).distinct
    val peersSendingHeader = minutes.flatMap(_.peersSendingHeader).distinct
    val tooLittle = requests < MinRequestsToJudge
    val forwardingProps = List("trust.proxy.enabled", "trust.proxy.header", "trust.proxy.peers")
    val trafficEvidence = List(
      "requests (last 15 minutes)" -> requests.toString,
      "with a forwarding header" -> s"$withHeader (${percent(withHeader, requests)})",
      "distinct TCP peers" -> peers.size.toString,
      "TCP peers sending the header" -> peersSendingHeader.take(10).mkString(", "))

    val forwarding = {
      val (status, message) =
        if (tooLittle) ("INFO", s"Too little traffic in the last $WindowMinutes minutes to judge ($requests requests).")
        else if (!trustEnabled && withHeader * 2 >= requests)
          ("ERROR", s"${percent(withHeader, requests)} of requests carry a forwarding header (X-Real-IP or X-Forwarded-For), so a proxy is " +
            "passing on client addresses, but trust.proxy.enabled is false and OBP-API ignores them. Every caller appears to come from the " +
            "proxy: per-IP limits, IP penalties and the busiest-callers view all see one address.")
        else if (!trustEnabled && withHeader > 0)
          ("WARNING", s"${percent(withHeader, requests)} of requests carry a forwarding header, which OBP-API ignores (trust.proxy.enabled is false). " +
            "Some traffic may come through a proxy whose client addresses are lost.")
        else if (!trustEnabled)
          ("OK", "No forwarding header seen: OBP-API uses the TCP peer as the client address, which is right when nothing sits in front of it.")
        else if (withHeader * 2 < requests)
          ("WARNING", s"trust.proxy.enabled is true but only ${percent(withHeader, requests)} of requests carry $header. The proxy may not set it, " +
            "or traffic reaches OBP-API without going through the proxy.")
        else ("OK", s"Client addresses are taken from $header, present on ${percent(withHeader, requests)} of requests.")
      Check("check_client_address_forwarding", "Client addresses are passed on and used", "Client addresses", "observed",
        status, message, trafficEvidence, forwardingProps)
    }

    val trustedPeers = {
      val (status, message) =
        if (!trustEnabled) ("OK", "OBP-API does not take client addresses from headers, so it cannot be told a false one.")
        else if (!peersConfigured)
          ("WARNING", s"$header is believed from whoever sends it (trust.proxy.peers is not set). A caller that reaches OBP-API directly, " +
            "bypassing the proxy, can name any address: to slip past per-IP limits, or to have someone else penalised. " +
            "List the proxy's addresses in trust.proxy.peers.")
        else if (untrusted > 0)
          ("WARNING", s"$untrusted requests in the last $WindowMinutes minutes carried $header from a peer not in trust.proxy.peers; the header was " +
            "ignored. Something reaches OBP-API without going through the proxy.")
        else ("OK", s"$header is believed only from the peers in trust.proxy.peers.")
      Check("check_trusted_proxy_peers", "Only the proxy can name a client address", "Client addresses", "configured",
        status, message,
        List("trust.proxy.peers" -> APIUtil.getPropsValue("trust.proxy.peers", "(not set)"),
          "headers ignored from untrusted peers (last 15 minutes)" -> untrusted.toString),
        forwardingProps)
    }

    val concentration = {
      val busiest = TrafficSources.addresses(WindowMinutes).headOption
      val (status, message) = busiest match {
        case _ if tooLittle => ("INFO", s"Too little traffic in the last $WindowMinutes minutes to judge ($requests requests).")
        case Some(top) if top.requests * 10 >= requests * 8 && isPrivate(top.key) =>
          ("WARNING", s"${percent(top.requests, requests)} of requests come from ${top.key}, a private address. That is usually a proxy or " +
            "an application whose callers' addresses are not passed on, not a real client.")
        case Some(top) if top.requests * 10 >= requests * 8 =>
          ("INFO", s"${percent(top.requests, requests)} of requests come from one address, ${top.key}. Check it is a real client and not a proxy.")
        case _ => ("OK", "No single address carries most of the traffic.")
      }
      Check("check_address_concentration", "Traffic is spread across real client addresses", "Client addresses", "observed",
        status, message,
        busiest.map(top => List("busiest address" -> top.key, "its share" -> percent(top.requests, requests),
          "private address" -> isPrivate(top.key).toString)).getOrElse(Nil),
        forwardingProps)
    }

    List(forwarding, trustedPeers, concentration)
  }

  // ===== Applications =====

  private def applicationChecks: List[Check] = {
    val consumers = TrafficSources.consumers(WindowMinutes)
    val notForwarding = consumers.flatMap { c =>
      val users = c.details.flatMap(_.users).distinct
      val addresses = c.details.flatMap(_.addresses).distinct
      if (users.size >= UsersFromOneAddress && addresses.size == 1) {
        val name = c.details.map(_.name).find(_.nonEmpty).getOrElse(c.key)
        Some(s"$name (${c.key}): ${users.size}+ users, all from ${addresses.head}")
      } else None
    }
    val (status, message) =
      if (consumers.isEmpty) ("INFO", s"No authenticated traffic in the last $WindowMinutes minutes to judge.")
      else if (notForwarding.nonEmpty)
        ("WARNING", s"${notForwarding.size} application(s) call OBP-API for several users from a single address, so their users' addresses " +
          "are not passed on. Per-IP limits and penalties then treat all their users as one caller. Such an application should send its " +
          "user's address in the forwarding header, and be listed in trust.proxy.peers.")
      else ("OK", "No application calls for many users from a single address.")
    List(Check("check_applications_pass_on_addresses", "Applications pass on their users' addresses", "Applications", "observed",
      status, message, notForwarding.zipWithIndex.map { case (line, i) => s"application ${i + 1}" -> line },
      List("trust.proxy.enabled", "trust.proxy.header", "trust.proxy.peers")))
  }

  // ===== Rate limits =====

  private def warnedSinceStart(scope: String): Long =
    Option(Telemetry.registry.find("obp.api.self_service_rate_limit.checks").tags("scope", scope, "outcome", "warned").counter())
      .map(_.count().toLong).getOrElse(0L)

  private def rateLimitChecks: List[Check] = {
    val scopes = SelfServiceRateLimiter.scopeDefaults.keys.toList.sorted
    val shadowScopes = scopes.filter(s => SelfServiceRateLimiter.modeFor(s) == SelfServiceRateLimiter.ModeShadow)
    val selfService = Check("check_self_service_mode", "Per-IP limits are enforced", "Rate limits", "configured",
      if (!SelfServiceRateLimiter.enabled) "WARNING" else if (shadowScopes.nonEmpty) "INFO" else "OK",
      if (!SelfServiceRateLimiter.enabled) "The self-service (per-IP) limiter is switched off."
      else if (shadowScopes.nonEmpty) s"${shadowScopes.size} of ${scopes.size} scopes are in shadow mode: over their limit, a request is warned, " +
        "not refused. The evidence shows how many requests each would have refused since start-up; check those before enforcing."
      else "Every scope is enforced.",
      scopes.map(s => s"$s (${SelfServiceRateLimiter.modeFor(s)})" -> s"${warnedSinceStart(s)} would have been refused since start-up"),
      List("self_service.rate_limit.enabled", "self_service.rate_limit.mode", "self_service.rate_limit.<scope>.mode"))

    val windows = List("rate_limiting_per_second", "rate_limiting_per_minute", "rate_limiting_per_hour", "rate_limiting_per_day")
    val consumerDefaults = windows.map(w => w -> APIUtil.getPropsAsLongValue(w, -1L))
    val consumerCheck = Check("check_consumer_default_limits", "Consumers without their own limits are limited", "Rate limits", "configured",
      if (consumerDefaults.forall(_._2 < 0)) "WARNING" else "OK",
      if (consumerDefaults.forall(_._2 < 0))
        "A Consumer with no rate limit rows of its own has no limit at all (every rate_limiting_per_* prop is -1)."
      else "Consumers without their own rate limit rows get the defaults shown.",
      consumerDefaults.map { case (w, v) => w -> v.toString }, windows)

    val anonymous = APIUtil.getPropsAsIntValue("user_consumer_limit_anonymous_access", 1000)
    val anonymousCheck = Check("check_anonymous_limit", "Anonymous calls are limited per address", "Rate limits", "configured",
      if (anonymous < 0) "WARNING" else "OK",
      if (anonymous < 0) "Anonymous calls have no hourly limit (-1)."
      else s"Anonymous calls are limited to $anonymous an hour per client address (applies to endpoints behind the middleware).",
      List("user_consumer_limit_anonymous_access" -> anonymous.toString), List("user_consumer_limit_anonymous_access"))

    val edge = Check("check_edge_rate_limits", "The proxy and API Explorer limit requests too", "Rate limits", "manual",
      "MANUAL", "Limits set in a proxy, load balancer or API Explorer cannot be seen from inside OBP-API. Confirm they exist: during the " +
        "NMB scan of 2026-09-23, API Explorer passed about 33,000 of 52,000 requests straight through.",
      Nil, Nil)

    List(selfService, consumerCheck, anonymousCheck, edge)
  }

  // ===== Observability and the NMB causes =====

  private def observabilityChecks: List[Check] = {
    val root = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    val level = root match {
      case logback: ch.qos.logback.classic.Logger => Option(logback.getEffectiveLevel).map(_.toString).getOrElse("unknown")
      case _ => "unknown"
    }
    val logCheck = Check("check_root_log_level", "The root log level is not DEBUG", "Observability", "configured",
      if (level == "DEBUG" || level == "TRACE") "ERROR" else "OK",
      if (level == "DEBUG" || level == "TRACE")
        s"The root log level is $level. Logging every request at $level costs CPU and memory under load; it was one of the causes of the NMB " +
          "outage of 2026-09-23. Use INFO in production (logback.xml, or LOG_LEVEL)."
      else s"The root log level is $level.",
      List("effective root log level" -> level), List("logback.xml root level"))

    val port = Telemetry.portSettings
    val scrape = Telemetry.lastScrapeMillis
    val ageSeconds = scrape.map(t => (System.currentTimeMillis() - t) / 1000)
    val telemetryCheck = Check("check_telemetry_collection", "Prometheus collects Telemetry", "Observability", "observed",
      if (!port.enabled) "INFO" else if (ageSeconds.forall(_ > 300)) "WARNING" else "OK",
      if (!port.enabled) "The Telemetry port is not open, so Prometheus cannot collect Telemetry from this instance."
      else ageSeconds match {
        case None => s"The Telemetry port is open on ${port.port}, but nothing has collected from it since this instance started."
        case Some(age) if age > 300 => s"Telemetry was last collected $age seconds ago; Prometheus may have stopped."
        case Some(age) => s"Telemetry was last collected $age seconds ago."
      },
      List("port open" -> port.enabled.toString, "port" -> port.port.toString,
        "last collected" -> scrape.map(t => java.time.Instant.ofEpochMilli(t).toString).getOrElse("never since start-up")),
      List("telemetry.port.enabled", "telemetry.port", "telemetry.host"))

    val redisReachable = Try(code.api.cache.Redis.use(code.api.JedisMethod.GET, s"${code.api.Constant.getGlobalCacheNamespacePrefix}deployment_check", None, None)).isSuccess
    val redisCheck = Check("check_redis", "Redis is reachable", "Observability", "observed",
      if (redisReachable) "OK" else "ERROR",
      if (redisReachable) "Redis answered." else "Redis did not answer. Caches, rate-limit counters, IP penalties and cache namespaces all fail open without it.",
      Nil, Nil)

    import scala.jdk.CollectionConverters._
    val lost = Telemetry.registry.find("obp.api.batch_writer.rows").tags("result", "lost").counters().asScala.map(_.count().toLong).sum
    val metricsCheck = Check("check_api_metrics", "API Metrics are recorded without loss", "Observability", "observed",
      if (!code.metrics.MetricsProps.writeMetrics) "INFO" else if (lost > 0) "WARNING" else "OK",
      if (!code.metrics.MetricsProps.writeMetrics) "API Metrics are not recorded on this instance (write_metrics is false)."
      else if (lost > 0) s"$lost API Metrics or Connector Metrics records have been lost to failed database writes since start-up."
      else "API Metrics are recorded, with no records lost since start-up.",
      List("write_metrics" -> code.metrics.MetricsProps.writeMetrics.toString, "records lost since start-up" -> lost.toString),
      List("write_metrics", "write_connector_metrics"))

    List(logCheck, telemetryCheck, redisCheck, metricsCheck)
  }
}
