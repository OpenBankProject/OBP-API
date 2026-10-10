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
import code.api.Constant.{ApiPathZero, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID}
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.{ApiPropsWithAlias, ApiRole, CallContext, CustomJsonFormats, NewStyle}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Empty, Full}
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

case class MyRoleCallerJsonV700(user_id: String, consumer_id: String, application_only: Boolean)
case class MyRoleHeldByJsonV700(user: Boolean, consumer: Boolean)
case class MyRoleJsonV700(
  role_name: String,
  bank_id: String,
  caller: MyRoleCallerJsonV700,
  held_by: MyRoleHeldByJsonV700,
  scopes_required: Boolean,
  allowed_for_auth_modes: List[String]
)

/**
 * One Role, as the caller holds it: whether the calling User holds it, whether the calling Consumer holds it
 * as a Scope, and so for which endpoint auth modes an endpoint that needs this Role would let the caller in.
 *
 * A service in front of OBP-API (API Explorer II, the Portal, API Manager) forwards the token it was given
 * and gets OBP-API's own answer, instead of copying OBP-API's access rules.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700MyRoles {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val authModes: List[EndpointAuthMode] = List(UserOnly, ApplicationOnly, UserOrApplication, UserAndApplication)

  /** The same decision as handleAccessControlWithAuthMode, for each auth mode, without granting
   *  just-in-time Entitlements: a GET must not create them. */
  private def myRole(roleName: String, bankId: String, cc: CallContext): Future[MyRoleJsonV700] =
    for {
      consumer <- Future(cc.consumer match {
        case Full(c) => Full(c)
        case _ => Empty
      }).map(unboxFullOrFail(_, Some(cc), ApplicationNotIdentified, 401))
      role <- NewStyle.function.tryons(s"$IncorrectRoleName $roleName", 404, Some(cc)) { ApiRole.valueOf(roleName) }
      // A system Role is asked for at SYS, which for it means no bank. A bank Role at SYS is held in the system space.
      _ <- Helper.booleanToFuture(failMsg = s"$EntitlementIsSystemRole Use $DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID as BANK_ID.",
        cc = Some(cc))(role.requiresBankId || bankId == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
    } yield {
      val heldAt = if (role.requiresBankId) bankId else ""
      val user = cc.user.toOption
      val userId = user.map(_.userId).getOrElse("")
      // A client-credentials token still brings a pseudo-user, named after the Consumer's client id.
      val applicationOnly = user.forall(u => Set(Option(consumer.key.get), Option(consumer.azp.get)).flatten.contains(u.idGivenByProvider))
      val virtualRoles = if (userId.isEmpty) Nil
                         else if (isSuperAdmin(userId)) superAdminVirtualRoles
                         else if (isOidcOperator(userId)) oidcOperatorVirtualRoles
                         else Nil
      val virtual = virtualRoles.contains(role.toString)
      val heldByUser = virtual || (userId.nonEmpty && hasEntitlement(heldAt, userId, role))
      val heldByConsumer = hasScope(heldAt, consumer.id.get.toString, role)
      val listed = getPropsValue("require_scopes_for_listed_roles", "").split(",").contains(role.toString)
      val scopesRequired = ApiPropsWithAlias.requireScopesForAllRoles || listed
      val allowed = authModes.filter { mode =>
        virtual || (if (scopesRequired) heldByUser && heldByConsumer else mode match {
          case UserOnly => heldByUser
          case ApplicationOnly => heldByConsumer
          case UserOrApplication => heldByUser || heldByConsumer
          case UserAndApplication => heldByUser && heldByConsumer
        })
      }
      MyRoleJsonV700(role.toString, bankId, MyRoleCallerJsonV700(userId, consumer.consumerId.get, applicationOnly),
        MyRoleHeldByJsonV700(heldByUser, heldByConsumer), scopesRequired, allowed.map(_.toString))
    }

  // Route: GET /obp/v7.0.0/banks/BANK_ID/my/roles/ROLE_NAME (BANK_ID SYS for system Roles)
  lazy val getMyRole: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "banks" / bankId / "my" / "roles" / roleName =>
      EndpointHelpers.executeFuture(req) { myRole(roleName, bankId, req.callContext) }
  }

  private val example = MyRoleJsonV700(
    role_name = "CanGetSystemLogCacheError",
    bank_id = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID,
    caller = MyRoleCallerJsonV700(user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", consumer_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh", application_only = true),
    held_by = MyRoleHeldByJsonV700(user = false, consumer = true),
    scopes_required = false,
    allowed_for_auth_modes = List("ApplicationOnly", "UserOrApplication")
  )

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyRole),
    "GET",
    "/banks/BANK_ID/my/roles/ROLE_NAME",
    "Get My Role",
    s"""Returns how the caller holds one Role, and so whether an endpoint that needs it would let the caller in.
       |
       |For a system Role, use `$DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID` as BANK_ID: it then means no bank. A bank Role is looked up at
       |BANK_ID, where `$DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID` is the system space of Dynamic Entities.
       |
       |Use it from a service that receives an OBP-API token (for example API Explorer II, the Portal or API Manager)
       |and must decide, as OBP-API would, whether that token may see something the service guards with an OBP Role.
       |
       |* `caller`: the User and Consumer of the token. `application_only` is true when no person is behind it
       |  (a client-credentials token, whose User is a pseudo-user named after the Consumer's client id).
       |* `held_by.user`: the User holds the Role as an Entitlement, or as a virtual Role from `super_admin_user_ids` or
       |  `oidc_operator_user_ids`.
       |* `held_by.consumer`: the Consumer holds the Role as a Scope.
       |* `scopes_required`: the props `require_scopes_for_all_roles` or `require_scopes_for_listed_roles` make
       |  every endpoint need both.
       |* `allowed_for_auth_modes`: the endpoint auth modes (`UserOnly`, `ApplicationOnly`, `UserOrApplication`,
       |  `UserAndApplication`) under which an endpoint that needs this Role would let the caller in.
       |  An endpoint that accepts several Roles lets the caller in if any one of them is allowed.
       |
       |Just-in-time Entitlements (`create_just_in_time_entitlements`) are not granted here: an endpoint may still
       |grant one when it is called.
       |
       |No Role is required: a caller may always learn what it holds. The caller must be identifiable as a Consumer.
       |An unknown Role name gets 404.
       |""".stripMargin,
    EmptyBody,
    example,
    List(ApplicationNotIdentified, BankNotFound, IncorrectRoleName, EntitlementIsSystemRole, UnknownError),
    List(apiTagRole, apiTagEntitlement, apiTagScope),
    None,
    authMode = UserOrApplication,
    http4sPartialFunction = Some(getMyRole)
  ).allowSystemSpace()
}
