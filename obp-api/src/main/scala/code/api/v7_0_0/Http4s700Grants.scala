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
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CustomJsonFormats, Glossary}
import code.entitlement.Entitlement
import code.scope.Scope
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer

/**
 * What has been granted on this instance, across all Users and Consumers: every Scope, and the Role names
 * anyone holds (as an Entitlement or a Scope). A monitoring or code-review service such as OBP-Sentinel uses
 * the Role names to learn which endpoints can be reached here, without learning who holds them.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700Grants {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  // Route: GET /obp/v7.0.0/scopes
  lazy val getAllScopes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "scopes" =>
      EndpointHelpers.executeFuture(req) {
        Scope.scope.vend.getScopesFuture().map(scopes => JSONFactory700.createAllScopesJsonV700(scopes.openOr(Nil)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAllScopes),
    "GET",
    "/scopes",
    "Get all Scopes",
    s"""Get every Scope on this instance, for all Consumers: its `bank_id`, `role_name` and `consumer_id`.
       |
       |A Scope is a Role granted to a Consumer (an application) rather than to a User; see
       |${Glossary.getGlossaryItemLink("API.Endpoint Auth Modes")}. For the Scopes of one Consumer, see Get Scopes for Consumer.
       |
       |**Who may call it.** A User with the Role CanGetAllScopes, or an application whose Consumer holds it as a Scope.
       |""".stripMargin,
    EmptyBody,
    JSONFactory700.allScopesJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagScope, apiTagRole),
    Some(List(canGetAllScopes)),
    authMode = UserOrApplication,
    http4sPartialFunction = Some(getAllScopes)
  )

  // Route: GET /obp/v7.0.0/reachable-roles
  lazy val getReachableRoles: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "reachable-roles" =>
      EndpointHelpers.executeFuture(req) {
        for {
          entitlements <- Entitlement.entitlement.vend.getEntitlementsFuture()
          scopes <- Scope.scope.vend.getScopesFuture()
        } yield JSONFactory700.createReachableRolesJsonV700(
          entitlements.openOr(Nil).map(_.roleName) ++ scopes.openOr(Nil).map(_.roleName))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getReachableRoles),
    "GET",
    "/reachable-roles",
    "Get Reachable Roles",
    s"""Get the names of the Roles that someone holds on this instance, as an Entitlement (a User) or as a
       |Scope (a Consumer), each listed once.
       |
       |An endpoint that needs a Role can only be reached if someone holds that Role, so together with the
       |Resource Docs this tells which endpoints can be reached here. Nothing else is returned: no Users, no
       |Consumers, no bank ids.
       |
       |**Who may call it.** A User with the Role CanGetReachableRoles, or an application whose Consumer
       |holds it as a Scope, such as a code-review service run as a Platform App
       |(see ${Glossary.getGlossaryItemLink("Platform Apps")}).
       |""".stripMargin,
    EmptyBody,
    JSONFactory700.reachableRolesJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagRole, apiTagEntitlement),
    Some(List(canGetReachableRoles)),
    authMode = UserOrApplication,
    http4sPartialFunction = Some(getReachableRoles)
  )
}
