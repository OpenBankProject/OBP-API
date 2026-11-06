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
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc, unboxFullOrFail}
import code.api.util.ApiRole.{canCreateTenant, canDeleteTenant, canGetTenant, canUpdateTenant}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle}
import code.tenant.{TenantTrait, Tenants}
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
 * This object holds the v7.0.0 Tenant endpoints. A Tenant is a customer of the operator of this OBP instance: an
 * organisation that uses it. Tenants belong to no bank, so their Roles are system Roles; later, banks will name the
 * Tenant they belong to.
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700Tenants {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val ValidTenantStatuses = Set("active", "suspended", "archived")
  private val ValidTenantVisibilities = Set("public", "unlisted", "private")
  private val TenantIdRegex = "^[a-zA-Z0-9._-]{2,64}$".r

  private val tenantDescription =
    """A Tenant is a customer of the operator of this OBP instance: an organisation that uses it. Tenants belong to no
      |bank, and their Roles are system Roles. The tenant_id is unique and immutable.""".stripMargin

  private def tenantOf(tenantId: String, cc: CallContext): Future[TenantTrait] =
    Future(Tenants.tenant.vend.getTenant(tenantId)).map(unboxFullOrFail(_, Some(cc), TenantNotFound, 404))

  // Route: POST /obp/v7.0.0/tenants (201)
  lazy val createTenant: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "tenants" =>
      EndpointHelpers.withUserAndBodyCreated[PostTenantJsonV700, TenantJsonV700](req) { (user, body, cc) =>
        val status = body.status.getOrElse("active")
        val visibility = body.visibility.getOrElse("public")
        for {
          _ <- Helper.booleanToFuture(InvalidTenantIdFormat, 400, Some(cc))(TenantIdRegex.findFirstIn(body.tenant_id).isDefined)
          _ <- Helper.booleanToFuture(InvalidTenantStatus, 400, Some(cc))(ValidTenantStatuses.contains(status))
          _ <- Helper.booleanToFuture(InvalidTenantVisibility, 400, Some(cc))(ValidTenantVisibilities.contains(visibility))
          existing <- Future(Tenants.tenant.vend.getTenant(body.tenant_id))
          _ <- Helper.booleanToFuture(TenantAlreadyExists, 409, Some(cc))(existing.isEmpty)
          created <- Future {
            Tenants.tenant.vend.createTenant(body.tenant_id, body.name, body.website, body.logo_url, status, visibility, user.userId)
          }.map(unboxFullOrFail(_, Some(cc), CreateTenantError, 400))
        } yield JSONFactory700Tenants.createTenantJsonV700(created)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createTenant),
    "POST",
    "/tenants",
    "Create Tenant",
    s"""Create a Tenant.
       |
       |$tenantDescription
       |
       |The tenant_id must be a URL-safe string (a-z, A-Z, 0-9, '-', '.', '_'), between 2 and 64 characters in length.
       |
       |Optional fields:
       |- status: one of active, suspended, archived (defaults to active)
       |- visibility: one of public, unlisted, private (defaults to public)
       |- website, logo_url
       |
       |Authentication is Required.""".stripMargin,
    PostTenantJsonV700("tesobe", "TESOBE GmbH", Some("https://www.tesobe.com"), None, Some("active"), Some("public")),
    JSONFactory700Tenants.tenantJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidTenantIdFormat, InvalidTenantStatus,
      InvalidTenantVisibility, TenantAlreadyExists, CreateTenantError, UnknownError),
    apiTagTenant :: Nil,
    Some(List(canCreateTenant)),
    http4sPartialFunction = Some(createTenant)
  )

  // Route: GET /obp/v7.0.0/tenants
  lazy val getTenants: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "tenants" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          tenants <- Tenants.tenant.vend.getTenants().map(unboxFullOrFail(_, Some(cc), UnknownError, 500))
          hasGetRole = APIUtil.hasAtLeastOneEntitlement("", user.userId, List(canGetTenant))
        } yield JSONFactory700Tenants.createTenantsJsonV700(if (hasGetRole) tenants else tenants.filter(_.visibility == "public"))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTenants),
    "GET",
    "/tenants",
    "Get Tenants",
    s"""Returns the Tenants.
       |
       |$tenantDescription
       |
       |By default returns only Tenants whose visibility is `public`. Users with the Role CanGetTenant see all of them,
       |including unlisted and private ones.
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    TenantsJsonV700(List(JSONFactory700Tenants.tenantJsonV700Example)),
    List($AuthenticatedUserIsRequired, UnknownError),
    apiTagTenant :: Nil,
    None,
    http4sPartialFunction = Some(getTenants)
  )

  // Route: GET /obp/v7.0.0/tenants/TENANT_ID
  lazy val getTenant: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "tenants" / tenantId =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          tenant <- tenantOf(tenantId, cc)
          _ <- if (tenant.visibility == "private") NewStyle.function.hasEntitlement("", user.userId, canGetTenant, Some(cc))
               else Future.successful(())
        } yield JSONFactory700Tenants.createTenantJsonV700(tenant)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTenant),
    "GET",
    "/tenants/TENANT_ID",
    "Get Tenant",
    s"""Returns the Tenant TENANT_ID.
       |
       |$tenantDescription
       |
       |Tenants with visibility `public` or `unlisted` are visible to any authenticated user. Tenants with visibility
       |`private` need the Role CanGetTenant.
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Tenants.tenantJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, TenantNotFound, UnknownError),
    apiTagTenant :: Nil,
    None,
    http4sPartialFunction = Some(getTenant)
  )

  // Route: PUT /obp/v7.0.0/tenants/TENANT_ID
  lazy val updateTenant: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "tenants" / tenantId =>
      EndpointHelpers.withUserAndBody[PutTenantJsonV700, TenantJsonV700](req) { (_, body, cc) =>
        for {
          _ <- tenantOf(tenantId, cc)
          _ <- Helper.booleanToFuture(InvalidTenantStatus, 400, Some(cc))(body.status.forall(ValidTenantStatuses.contains))
          _ <- Helper.booleanToFuture(InvalidTenantVisibility, 400, Some(cc))(body.visibility.forall(ValidTenantVisibilities.contains))
          updated <- Future {
            Tenants.tenant.vend.updateTenant(tenantId, body.name, body.website, body.logo_url, body.status, body.visibility)
          }.map(unboxFullOrFail(_, Some(cc), UpdateTenantError, 400))
        } yield JSONFactory700Tenants.createTenantJsonV700(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateTenant),
    "PUT",
    "/tenants/TENANT_ID",
    "Update Tenant",
    s"""Update the Tenant TENANT_ID. All body fields are optional; the tenant_id is immutable.
       |
       |$tenantDescription
       |
       |Authentication is Required.""".stripMargin,
    PutTenantJsonV700(Some("TESOBE GmbH"), Some("https://www.tesobe.com"), None, Some("active"), Some("public")),
    JSONFactory700Tenants.tenantJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, TenantNotFound, InvalidTenantStatus,
      InvalidTenantVisibility, UpdateTenantError, UnknownError),
    apiTagTenant :: Nil,
    Some(List(canUpdateTenant)),
    http4sPartialFunction = Some(updateTenant)
  )

  // Route: DELETE /obp/v7.0.0/tenants/TENANT_ID (204)
  lazy val deleteTenant: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "tenants" / tenantId =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        for {
          _ <- tenantOf(tenantId, cc)
          _ <- Future(Tenants.tenant.vend.deleteTenant(tenantId)).map(unboxFullOrFail(_, Some(cc), DeleteTenantError, 400))
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteTenant),
    "DELETE",
    "/tenants/TENANT_ID",
    "Delete Tenant",
    s"""Delete the Tenant TENANT_ID.
       |
       |$tenantDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, TenantNotFound, DeleteTenantError, UnknownError),
    apiTagTenant :: Nil,
    Some(List(canDeleteTenant)),
    http4sPartialFunction = Some(deleteTenant)
  )
}
