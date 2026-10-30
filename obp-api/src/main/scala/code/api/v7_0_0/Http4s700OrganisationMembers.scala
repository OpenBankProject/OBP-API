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

import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc, unboxFullOrFail}
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, CallContext, CustomJsonFormats}
import code.organisation.{OrganisationMemberTrait, OrganisationMembers, OrganisationTrait, Organisations}
import code.users.{UserReference, Users}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoints for the members of an Organisation, and Get My Organisations.
 *
 * Who may do what:
 * - the Roles, granted at the Organisation's space (BANK_ID, or SYS), allow everything their names say;
 * - an active member whose member_role is owner or admin manages the members without a Role;
 * - any active member may read the member list;
 * - a member may accept their own invitation, leave, and change their own title and is_primary.
 * So the ResourceDocs list the Roles but disable their automatic check, and each handler decides.
 *
 * The caller is matched as a member by the person it acts for, so an agent acting for an owner manages
 * as that owner.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700OrganisationMembers {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val MaxTitleLength = 255

  private def members = OrganisationMembers.organisationMember.vend

  /** The person the caller acts for: the caller itself unless it is an agent acting under a Consent. */
  private def personOf(user: User): String =
    Users.users.vend.attributionOf(user.userId, UserReference.OrganisationMember_CreatedByUserId)
      .map(_.onBehalfOfUserId).openOr(user.userId)

  private def usernameOf(userId: String): String =
    Users.users.vend.getUserByUserId(userId).map(_.name).openOr("")

  private def json(m: OrganisationMemberTrait, bankId: String): OrganisationMemberJsonV700 =
    JSONFactory700OrganisationMembers.createOrganisationMemberJson(m, bankId, usernameOf(m.userId))

  private def organisationInSpace(bankIdInUrl: String, organisationId: String, cc: CallContext): Future[OrganisationTrait] =
    Future(Organisations.organisation.vend.getOrganisation(bankIdInUrl, organisationId))
      .map(unboxFullOrFail(_, Some(cc), OrganisationNotFound, 404))

  private def memberOf(organisationId: String, userId: String, cc: CallContext): Future[OrganisationMemberTrait] =
    Future(members.getMember(organisationId, userId))
      .map(unboxFullOrFail(_, Some(cc), s"$OrganisationMemberNotFound Current USER_ID is $userId", 404))

  private def activeMembership(organisationId: String, personId: String): Option[OrganisationMemberTrait] =
    members.getMember(organisationId, personId).toOption.filter(_.status == "active")

  private def managesMembers(organisationId: String, personId: String): Boolean =
    activeMembership(organisationId, personId).exists(m => OrganisationMembers.ManagingMemberRoles.contains(m.memberRole))

  private def holds(bankIdInUrl: String, user: User, role: ApiRole): Boolean =
    APIUtil.hasAtLeastOneEntitlement(bankIdInUrl, user.userId, List(role))

  private def missing(role: ApiRole, alternative: String): String =
    s"$UserHasMissingRoles $role at BANK_ID, or $alternative."

  private def allowedOr403(message: String, cc: CallContext)(allowed: => Boolean): Future[Unit] =
    Helper.booleanToFuture(message, 403, Some(cc))(allowed).map(_ => ())

  private def checkValues(memberRole: Option[String], status: Option[String], title: Option[String], cc: CallContext): Future[Unit] =
    for {
      _ <- Helper.booleanToFuture(InvalidOrganisationMemberRole, 400, Some(cc))(memberRole.forall(OrganisationMembers.ValidMemberRoles.contains))
      _ <- Helper.booleanToFuture(InvalidOrganisationMemberStatus, 400, Some(cc))(status.forall(OrganisationMembers.ValidStatuses.contains))
      _ <- Helper.booleanToFuture(InvalidOrganisationMemberTitle, 400, Some(cc))(title.forall(_.length <= MaxTitleLength))
    } yield ()

  private val membersDescription =
    s"""A membership links a User to an Organisation, in the Organisation's space (BANK_ID is a bank, or SYS). It grants no
       |Roles: it says who belongs to the Organisation and as what (member_role: owner, admin or member), and apps decide what
       |that lets a member do. status is invited, active, suspended or left; leaving sets left rather than deleting the row.
       |
       |An active owner or admin member manages the members without a Role, and any active member may read the member
       |list. The Roles, granted at BANK_ID, allow the same for anyone holding them.
       |
       |created_by_user_id and updated_by_user_id are whoever made the call, an agent's own id when an agent made it;
       |created_by_on_behalf_of_user_id and updated_by_on_behalf_of_user_id are the person it was made for.""".stripMargin

  // Route: POST /obp/v7.0.0/banks/BANK_ID/organisations/ORGANISATION_ID/members (201)
  lazy val createOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / bankIdInUrl / "organisations" / organisationId / "members" =>
      EndpointHelpers.withUserAndBodyCreated[PostOrganisationMemberJsonV700, OrganisationMemberJsonV700](req) { (user, body, cc) =>
        val memberUserId = Option(body.user_id).map(_.trim).getOrElse("")
        val memberRole = body.member_role.getOrElse("member")
        val status = body.status.getOrElse("active")
        for {
          org <- organisationInSpace(bankIdInUrl, organisationId, cc)
          _ <- allowedOr403(missing(canCreateOrganisationMember, "be an active owner or admin member of the Organisation"), cc) {
            holds(bankIdInUrl, user, canCreateOrganisationMember) || managesMembers(org.organisationId, personOf(user))
          }
          _ <- checkValues(Some(memberRole), Some(status), body.title, cc)
          member <- Future(Users.users.vend.getUserByUserId(memberUserId))
            .map(unboxFullOrFail(_, Some(cc), s"$UserNotFoundByUserId Current user_id is $memberUserId", 404))
          // Explicit target: fail loud rather than redirect (see the entitlement endpoints).
          _ <- Helper.booleanToFuture(
            s"$InvalidUserId user_id names a consent user (an agent identity minted by a Consent). A member of an Organisation is a person - use their USER_ID.",
            400, Some(cc))(!member.isConsentUser)
          _ <- Helper.booleanToFuture(s"$OrganisationMemberAlreadyExists Current user_id is $memberUserId.", 409, Some(cc)) {
            members.getMember(org.organisationId, memberUserId).isEmpty
          }
          created <- Future(members.createMember(org.organisationId, memberUserId, memberRole, body.title, status,
            body.is_primary.getOrElse(false), user.userId))
            .map(unboxFullOrFail(_, Some(cc), CreateOrganisationMemberError, 400))
        } yield json(created, bankIdInUrl)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createOrganisationMember),
    "POST",
    "/banks/BANK_ID/organisations/ORGANISATION_ID/members",
    "Create Organisation Member",
    s"""Add a User to an Organisation. member_role defaults to member and status to active; send status invited for the
       |User to accept by setting it to active themselves. is_primary true makes this the User's primary Organisation and
       |clears the flag on their others. The User must be a person: a consent user is refused.
       |
       |$membersDescription""".stripMargin,
    JSONFactory700OrganisationMembers.postOrganisationMemberJsonV700Example,
    JSONFactory700OrganisationMembers.organisationMemberJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, OrganisationNotFound,
      InvalidOrganisationMemberRole, InvalidOrganisationMemberStatus, InvalidOrganisationMemberTitle, UserNotFoundByUserId,
      InvalidUserId, OrganisationMemberAlreadyExists, CreateOrganisationMemberError, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canCreateOrganisationMember)),
    http4sPartialFunction = Some(createOrganisationMember)
  ).allowSystemSpace().disableAutoValidateRoles()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/organisations/ORGANISATION_ID/members
  lazy val getOrganisationMembers: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "organisations" / organisationId / "members" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          org <- organisationInSpace(bankIdInUrl, organisationId, cc)
          _ <- allowedOr403(missing(canGetOrganisationMembers, "be an active member of the Organisation"), cc) {
            holds(bankIdInUrl, user, canGetOrganisationMembers) || activeMembership(org.organisationId, personOf(user)).isDefined
          }
          all <- Future(members.getMembers(org.organisationId)).map(unboxFullOrFail(_, Some(cc), UnknownError, 500))
        } yield OrganisationMembersJsonV700(all.map(json(_, bankIdInUrl)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOrganisationMembers),
    "GET",
    "/banks/BANK_ID/organisations/ORGANISATION_ID/members",
    "Get Organisation Members",
    s"""The members of an Organisation, whatever their status.
       |
       |$membersDescription""".stripMargin,
    EmptyBody,
    JSONFactory700OrganisationMembers.organisationMembersJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canGetOrganisationMembers)),
    http4sPartialFunction = Some(getOrganisationMembers)
  ).allowSystemSpace().disableAutoValidateRoles()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/organisations/ORGANISATION_ID/members/USER_ID
  lazy val getOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "organisations" / organisationId / "members" / userIdInUrl =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          org <- organisationInSpace(bankIdInUrl, organisationId, cc)
          person = personOf(user)
          _ <- allowedOr403(missing(canGetOrganisationMembers, "be an active member of the Organisation, or this member"), cc) {
            holds(bankIdInUrl, user, canGetOrganisationMembers) || person == userIdInUrl ||
              activeMembership(org.organisationId, person).isDefined
          }
          member <- memberOf(org.organisationId, userIdInUrl, cc)
        } yield json(member, bankIdInUrl)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOrganisationMember),
    "GET",
    "/banks/BANK_ID/organisations/ORGANISATION_ID/members/USER_ID",
    "Get Organisation Member",
    s"""One member of an Organisation. A User may always read their own membership.
       |
       |$membersDescription""".stripMargin,
    EmptyBody,
    JSONFactory700OrganisationMembers.organisationMemberJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound, OrganisationMemberNotFound,
      UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canGetOrganisationMembers)),
    http4sPartialFunction = Some(getOrganisationMember)
  ).allowSystemSpace().disableAutoValidateRoles()

  // Route: PUT /obp/v7.0.0/banks/BANK_ID/organisations/ORGANISATION_ID/members/USER_ID
  lazy val updateOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "banks" / bankIdInUrl / "organisations" / organisationId / "members" / userIdInUrl =>
      EndpointHelpers.withUserAndBody[PutOrganisationMemberJsonV700, OrganisationMemberJsonV700](req) { (user, body, cc) =>
        for {
          org <- organisationInSpace(bankIdInUrl, organisationId, cc)
          person = personOf(user)
          isManager = holds(bankIdInUrl, user, canUpdateOrganisationMember) || managesMembers(org.organisationId, person)
          isSelf = person == userIdInUrl
          _ <- allowedOr403(missing(canUpdateOrganisationMember, "be an active owner or admin member of the Organisation, or this member"), cc) {
            isManager || isSelf
          }
          member <- memberOf(org.organisationId, userIdInUrl, cc)
          _ <- checkValues(body.member_role, body.status, body.title, cc)
          // A member on their own may accept an invitation or leave, and change their title and is_primary.
          _ <- allowedOr403(OrganisationMemberSelfChangeNotAllowed, cc) {
            isManager || (body.member_role.isEmpty && body.status.forall {
              case "left"   => true
              case "active" => member.status == "invited" || member.status == "active"
              case other    => other == member.status
            })
          }
          updated <- Future(members.updateMember(org.organisationId, userIdInUrl, body.member_role, body.title,
            body.status, body.is_primary, user.userId))
            .map(unboxFullOrFail(_, Some(cc), CreateOrganisationMemberError, 400))
        } yield json(updated, bankIdInUrl)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateOrganisationMember),
    "PUT",
    "/banks/BANK_ID/organisations/ORGANISATION_ID/members/USER_ID",
    "Update Organisation Member",
    s"""Change a membership. All body fields are optional. A member may, on their own, accept an invitation (status active),
       |leave (status left) and change their title and is_primary; changing member_role or any other status needs an owner or
       |admin member, or the Role.
       |
       |$membersDescription""".stripMargin,
    JSONFactory700OrganisationMembers.putOrganisationMemberJsonV700Example,
    JSONFactory700OrganisationMembers.organisationMemberJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, OrganisationNotFound,
      OrganisationMemberNotFound, InvalidOrganisationMemberRole, InvalidOrganisationMemberStatus,
      InvalidOrganisationMemberTitle, OrganisationMemberSelfChangeNotAllowed, CreateOrganisationMemberError, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canUpdateOrganisationMember)),
    http4sPartialFunction = Some(updateOrganisationMember)
  ).allowSystemSpace().disableAutoValidateRoles()

  // Route: DELETE /obp/v7.0.0/banks/BANK_ID/organisations/ORGANISATION_ID/members/USER_ID (204)
  lazy val deleteOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "banks" / bankIdInUrl / "organisations" / organisationId / "members" / userIdInUrl =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        for {
          org <- organisationInSpace(bankIdInUrl, organisationId, cc)
          _ <- memberOf(org.organisationId, userIdInUrl, cc)
          _ <- Future(members.deleteMember(org.organisationId, userIdInUrl))
            .map(unboxFullOrFail(_, Some(cc), CreateOrganisationMemberError, 400))
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteOrganisationMember),
    "DELETE",
    "/banks/BANK_ID/organisations/ORGANISATION_ID/members/USER_ID",
    "Delete Organisation Member",
    s"""Delete a membership row, for correcting mistakes. A member leaving is recorded by setting status to left instead,
       |which keeps the history.
       |
       |$membersDescription""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound, OrganisationMemberNotFound,
      UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canDeleteOrganisationMember)),
    http4sPartialFunction = Some(deleteOrganisationMember)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/my/organisations
  lazy val getMyOrganisations: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "organisations" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          memberships <- Future(members.getMembershipsOfUser(personOf(user)))
            .map(unboxFullOrFail(_, Some(cc), UnknownError, 500))
        } yield MyOrganisationsJsonV700(memberships.flatMap { m =>
          Organisations.organisation.vend.getOrganisation(m.organisationId).toOption.map { org =>
            MyOrganisationJsonV700(JSONFactory700.createOrganisationJsonV700(org), json(m, org.bankId))
          }
        })
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyOrganisations),
    "GET",
    "/my/organisations",
    "Get My Organisations",
    s"""The Organisations the current User is a member of, in every space, each with the User's membership and whatever its
       |status. When an agent calls under a Consent, these are the memberships of the person it acts for.
       |
       |$membersDescription""".stripMargin,
    EmptyBody,
    JSONFactory700OrganisationMembers.myOrganisationsJsonV700Example,
    List($AuthenticatedUserIsRequired, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyOrganisations)
  )
}
