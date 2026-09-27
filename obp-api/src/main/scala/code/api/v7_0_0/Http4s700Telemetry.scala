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

import cats.effect.IO
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CustomJsonFormats, Glossary}
import code.telemetry.Telemetry
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 Telemetry endpoint, which shows people (through API Explorer or API
 * Manager) the same Telemetry that Prometheus collects from the separate Telemetry port.
 *
 * It is declared in its own object, like the Dynamic Entity definitions, to keep Http4s700's
 * initialiser under the JVM's 64KB method limit.
 */
object Http4s700Telemetry {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  // Route: GET /obp/v7.0.0/management/telemetry
  lazy val getTelemetry: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "telemetry" =>
      EndpointHelpers.withUser(req) { (_, _) =>
        val namePrefix = req.uri.query.params.get("name_prefix").filter(_.nonEmpty)
        Future(JSONFactory700.createTelemetryJson(namePrefix))
      }
  }

  /** How this instance's separate port is set, for the description. Read once, when the docs are built. */
  private val portDescription: String = {
    val settings = Telemetry.portSettings
    if (settings.enabled)
      s"On this instance the Telemetry port is open: Prometheus collects Telemetry from port ${settings.port} " +
        s"of each OBP-API instance, at the path `${Telemetry.ScrapePath}`."
    else
      s"On this instance the Telemetry port is not open, so Telemetry can be read only through this endpoint. " +
        s"When it is open, Prometheus collects Telemetry from a separate port of each OBP-API instance, at the path `${Telemetry.ScrapePath}`."
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTelemetry),
    "GET",
    "/management/telemetry",
    "Get Telemetry",
    s"""Get this OBP-API instance's Telemetry: aggregated numbers about how it is running, such as the
       |number and duration of requests per endpoint, Connector calls per method, cache hits and misses,
       |the database connection pool, the log dispatch queue, memory, garbage collection and threads.
       |
       |Telemetry is not API Metrics. It never records who made a call; see ${Glossary.getGlossaryItemLink("Telemetry")}.
       |
       |**Which instance answered.** Behind a load balancer, each call can reach a different OBP-API
       |instance, and each instance reports only its own figures. The response names the instance that
       |answered with `api_instance_id` (the same id its API Metrics records carry) and `git_commit`.
       |
       |**The separate port.** $portDescription
       |Prometheus should use that port, not this endpoint: the port is read from each instance directly,
       |so figures from different instances are never mixed; it keeps answering when the API is overloaded;
       |and reading it writes no API Metrics record.
       |
       |**Names.** Meters are listed under their Micrometer names, with dots. Prometheus shows the same
       |meters with underscores, `_total` added to counters and `_seconds` to timers, so
       |`obp.api.endpoint.requests` appears there as `obp_api_endpoint_requests_seconds`.
       |OBP-API's own meters start with `obp.api.`; the others (`jvm.`, `hikaricp.`, `cache.`, `process.`,
       |`system.`) are standard.
       |
       |**Filter.** `name_prefix` limits the list to meters whose name starts with it,
       |for example `?name_prefix=obp.api.endpoint`.
       |""".stripMargin,
    EmptyBody,
    JSONFactory700.telemetryJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagApi, apiTagSystem),
    Some(List(canGetTelemetry)),
    http4sPartialFunction = Some(getTelemetry)
  )
}
