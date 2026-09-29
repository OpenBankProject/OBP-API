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
import code.api.util.{CustomJsonFormats, DeploymentChecks}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 Deployment Checks endpoint: how this instance and the applications in
 * front of it are set up, worked out from its props and its recent traffic (see
 * [[code.api.util.DeploymentChecks]]).
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700DeploymentChecks {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  // Route: GET /obp/v7.0.0/management/system/diagnostics/deployment
  lazy val getDeploymentChecks: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "system" / "diagnostics" / "deployment" =>
      EndpointHelpers.withUser(req) { (_, _) =>
        Future(JSONFactory700Operations.createDeploymentChecksJson(DeploymentChecks.run()))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDeploymentChecks),
    "GET",
    "/management/system/diagnostics/deployment",
    "Get Deployment Checks",
    s"""Checks of how this OBP-API instance, and the proxy and applications in front of it, are set up,
       |worked out from its props and from its traffic of the last ${DeploymentChecks.WindowMinutes} minutes.
       |
       |The protections against scans and floods (per-IP limits, IP penalties, the busiest-callers view) depend on
       |OBP-API seeing each caller's real address. A proxy or application set up wrongly defeats them without any
       |error: every caller looks like the proxy, or a caller can name any address it likes. Those mistakes show in the
       |traffic, which is what these checks look at.
       |
       |Each check has a `status` (OK, INFO, WARNING, ERROR, or MANUAL for something that cannot be seen from inside
       |OBP-API), a `basis` (`observed` from traffic, `configured` from props, or `manual`), a `message`, its
       |`evidence`, and the `props` involved. An observed check with too little traffic to judge (fewer than
       |${DeploymentChecks.MinRequestsToJudge} requests) says so, rather than guessing.
       |
       |Areas: client addresses (are they passed on, used, and believed only from the proxy), applications (does any
       |application call for many users from one address), rate limits, and observability (log level, Telemetry
       |collection, Redis, API Metrics).
       |
       |The traffic evidence is this instance's own: behind a load balancer, the response describes the instance that
       |answered (`api_instance_id`).
       |""".stripMargin,
    EmptyBody,
    JSONFactory700Operations.deploymentChecksJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagSystem, apiTagApi),
    Some(List(canGetConfig)),
    http4sPartialFunction = Some(getDeploymentChecks)
  )
}
