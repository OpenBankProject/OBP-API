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
import code.api.util.CustomJsonFormats
import code.telemetry.TrafficSources
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoint that shows where the traffic on this instance is coming
 * from: the busiest Consumers, client IP addresses, and callers and endpoints (see
 * [[code.telemetry.TrafficSources]]).
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700TrafficSources {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val AllowedWindows = Set(1, 5, 15)

  // Route: GET /obp/v7.0.0/management/traffic/top-callers
  lazy val getTrafficSources: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "traffic" / "top-callers" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val window = req.uri.query.params.get("window").map(_.trim).getOrElse("5")
        for {
          _ <- Helper.booleanToFuture(s"$InvalidTrafficWindow Current value is $window", failCode = 400, cc = Some(cc)) {
            window.forall(_.isDigit) && window.nonEmpty && AllowedWindows.contains(window.toInt)
          }
        } yield JSONFactory700Operations.createTrafficSourcesJson(window.toInt)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTrafficSources),
    "GET",
    "/management/traffic/top-callers",
    "Get Top Callers",
    s"""Where the traffic on this OBP-API instance is coming from: the busiest Consumers, the busiest
       |client IP addresses, and the busiest pairs of caller and endpoint, over the last `window`
       |minutes (1, 5 or 15; default 5).
       |
       |- `consumers`: requests that authenticated a Consumer, by Consumer.
       |- `addresses`: every request, authenticated or not, by client IP address, with the Consumers seen from it.
       |- `callers_and_endpoints`: by caller (the Consumer when one was authenticated, otherwise the IP address)
       |  and endpoint. The endpoint is an operation id, `unmatched` for paths no endpoint serves (one value for
       |  all of them), `refused:LIMITER` for requests refused before routing, or `other`.
       |
       |Each list shows at most ${JSONFactory700Operations.TrafficRowsShown} rows, busiest first. Counts are
       |estimates: `requests` is within `error` of the true count. `status_*`, `refused` and `unmatched` are exact
       |from when the row started being tracked in each minute.
       |
       |How it is counted. Each minute has three tables of fixed size (${TrafficSources.ConsumerSlots} Consumers,
       |${TrafficSources.AddressSlots} addresses, ${TrafficSources.CallerEndpointSlots} pairs), kept with the
       |Space-Saving algorithm of Ahmed Metwally, Divyakant Agrawal and Amr El Abbadi ("Efficient Computation of
       |Frequent and Top-k Elements in Data Streams", ICDT 2005), which refines the frequent-items algorithm of
       |Jayadev Misra and David Gries (1982). Any caller with more than 1/${TrafficSources.ConsumerSlots} of a
       |minute's requests is guaranteed to appear. Minutes are merged into the window as mergeable summaries
       |(Agarwal, Cormode, Huang, Phillips, Wei and Yi, PODS 2012). ${TrafficSources.MinutesKept} minutes are kept.
       |
       |Each instance counts only its own traffic, in memory. Behind a load balancer the response describes the
       |instance that answered (`api_instance_id`). Nothing is written to the database or to Prometheus, and IP
       |addresses are forgotten after ${TrafficSources.MinutesKept} minutes. IP addresses are personal data: this
       |Role should be granted only to people who handle incidents.
       |""".stripMargin,
    EmptyBody,
    JSONFactory700Operations.trafficSourcesJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidTrafficWindow, UnknownError),
    List(apiTagRateLimits, apiTagSystem),
    Some(List(canGetTrafficSources)),
    http4sPartialFunction = Some(getTrafficSources)
  )
}
