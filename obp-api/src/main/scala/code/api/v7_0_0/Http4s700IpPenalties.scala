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
import code.api.util.{CustomJsonFormats, IpPenalties}
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
 * This object holds the v7.0.0 endpoints that manage IP penalties: temporary per-minute limits an
 * operator puts on one IP address during an incident (see [[code.api.util.IpPenalties]]).
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700IpPenalties {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val penaltyDescription =
    s"""An IP penalty limits one IP address to a number of requests per minute, on every endpoint of
       |this instance, until it expires. A `per_minute_limit` of 0 refuses every request. A refused
       |request gets 429 `${TooManyRequestsIpPenalty.takeWhile(_ != ':')}`. Penalties are always
       |enforced, are shared by every instance (they are kept in Redis), and disappear by themselves
       |when they expire. The endpoints under `/management/ip-penalties` are never refused because of a
       |penalty, so a mistake can always be undone.
       |
       |The address is the client address OBP-API resolves for the request. Behind a proxy that does not
       |pass on the client's address, that is the proxy's address, and a penalty on it restricts
       |everyone behind the proxy.""".stripMargin

  // Route: POST /obp/v7.0.0/management/ip-penalties
  lazy val createIpPenalty: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "management" / "ip-penalties" =>
      EndpointHelpers.withUserAndBodyCreated[PostIpPenaltyJsonV700, IpPenaltyJsonV700](req) { (user, body, cc) =>
        for {
          _ <- Helper.booleanToFuture(InvalidIpPenalty, failCode = 400, cc = Some(cc)) {
            body.per_minute_limit >= 0 &&
              body.duration_minutes >= 1 && body.duration_minutes <= IpPenalties.MaxDurationMinutes &&
              body.reason != null && body.reason.trim.nonEmpty && body.reason.length <= IpPenalties.MaxReasonLength
          }
          _ <- Helper.booleanToFuture(s"$InvalidIpAddress Current value is ${body.ip_address}", failCode = 400, cc = Some(cc)) {
            IpPenalties.canonicalAddress(body.ip_address).isDefined
          }
          _ <- Helper.booleanToFuture(IpPenaltyAlreadyExists, failCode = 409, cc = Some(cc)) {
            !IpPenalties.exists(body.ip_address)
          }
          added <- Future(IpPenalties.add(body.ip_address, body.per_minute_limit, body.duration_minutes, body.reason.trim, user.userId))
          _ <- Helper.booleanToFuture(added.left.getOrElse(UnknownError), failCode = 409, cc = Some(cc))(added.isRight)
        } yield JSONFactory700Operations.createIpPenaltyJson(added.toOption.get)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createIpPenalty),
    "POST",
    "/management/ip-penalties",
    "Create IP Penalty",
    s"""Put one IP address under a temporary per-minute limit, for example during a denial-of-service
       |or scanning incident.
       |
       |$penaltyDescription
       |
       |`duration_minutes` is from 1 to ${IpPenalties.MaxDurationMinutes} (one week). An address can have one
       |penalty at a time: to change it, delete it and create it again (409 when one exists).
       |""".stripMargin,
    JSONFactory700Operations.postIpPenaltyJsonV700Example,
    JSONFactory700Operations.ipPenaltyJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidIpPenalty, InvalidIpAddress,
      IpPenaltyAlreadyExists, UnknownError),
    List(apiTagRateLimits, apiTagSystem),
    Some(List(canCreateIpPenalty)),
    http4sPartialFunction = Some(createIpPenalty)
  )

  // Route: GET /obp/v7.0.0/management/ip-penalties
  lazy val getIpPenalties: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "management" / "ip-penalties" =>
      EndpointHelpers.withUser(req) { (_, _) =>
        Future(IpPenaltiesJsonV700(IpPenalties.listAll().map(JSONFactory700Operations.createIpPenaltyJson)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getIpPenalties),
    "GET",
    "/management/ip-penalties",
    "Get IP Penalties",
    s"""Get the IP penalties in force, soonest to expire first.
       |
       |$penaltyDescription
       |""".stripMargin,
    EmptyBody,
    JSONFactory700Operations.ipPenaltiesJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagRateLimits, apiTagSystem),
    Some(List(canGetIpPenalties)),
    http4sPartialFunction = Some(getIpPenalties)
  )

  // Route: DELETE /obp/v7.0.0/management/ip-penalties/IP_ADDRESS
  lazy val deleteIpPenalty: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `prefixPath` / "management" / "ip-penalties" / ipAddress =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        for {
          removed <- Future(IpPenalties.remove(ipAddress))
          _ <- Helper.booleanToFuture(s"$IpPenaltyNotFound Current value is $ipAddress", failCode = 404, cc = Some(cc))(removed)
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteIpPenalty),
    "DELETE",
    "/management/ip-penalties/IP_ADDRESS",
    "Delete IP Penalty",
    s"""Remove the penalty on one IP address before it expires.
       |
       |$penaltyDescription
       |""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, IpPenaltyNotFound, UnknownError),
    List(apiTagRateLimits, apiTagSystem),
    Some(List(canDeleteIpPenalty)),
    http4sPartialFunction = Some(deleteIpPenalty)
  )
}
