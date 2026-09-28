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
package code.api.v7_0_0

import java.util.Date

import code.api.Constant
import code.api.util.{APIUtil, ExampleValue, IpPenalties}
import code.telemetry.{Telemetry, TrafficSources}

/*
 * The JSON of the v7.0.0 operations endpoints (Telemetry and IP penalties).
 *
 * These case classes are declared at package level rather than inside `object JSONFactory700`,
 * which already holds over a hundred nested case classes. json4s reads a case class's field names
 * from the compiled Scala signature; for a nested class that is the enclosing object's signature,
 * so the object's signature grows with every class added to it. Keeping new, self-contained groups
 * of JSON types in their own file stops that growth. JsonFactorySignatureTest checks that json4s
 * can read every case class nested in a JSONFactory object.
 */

// ===== Telemetry =====

/** One meter: its Micrometer name, type, unit, tags and current values (count, total_time, max, value ...). */
case class TelemetryMeterJsonV700(
  name: String,
  `type`: String,
  base_unit: Option[String],
  tags: Map[String, String],
  measurements: Map[String, Double]
)

/** The separate port Prometheus scrapes, as configured on this instance. */
case class TelemetryPortJsonV700(enabled: Boolean, port: Int, path: String)

case class TelemetryJsonV700(
  api_instance_id: String,
  git_commit: String,
  port: TelemetryPortJsonV700,
  meters: List[TelemetryMeterJsonV700]
)

// ===== IP penalties =====

/** Request body of POST /management/ip-penalties. `per_minute_limit` 0 refuses every request. */
case class PostIpPenaltyJsonV700(ip_address: String, per_minute_limit: Long, duration_minutes: Long, reason: String)

case class IpPenaltyJsonV700(
  ip_address: String,
  per_minute_limit: Long,
  reason: String,
  created_by_user_id: String,
  created_at: Date,
  expires_at: Date
)

case class IpPenaltiesJsonV700(ip_penalties: List[IpPenaltyJsonV700])

// ===== Where traffic is coming from =====

/** A Consumer among the busiest. `requests` is within `error` of the true count. */
case class TrafficConsumerJsonV700(
  consumer_id: String,
  application_name: String,
  requests: Long,
  error: Long,
  status_2xx: Long,
  status_4xx: Long,
  status_5xx: Long,
  refused: Long,
  unmatched: Long,
  endpoints: List[String],
  last_ip_address: String,
  first_seen: Date,
  last_seen: Date
)

/** A client IP address among the busiest, whatever credentials its requests carried. */
case class TrafficAddressJsonV700(
  ip_address: String,
  requests: Long,
  error: Long,
  status_2xx: Long,
  status_4xx: Long,
  status_5xx: Long,
  refused: Long,
  unmatched: Long,
  endpoints: List[String],
  consumer_ids: List[String],
  first_seen: Date,
  last_seen: Date
)

/** A pair of caller (a Consumer, or an IP address for other requests) and endpoint among the busiest. */
case class TrafficCallerEndpointJsonV700(
  caller_kind: String,
  caller: String,
  endpoint: String,
  api_version: Option[String],
  requests: Long,
  error: Long,
  status_2xx: Long,
  status_4xx: Long,
  status_5xx: Long,
  refused: Long,
  mean_duration_ms: Long,
  max_duration_ms: Long,
  last_seen: Date
)

case class TrafficSourcesJsonV700(
  api_instance_id: String,
  window_minutes: Int,
  consumers: List[TrafficConsumerJsonV700],
  addresses: List[TrafficAddressJsonV700],
  callers_and_endpoints: List[TrafficCallerEndpointJsonV700]
)

// ===== Deployment Checks =====

case class DeploymentCheckEvidenceJsonV700(name: String, value: String)

/** One check. `basis` is observed (from traffic), configured (from props) or manual (not visible from OBP-API). */
case class DeploymentCheckJsonV700(
  id: String,
  title: String,
  area: String,
  basis: String,
  status: String,
  message: String,
  evidence: List[DeploymentCheckEvidenceJsonV700],
  props: List[String]
)

case class DeploymentChecksJsonV700(
  api_instance_id: String,
  checked_at: Date,
  window_minutes: Int,
  errors: Int,
  warnings: Int,
  checks: List[DeploymentCheckJsonV700]
)

/** This object builds the JSON above, and holds the examples the ResourceDocs show. */
object JSONFactory700Operations {

  /** This instance's Telemetry, from the same registry the separate port serves, optionally limited to names starting with `namePrefix`. */
  def createTelemetryJson(namePrefix: Option[String]): TelemetryJsonV700 = {
    import scala.jdk.CollectionConverters._
    val settings = Telemetry.portSettings
    val meters = Telemetry.registry.getMeters.asScala.toList
      .filter(meter => namePrefix.forall(prefix => meter.getId.getName.startsWith(prefix)))
      .map { meter =>
        val id = meter.getId
        TelemetryMeterJsonV700(
          name = id.getName,
          `type` = id.getType.name.toLowerCase,
          base_unit = Option(id.getBaseUnit),
          tags = id.getTags.asScala.map(tag => tag.getKey -> tag.getValue).toMap,
          // A gauge whose source has gone reads NaN, which is not valid JSON.
          measurements = meter.measure().asScala
            .filter(measurement => java.lang.Double.isFinite(measurement.getValue))
            .map(measurement => measurement.getStatistic.name.toLowerCase -> measurement.getValue).toMap)
      }
      .sortBy(meter => (meter.name, meter.tags.toList.sorted.mkString(",")))
    TelemetryJsonV700(
      api_instance_id = Constant.ApiInstanceId,
      git_commit = APIUtil.gitCommit,
      port = TelemetryPortJsonV700(settings.enabled, settings.port, Telemetry.ScrapePath),
      meters = meters)
  }

  lazy val telemetryJsonV700Example = TelemetryJsonV700(
    api_instance_id = "obp_4f6b3c2a-9d1e-4b7a-8c5f-2e1d0a9b8c7d",
    git_commit = "3286937795b4d0c2e1f6a8b9c0d1e2f3a4b5c6d7",
    port = TelemetryPortJsonV700(enabled = true, port = Telemetry.DefaultPort, path = Telemetry.ScrapePath),
    meters = List(
      TelemetryMeterJsonV700("cache.gets", "counter", None, Map("cache" -> "json_schema", "result" -> "hit"), Map("count" -> 118.0)),
      TelemetryMeterJsonV700("jvm.threads.live", "gauge", Some("threads"), Map.empty, Map("value" -> 64.0)),
      TelemetryMeterJsonV700("obp.api.endpoint.requests", "timer", Some("seconds"),
        Map("operation" -> "OBPv7.0.0-getBanks", "api_version" -> "v7.0.0", "status" -> "2xx"),
        Map("count" -> 42.0, "total_time" -> 1.26, "max" -> 0.081))
    )
  )

  def createIpPenaltyJson(penalty: IpPenalties.Penalty): IpPenaltyJsonV700 =
    IpPenaltyJsonV700(penalty.ipAddress, penalty.perMinuteLimit, penalty.reason, penalty.createdByUserId,
      new Date(penalty.createdAtMillis), new Date(penalty.expiresAtMillis))

  lazy val postIpPenaltyJsonV700Example = PostIpPenaltyJsonV700(
    ip_address = "203.0.113.42", per_minute_limit = 10, duration_minutes = 60,
    reason = "Vulnerability scan: about 900 requests a minute to resource-docs with changing filters")

  lazy val ipPenaltyJsonV700Example = IpPenaltyJsonV700(
    ip_address = "203.0.113.42", per_minute_limit = 10,
    reason = "Vulnerability scan: about 900 requests a minute to resource-docs with changing filters",
    created_by_user_id = ExampleValue.userIdExample.value,
    created_at = APIUtil.DateWithMsExampleObject, expires_at = APIUtil.DateWithMsExampleObject)

  lazy val ipPenaltiesJsonV700Example = IpPenaltiesJsonV700(List(ipPenaltyJsonV700Example))

  // ===== Where traffic is coming from =====

  val TrafficRowsShown = 50

  private def endpointsOf(sets: List[scala.collection.mutable.LinkedHashSet[String]]): List[String] =
    sets.flatten.distinct.take(TrafficSources.EndpointsKeptPerCaller)

  /** The busiest Consumers, addresses, and callers and endpoints of this instance, over the last `windowMinutes`. */
  def createTrafficSourcesJson(windowMinutes: Int): TrafficSourcesJsonV700 = {
    import TrafficSources._
    val consumers = TrafficSources.consumers(windowMinutes).take(TrafficRowsShown).map { m =>
      val latest = m.details.maxBy(_.lastSeen)
      TrafficConsumerJsonV700(
        consumer_id = m.key,
        application_name = m.details.map(_.name).find(_.nonEmpty).getOrElse(""),
        requests = m.requests, error = m.error,
        status_2xx = m.details.map(_.status2xx).sum, status_4xx = m.details.map(_.status4xx).sum,
        status_5xx = m.details.map(_.status5xx).sum, refused = m.details.map(_.refused).sum,
        unmatched = m.details.map(_.unmatched).sum,
        endpoints = endpointsOf(m.details.map(_.endpoints)),
        last_ip_address = latest.lastIp,
        first_seen = new Date(m.details.map(_.firstSeen).min), last_seen = new Date(latest.lastSeen))
    }
    val addresses = TrafficSources.addresses(windowMinutes).take(TrafficRowsShown).map { m =>
      TrafficAddressJsonV700(
        ip_address = m.key,
        requests = m.requests, error = m.error,
        status_2xx = m.details.map(_.status2xx).sum, status_4xx = m.details.map(_.status4xx).sum,
        status_5xx = m.details.map(_.status5xx).sum, refused = m.details.map(_.refused).sum,
        unmatched = m.details.map(_.unmatched).sum,
        endpoints = endpointsOf(m.details.map(_.endpoints)),
        consumer_ids = m.details.flatMap(_.consumers).distinct.take(ConsumersKeptPerAddress),
        first_seen = new Date(m.details.map(_.firstSeen).min), last_seen = new Date(m.details.map(_.lastSeen).max))
    }
    val callerEndpoints = TrafficSources.callerEndpoints(windowMinutes).take(TrafficRowsShown).map { m =>
      val (caller, endpoint) = m.key
      val counted = m.details.map(d => d.status2xx + d.status4xx + d.status5xx).sum
      TrafficCallerEndpointJsonV700(
        caller_kind = caller.kind, caller = caller.value, endpoint = endpoint,
        api_version = m.details.map(_.apiVersion).find(_.nonEmpty),
        requests = m.requests, error = m.error,
        status_2xx = m.details.map(_.status2xx).sum, status_4xx = m.details.map(_.status4xx).sum,
        status_5xx = m.details.map(_.status5xx).sum, refused = m.details.map(_.refused).sum,
        mean_duration_ms = if (counted > 0) m.details.map(_.totalDurationMillis).sum / counted else 0L,
        max_duration_ms = m.details.map(_.maxDurationMillis).max,
        last_seen = new Date(m.details.map(_.lastSeen).max))
    }
    TrafficSourcesJsonV700(Constant.ApiInstanceId, windowMinutes, consumers, addresses, callerEndpoints)
  }

  def createDeploymentChecksJson(checks: List[code.api.util.DeploymentChecks.Check]): DeploymentChecksJsonV700 =
    DeploymentChecksJsonV700(
      api_instance_id = Constant.ApiInstanceId,
      checked_at = new Date(),
      window_minutes = code.api.util.DeploymentChecks.WindowMinutes,
      errors = checks.count(_.status == "ERROR"),
      warnings = checks.count(_.status == "WARNING"),
      checks = checks.map(c => DeploymentCheckJsonV700(c.id, c.title, c.area, c.basis, c.status, c.message,
        c.evidence.map { case (name, value) => DeploymentCheckEvidenceJsonV700(name, value) }, c.props)))

  lazy val deploymentChecksJsonV700Example = DeploymentChecksJsonV700(
    api_instance_id = "obp_4f6b3c2a-9d1e-4b7a-8c5f-2e1d0a9b8c7d",
    checked_at = APIUtil.DateWithMsExampleObject,
    window_minutes = 15,
    errors = 1,
    warnings = 0,
    checks = List(DeploymentCheckJsonV700(
      id = "check_client_address_forwarding", title = "Client addresses are passed on and used", area = "Client addresses",
      basis = "observed", status = "ERROR",
      message = "97% of requests carry a forwarding header (X-Real-IP or X-Forwarded-For), so a proxy is passing on client addresses, " +
        "but trust.proxy.enabled is false and OBP-API ignores them.",
      evidence = List(DeploymentCheckEvidenceJsonV700("requests (last 15 minutes)", "4210"),
        DeploymentCheckEvidenceJsonV700("with a forwarding header", "4090 (97%)")),
      props = List("trust.proxy.enabled", "trust.proxy.header", "trust.proxy.peers")))
  )

  lazy val trafficSourcesJsonV700Example = TrafficSourcesJsonV700(
    api_instance_id = "obp_4f6b3c2a-9d1e-4b7a-8c5f-2e1d0a9b8c7d",
    window_minutes = 5,
    consumers = List(TrafficConsumerJsonV700(
      consumer_id = ExampleValue.consumerIdExample.value, application_name = "Mobile Banking App",
      requests = 12400, error = 0, status_2xx = 12310, status_4xx = 85, status_5xx = 5, refused = 0, unmatched = 0,
      endpoints = List("OBPv7.0.0-getBanks", "OBPv6.0.0-getCoreAccountById"), last_ip_address = "198.51.100.23",
      first_seen = APIUtil.DateWithMsExampleObject, last_seen = APIUtil.DateWithMsExampleObject)),
    addresses = List(TrafficAddressJsonV700(
      ip_address = "203.0.113.42", requests = 52400, error = 300, status_2xx = 1200, status_4xx = 51100, status_5xx = 100,
      refused = 0, unmatched = 38000, endpoints = List("unmatched", "OBPv1.4.0-getResourceDocsObp"), consumer_ids = Nil,
      first_seen = APIUtil.DateWithMsExampleObject, last_seen = APIUtil.DateWithMsExampleObject)),
    callers_and_endpoints = List(TrafficCallerEndpointJsonV700(
      caller_kind = "ip", caller = "203.0.113.42", endpoint = "unmatched", api_version = None,
      requests = 38000, error = 250, status_2xx = 0, status_4xx = 38000, status_5xx = 0, refused = 0,
      mean_duration_ms = 3, max_duration_ms = 41, last_seen = APIUtil.DateWithMsExampleObject))
  )
}
