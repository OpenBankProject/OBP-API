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
import code.telemetry.Telemetry

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
}
