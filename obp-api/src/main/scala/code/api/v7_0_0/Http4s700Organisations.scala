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
import code.api.util.ApiRole.{canCreateOrganisationAtOneBank, canGetOrganisationAtOneBank, canUpdateOrganisationStatusAtOneBank}
import code.api.util.{APIUtil, ApiRole}
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CallContext, CustomJsonFormats, NewStyle, RateLimitingUtil}
import code.api.v6_0_0.JSONFactory600
import code.consumer.Consumers
import code.model.Consumer
import code.organisation.{ConsumerOwnership, OrganisationMemberTrait, OrganisationTrait, Organisations}
import code.users.{Users => UserVend}
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
 * The v7.0.0 Organisation endpoints, and the endpoints with which the owners of a Consumer read it.
 *
 * An Organisation is a company or team that uses the APIs, for example a fintech, and owns Consumers. Its
 * members may act for its Consumers without a Role; its admins also manage it. The rule is "/my/... is yours,
 * /management/... needs a Role". See todo/todo_organisations.md and todo/todo_consumer_owner_access.md.
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700Organisations {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val organisationDescription =
    """An Organisation is a company or team that uses the APIs, for example a fintech, and owns Consumers. Its
      |members are admins or members. Members may act for the Organisation's Consumers without a Role: read
      |them, their usage, Scopes and API Product Subscriptions, and subscribe them. Admins also change the
      |Organisation and its members, and move Consumers into or out of it.
      |
      |Its bank_id is the space that administers it, and staff Roles for Organisations are held there. An
      |Organisation you create for yourself is at SYS. Only staff with CanCreateOrganisationAtOneBank at a bank
      |create one there. The bank_id is not a legal relationship with that bank, which is what a Customer is. An
      |Organisation is not a Tenant either: a Tenant is a customer of the operator of this instance.""".stripMargin

  private val consumerOwnerDescription =
    """You may read a Consumer here if you created it and it belongs to no Organisation, or if it belongs to an
      |active Organisation you are a member of. Any other CONSUMER_ID answers 404. The consumer key and secret
      |are never returned.""".stripMargin

  private def validName(name: String): Boolean = name.trim.nonEmpty && name.trim.length <= 255

  /** SYS, or the id of a bank that exists. */
  private def organisationBankId(given: Option[String], cc: CallContext): Future[String] = {
    val bankId = given.map(_.trim).filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
    Helper.booleanToFuture(InvalidOrganisationBankId, 400, Some(cc))(APIUtil.isBankIdWhereRolesCanBeHeld(bankId, Some(cc)))
      .map(_ => bankId)
  }

  private def holdsAt(bankId: String, userId: String, role: ApiRole, cc: CallContext): Boolean =
    APIUtil.handleAccessControlRegardingEntitlementsAndScopes(bankId, userId, APIUtil.getConsumerPrimaryKey(Some(cc)), role :: Nil)

  /** Whether the Role is held at any bank_id, as an Entitlement of the User or a Scope of the calling Consumer. */
  private def holdsAnywhere(userId: String, role: ApiRole, cc: CallContext): Boolean = {
    val asEntitlement = code.entitlement.Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
      .map(_.exists(_.roleName == role.toString)).openOr(false)
    lazy val asScope = cc.consumer.toOption
      .flatMap(consumer => code.scope.Scope.scope.vend.getScopesByConsumerId(consumer.id.get.toString).toOption)
      .exists(_.exists(_.roleName == role.toString))
    asEntitlement || asScope
  }

  /** Staff Roles are checked at the Organisation's bank_id, which is not in the path. */
  private def staffRoleCheck(bankId: String, userId: String, role: ApiRole, cc: CallContext): Future[Box[Unit]] =
    Helper.booleanToFuture(s"$UserHasMissingRoles$role for BankId($bankId).", 403, Some(cc))(holdsAt(bankId, userId, role, cc))

  private def staffOrganisation(organisationId: String, userId: String, role: ApiRole, cc: CallContext): Future[OrganisationTrait] =
    for {
      organisation <- Future(Organisations.getOrganisation(organisationId)).map(unboxFullOrFail(_, Some(cc), OrganisationNotFound, 404))
      _ <- staffRoleCheck(organisation.bankId, userId, role, cc)
    } yield organisation

  /** The Organisation, if the User is a member of it; otherwise 404, so that it does not show it exists. */
  private def membershipOf(organisationId: String, userId: String, cc: CallContext): Future[(OrganisationTrait, OrganisationMemberTrait)] =
    Future {
      for {
        organisation <- Organisations.getOrganisation(organisationId)
        membership <- Organisations.getMember(organisationId, userId)
      } yield (organisation, membership)
    }.map(unboxFullOrFail(_, Some(cc), OrganisationNotFound, 404))

  private def requireAdmin(membership: OrganisationMemberTrait, cc: CallContext): Future[Box[Unit]] =
    Helper.booleanToFuture(OrganisationAdminRequired, 403, Some(cc))(membership.memberRole == Organisations.RoleAdmin)

  private type Box[T] = net.liftweb.common.Box[T]

  private def membersJson(organisationId: String): Future[OrganisationMembersJsonV700] = Future {
    OrganisationMembersJsonV700(Organisations.getMembers(organisationId).map { member =>
      JSONFactory700Organisations.createMemberJson(member, UserVend.users.vend.getUserByUserId(member.userId).toOption)
    })
  }

  /** The bank_id in the request body, if one was sent. */
  private def bankIdInBody(cc: CallContext): Option[String] =
    scala.util.Try(com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("{}")) \ "bank_id").toOption.collect {
      case org.json4s.JString(value) if value.trim.nonEmpty => value.trim
    }

  /** The Consumer, if the User may act for it; otherwise 404. */
  private def myConsumer(consumerId: String, userId: String, cc: CallContext): Future[Consumer] =
    Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId)
      .map(_.filter(consumer => ConsumerOwnership.canActFor(consumer, userId)))
      .map(unboxFullOrFail(_, Some(cc), MyConsumerNotFound, 404))

  // ─── The caller's Organisations ──────────────────────────────────────────

  // Route: POST /obp/v7.0.0/my/organisations (201)
  lazy val createMyOrganisation: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "my" / "organisations" =>
      EndpointHelpers.withUserAndBodyCreated[PostOrganisationJsonV700, MyOrganisationJsonV700](req) { (user, body, cc) =>
        for {
          _ <- Helper.booleanToFuture(InvalidOrganisationName, 400, Some(cc))(validName(Option(body.name).getOrElse("")))
          // A bank_id other than SYS is refused rather than ignored, so the caller knows where the Organisation is.
          _ <- Helper.booleanToFuture(MyOrganisationBankIdNotAllowed, 400, Some(cc))(bankIdInBody(cc).forall(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID))
          organisation <- Future {
            Organisations.createOrganisation(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, body.name.trim, body.description.getOrElse(""), body.website, user.userId, user.userId)
          }.map(unboxFullOrFail(_, Some(cc), CreateOrganisationError, 400))
          membership <- Future(Organisations.getMember(organisation.organisationId, user.userId))
            .map(unboxFullOrFail(_, Some(cc), CreateOrganisationError, 400))
        } yield JSONFactory700Organisations.createMyOrganisationJson(organisation, membership)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createMyOrganisation),
    "POST",
    "/my/organisations",
    "Create My Organisation",
    s"""Create an Organisation. You become its first admin. No Role is needed.
       |
       |The Organisation is at SYS: staff with Roles at SYS administer it. To create an Organisation at a bank, staff
       |with CanCreateOrganisationAtOneBank there use Create Organisation (POST /management/organisations).
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    PostOrganisationJsonV700("Example Fintech Ltd", Some("Builds payment apps."), Some("https://fintech.example.com")),
    JSONFactory700Organisations.myOrganisationJsonV700Example,
    List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidOrganisationName, MyOrganisationBankIdNotAllowed, CreateOrganisationError, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(createMyOrganisation)
  )

  // Route: GET /obp/v7.0.0/my/organisations
  lazy val getMyOrganisations: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "organisations" =>
      EndpointHelpers.withUser(req) { (user, _) =>
        Future {
          MyOrganisationsJsonV700(Organisations.getOrganisationsOfUser(user.userId).map { case (organisation, membership) =>
            JSONFactory700Organisations.createMyOrganisationJson(organisation, membership)
          })
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyOrganisations),
    "GET",
    "/my/organisations",
    "Get My Organisations",
    s"""Get the Organisations you are a member of, each with your member_role in it.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    MyOrganisationsJsonV700(List(JSONFactory700Organisations.myOrganisationJsonV700Example)),
    List($AuthenticatedUserIsRequired, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyOrganisations)
  )

  // Route: GET /obp/v7.0.0/my/organisations/ORGANISATION_ID
  lazy val getMyOrganisation: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "organisations" / organisationId =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        membershipOf(organisationId, user.userId, cc).map { case (organisation, membership) =>
          JSONFactory700Organisations.createMyOrganisationJson(organisation, membership)
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyOrganisation),
    "GET",
    "/my/organisations/ORGANISATION_ID",
    "Get My Organisation",
    s"""Get one of your Organisations. An Organisation you are not a member of answers 404.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Organisations.myOrganisationJsonV700Example,
    List($AuthenticatedUserIsRequired, OrganisationNotFound, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyOrganisation)
  )

  // Route: PUT /obp/v7.0.0/my/organisations/ORGANISATION_ID
  lazy val updateMyOrganisation: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "my" / "organisations" / organisationId =>
      EndpointHelpers.withUserAndBody[PutOrganisationJsonV700, MyOrganisationJsonV700](req) { (user, body, cc) =>
        for {
          (_, membership) <- membershipOf(organisationId, user.userId, cc)
          _ <- requireAdmin(membership, cc)
          _ <- Helper.booleanToFuture(InvalidOrganisationName, 400, Some(cc))(body.name.forall(validName))
          updated <- Future(Organisations.updateOrganisation(organisationId, body.name.map(_.trim), body.description, body.website))
            .map(unboxFullOrFail(_, Some(cc), UpdateOrganisationError, 400))
        } yield JSONFactory700Organisations.createMyOrganisationJson(updated, membership)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateMyOrganisation),
    "PUT",
    "/my/organisations/ORGANISATION_ID",
    "Update My Organisation",
    s"""Change the name, description or website of an Organisation. Only its admins may. Fields left out are
       |not changed.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    PutOrganisationJsonV700(Some("Example Fintech Ltd"), Some("Builds payment apps."), Some("https://fintech.example.com")),
    JSONFactory700Organisations.myOrganisationJsonV700Example,
    List($AuthenticatedUserIsRequired, InvalidJsonFormat, OrganisationNotFound, OrganisationAdminRequired, InvalidOrganisationName,
      UpdateOrganisationError, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(updateMyOrganisation)
  )

  // Route: DELETE /obp/v7.0.0/my/organisations/ORGANISATION_ID (204)
  lazy val deleteMyOrganisation: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "my" / "organisations" / organisationId =>
      EndpointHelpers.withUserDelete(req) { (user, cc) =>
        for {
          (_, membership) <- membershipOf(organisationId, user.userId, cc)
          _ <- requireAdmin(membership, cc)
          _ <- Helper.booleanToFuture(OrganisationHasConsumers, 409, Some(cc))(ConsumerOwnership.consumerCount(organisationId) == 0)
          _ <- Future(Organisations.deleteOrganisation(organisationId)).map(unboxFullOrFail(_, Some(cc), DeleteOrganisationError, 400))
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteMyOrganisation),
    "DELETE",
    "/my/organisations/ORGANISATION_ID",
    "Delete My Organisation",
    s"""Delete an Organisation and its memberships. Only its admins may, and only when no Consumer belongs to it.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($AuthenticatedUserIsRequired, OrganisationNotFound, OrganisationAdminRequired, OrganisationHasConsumers,
      DeleteOrganisationError, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(deleteMyOrganisation)
  )

  // ─── Members ─────────────────────────────────────────────────────────────

  // Route: GET /obp/v7.0.0/my/organisations/ORGANISATION_ID/members
  lazy val getMyOrganisationMembers: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "organisations" / organisationId / "members" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          _ <- membershipOf(organisationId, user.userId, cc)
          members <- membersJson(organisationId)
        } yield members
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyOrganisationMembers),
    "GET",
    "/my/organisations/ORGANISATION_ID/members",
    "Get My Organisation Members",
    s"""Get the members of one of your Organisations, with their member_role.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    OrganisationMembersJsonV700(List(JSONFactory700Organisations.organisationMemberJsonV700Example)),
    List($AuthenticatedUserIsRequired, OrganisationNotFound, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyOrganisationMembers)
  )

  // Route: POST /obp/v7.0.0/my/organisations/ORGANISATION_ID/members (201)
  lazy val addMyOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "my" / "organisations" / organisationId / "members" =>
      EndpointHelpers.withUserAndBodyCreated[PostOrganisationMemberJsonV700, OrganisationMemberJsonV700](req) { (user, body, cc) =>
        for {
          (_, membership) <- membershipOf(organisationId, user.userId, cc)
          _ <- requireAdmin(membership, cc)
          _ <- Helper.booleanToFuture(InvalidOrganisationMemberRole, 400, Some(cc))(Organisations.ValidMemberRoles.contains(body.member_role))
          (newMember, _) <- NewStyle.function.findByUserId(body.user_id, Some(cc))
          _ <- Helper.booleanToFuture(OrganisationMemberAlreadyExists, 409, Some(cc))(Organisations.getMember(organisationId, newMember.userId).isEmpty)
          added <- Future(Organisations.addMember(organisationId, newMember.userId, body.member_role, user.userId))
            .map(unboxFullOrFail(_, Some(cc), UpdateOrganisationMemberError, 400))
        } yield JSONFactory700Organisations.createMemberJson(added, Some(newMember))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(addMyOrganisationMember),
    "POST",
    "/my/organisations/ORGANISATION_ID/members",
    "Add My Organisation Member",
    s"""Add a User to one of your Organisations, as admin or member. Only its admins may. The new member can
       |read their user_id from Get Current User.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    PostOrganisationMemberJsonV700("9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", "member"),
    JSONFactory700Organisations.organisationMemberJsonV700Example,
    List($AuthenticatedUserIsRequired, InvalidJsonFormat, OrganisationNotFound, OrganisationAdminRequired, InvalidOrganisationMemberRole,
      UserNotFoundById, OrganisationMemberAlreadyExists, UpdateOrganisationMemberError, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(addMyOrganisationMember)
  )

  // Route: PUT /obp/v7.0.0/my/organisations/ORGANISATION_ID/members/USER_ID
  lazy val updateMyOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "my" / "organisations" / organisationId / "members" / memberUserId =>
      EndpointHelpers.withUserAndBody[PutOrganisationMemberJsonV700, OrganisationMemberJsonV700](req) { (user, body, cc) =>
        for {
          (_, membership) <- membershipOf(organisationId, user.userId, cc)
          _ <- requireAdmin(membership, cc)
          _ <- Helper.booleanToFuture(InvalidOrganisationMemberRole, 400, Some(cc))(Organisations.ValidMemberRoles.contains(body.member_role))
          member <- Future(Organisations.getMember(organisationId, memberUserId)).map(unboxFullOrFail(_, Some(cc), OrganisationMemberNotFound, 404))
          demotesLastAdmin = member.memberRole == Organisations.RoleAdmin && body.member_role != Organisations.RoleAdmin &&
            Organisations.adminCount(organisationId) <= 1
          _ <- Helper.booleanToFuture(OrganisationNeedsAnAdmin, 400, Some(cc))(!demotesLastAdmin)
          updated <- Future(Organisations.updateMemberRole(organisationId, memberUserId, body.member_role))
            .map(unboxFullOrFail(_, Some(cc), UpdateOrganisationMemberError, 400))
        } yield JSONFactory700Organisations.createMemberJson(updated, UserVend.users.vend.getUserByUserId(memberUserId).toOption)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateMyOrganisationMember),
    "PUT",
    "/my/organisations/ORGANISATION_ID/members/USER_ID",
    "Update My Organisation Member",
    s"""Change a member's member_role to admin or member. Only admins may. The last admin cannot be made a member.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    PutOrganisationMemberJsonV700("admin"),
    JSONFactory700Organisations.organisationMemberJsonV700Example,
    List($AuthenticatedUserIsRequired, InvalidJsonFormat, OrganisationNotFound, OrganisationAdminRequired, InvalidOrganisationMemberRole,
      OrganisationMemberNotFound, OrganisationNeedsAnAdmin, UpdateOrganisationMemberError, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(updateMyOrganisationMember)
  )

  // Route: DELETE /obp/v7.0.0/my/organisations/ORGANISATION_ID/members/USER_ID (204)
  lazy val removeMyOrganisationMember: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "my" / "organisations" / organisationId / "members" / memberUserId =>
      EndpointHelpers.withUserDelete(req) { (user, cc) =>
        for {
          (_, membership) <- membershipOf(organisationId, user.userId, cc)
          // A member may leave on their own; removing anyone else takes an admin.
          _ <- if (memberUserId == user.userId) Future.successful(()) else requireAdmin(membership, cc)
          member <- Future(Organisations.getMember(organisationId, memberUserId)).map(unboxFullOrFail(_, Some(cc), OrganisationMemberNotFound, 404))
          removesLastAdmin = member.memberRole == Organisations.RoleAdmin && Organisations.adminCount(organisationId) <= 1
          _ <- Helper.booleanToFuture(OrganisationNeedsAnAdmin, 400, Some(cc))(!removesLastAdmin)
          _ <- Future(Organisations.removeMember(organisationId, memberUserId)).map(unboxFullOrFail(_, Some(cc), UpdateOrganisationMemberError, 400))
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(removeMyOrganisationMember),
    "DELETE",
    "/my/organisations/ORGANISATION_ID/members/USER_ID",
    "Remove My Organisation Member",
    s"""Remove a member from one of your Organisations. Admins may remove anyone; any member may remove
       |themselves to leave. The last admin cannot be removed.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($AuthenticatedUserIsRequired, OrganisationNotFound, OrganisationAdminRequired, OrganisationMemberNotFound,
      OrganisationNeedsAnAdmin, UpdateOrganisationMemberError, UnknownError),
    apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(removeMyOrganisationMember)
  )

  // ─── Staff ───────────────────────────────────────────────────────────────
  // Their Roles are held at the Organisation's bank_id (a bank, or SYS), which is not in the path, so the docs
  // declare the Roles for the catalogue and the handlers check them.

  // Route: POST /obp/v7.0.0/management/organisations (201)
  lazy val createOrganisation: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "organisations" =>
      EndpointHelpers.withUserAndBodyCreated[PostManagementOrganisationJsonV700, OrganisationJsonV700](req) { (user, body, cc) =>
        for {
          _ <- Helper.booleanToFuture(InvalidOrganisationName, 400, Some(cc))(validName(Option(body.name).getOrElse("")))
          bankId <- organisationBankId(body.bank_id, cc)
          _ <- staffRoleCheck(bankId, user.userId, canCreateOrganisationAtOneBank, cc)
          (firstAdmin, _) <- NewStyle.function.findByUserId(Option(body.admin_user_id).getOrElse(""), Some(cc))
          organisation <- Future {
            Organisations.createOrganisation(bankId, body.name.trim, body.description.getOrElse(""), body.website, firstAdmin.userId, user.userId)
          }.map(unboxFullOrFail(_, Some(cc), CreateOrganisationError, 400))
        } yield JSONFactory700Organisations.createOrganisationJson(organisation)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createOrganisation),
    "POST",
    "/management/organisations",
    "Create Organisation",
    s"""Create an Organisation for someone else and name its first admin, for example when a regulator registers
       |a participant. The Role is checked at the bank_id, which is SYS if left out. The first admin then adds the
       |rest of their team.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    PostManagementOrganisationJsonV700(Some("SYS"), "Example Fintech Ltd", Some("Builds payment apps."),
      Some("https://fintech.example.com"), "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"),
    JSONFactory700Organisations.organisationJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidOrganisationName, InvalidOrganisationBankId,
      UserNotFoundById, CreateOrganisationError, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canCreateOrganisationAtOneBank)),
    http4sPartialFunction = Some(createOrganisation)
  ).disableAutoValidateRoles()

  // Route: GET /obp/v7.0.0/management/organisations
  lazy val getOrganisations: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "organisations" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        val bankIdFilter = req.uri.query.params.get("bank_id").map(_.trim).filter(_.nonEmpty)
        for {
          organisations <- Future(Organisations.getOrganisations().filter(organisation => bankIdFilter.forall(_ == organisation.bankId)))
          allowedBankIds = organisations.map(_.bankId).distinct
            .filter(bankId => holdsAt(bankId, user.userId, canGetOrganisationAtOneBank, cc)).toSet
          // With a bank_id, the Role must be held there. Without one, the list is filtered to the banks where it is held.
          _ <- bankIdFilter match {
            case Some(bankId) => staffRoleCheck(bankId, user.userId, canGetOrganisationAtOneBank, cc)
            case None => Helper.booleanToFuture(s"$UserHasMissingRoles$canGetOrganisationAtOneBank at any bank_id.", 403, Some(cc)) {
              allowedBankIds.nonEmpty || holdsAnywhere(user.userId, canGetOrganisationAtOneBank, cc)
            }
          }
        } yield JSONFactory700Organisations.createOrganisationsJson(organisations.filter(organisation => allowedBankIds.contains(organisation.bankId)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOrganisations),
    "GET",
    "/management/organisations",
    "Get Organisations",
    s"""Get the Organisations at the bank_ids where you hold CanGetOrganisationAtOneBank. With the query
       |parameter bank_id, only those at that bank_id; the Role must then be held there.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    OrganisationsJsonV700(List(JSONFactory700Organisations.organisationJsonV700Example)),
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canGetOrganisationAtOneBank)),
    http4sPartialFunction = Some(getOrganisations)
  ).disableAutoValidateRoles()

  // Route: GET /obp/v7.0.0/management/organisations/ORGANISATION_ID
  lazy val getOrganisation: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "organisations" / organisationId =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        staffOrganisation(organisationId, user.userId, canGetOrganisationAtOneBank, cc).map(JSONFactory700Organisations.createOrganisationJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOrganisation),
    "GET",
    "/management/organisations/ORGANISATION_ID",
    "Get Organisation",
    s"""Get an Organisation. The Role is checked at the Organisation's bank_id.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Organisations.organisationJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canGetOrganisationAtOneBank)),
    http4sPartialFunction = Some(getOrganisation)
  ).disableAutoValidateRoles()

  // Route: GET /obp/v7.0.0/management/organisations/ORGANISATION_ID/members
  lazy val getOrganisationMembers: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "organisations" / organisationId / "members" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          organisation <- staffOrganisation(organisationId, user.userId, canGetOrganisationAtOneBank, cc)
          members <- membersJson(organisation.organisationId)
        } yield members
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOrganisationMembers),
    "GET",
    "/management/organisations/ORGANISATION_ID/members",
    "Get Organisation Members",
    s"""Get the members of an Organisation, with their member_role. The Role is checked at the Organisation's
       |bank_id. An application can use this to give the members access of its own, for example Roles in a space.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    OrganisationMembersJsonV700(List(JSONFactory700Organisations.organisationMemberJsonV700Example)),
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canGetOrganisationAtOneBank)),
    http4sPartialFunction = Some(getOrganisationMembers)
  ).disableAutoValidateRoles()

  // Route: PUT /obp/v7.0.0/management/organisations/ORGANISATION_ID/status
  lazy val updateOrganisationStatus: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "management" / "organisations" / organisationId / "status" =>
      EndpointHelpers.withUserAndBody[PutOrganisationStatusJsonV700, OrganisationJsonV700](req) { (user, body, cc) =>
        for {
          _ <- staffOrganisation(organisationId, user.userId, canUpdateOrganisationStatusAtOneBank, cc)
          _ <- Helper.booleanToFuture(InvalidOrganisationStatus, 400, Some(cc))(Organisations.ValidStatuses.contains(body.status))
          updated <- Future(Organisations.updateStatus(organisationId, body.status)).map(unboxFullOrFail(_, Some(cc), UpdateOrganisationError, 400))
        } yield JSONFactory700Organisations.createOrganisationJson(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateOrganisationStatus),
    "PUT",
    "/management/organisations/ORGANISATION_ID/status",
    "Update Organisation Status",
    s"""Set an Organisation to active or suspended. While it is suspended its members still see it, but they
       |cannot act for its Consumers. The Role is checked at the Organisation's bank_id.
       |
       |$organisationDescription
       |
       |Authentication is Required.""".stripMargin,
    PutOrganisationStatusJsonV700("suspended"),
    JSONFactory700Organisations.organisationJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidOrganisationStatus, OrganisationNotFound,
      UpdateOrganisationError, UnknownError),
    apiTagOrganisation :: Nil,
    Some(List(canUpdateOrganisationStatusAtOneBank)),
    http4sPartialFunction = Some(updateOrganisationStatus)
  ).disableAutoValidateRoles()

  // ─── The caller's Consumers ──────────────────────────────────────────────

  // Route: GET /obp/v7.0.0/my/consumers
  lazy val getMyConsumers: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "consumers" =>
      EndpointHelpers.withUser(req) { (user, _) =>
        ConsumerOwnership.consumersFor(user.userId)
          .map(consumers => MyConsumersJsonV700(consumers.map(JSONFactory700Organisations.createMyConsumerJson)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyConsumers),
    "GET",
    "/my/consumers",
    "Get My Consumers",
    s"""Get the Consumers you may act for. These are the ones you created that belong to no Organisation, and
       |those of the active Organisations you are a member of. No Role is needed.
       |
       |$consumerOwnerDescription""".stripMargin,
    EmptyBody,
    MyConsumersJsonV700(List(JSONFactory700Organisations.myConsumerJsonV700Example)),
    List($AuthenticatedUserIsRequired, UnknownError),
    apiTagConsumer :: apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyConsumers)
  )

  // Route: GET /obp/v7.0.0/my/consumers/CONSUMER_ID
  lazy val getMyConsumer: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "consumers" / consumerId =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        myConsumer(consumerId, user.userId, cc).map(JSONFactory700Organisations.createMyConsumerJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyConsumer),
    "GET",
    "/my/consumers/CONSUMER_ID",
    "Get My Consumer",
    s"""Get one of your Consumers. A disabled Consumer is returned with enabled false, so you can see why your
       |application stopped working.
       |
       |$consumerOwnerDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Organisations.myConsumerJsonV700Example,
    List($AuthenticatedUserIsRequired, MyConsumerNotFound, UnknownError),
    apiTagConsumer :: apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyConsumer)
  )

  // Route: GET /obp/v7.0.0/my/consumers/CONSUMER_ID/usage
  lazy val getMyConsumerUsage: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "consumers" / consumerId / "usage" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          consumer <- myConsumer(consumerId, user.userId, cc)
          callCounters <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
          date = new java.util.Date()
          (activeRateLimit, rateLimitIds) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumer.consumerId.get, date)
        } yield MyConsumerUsageJsonV700(
          consumer.consumerId.get,
          JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(activeRateLimit, rateLimitIds, date),
          JSONFactory600.createRedisCallCountersJson(callCounters)
        )
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyConsumerUsage),
    "GET",
    "/my/consumers/CONSUMER_ID/usage",
    "Get My Consumer Usage",
    s"""Get the rate limits one of your Consumers is under now, and how many calls it has made in each period
       |so far. Every response from OBP also carries the headers X-Rate-Limit-Limit, X-Rate-Limit-Remaining and
       |X-Rate-Limit-Reset for the calling application.
       |
       |$consumerOwnerDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Organisations.myConsumerUsageJsonV700Example,
    List($AuthenticatedUserIsRequired, MyConsumerNotFound, UnknownError),
    apiTagConsumer :: apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyConsumerUsage)
  )

  // Route: GET /obp/v7.0.0/my/consumers/CONSUMER_ID/scopes
  lazy val getMyConsumerScopes: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "consumers" / consumerId / "scopes" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          consumer <- myConsumer(consumerId, user.userId, cc)
          scopes <- Future(code.scope.Scope.scope.vend.getScopesByConsumerId(consumer.id.get.toString).openOr(Nil))
        } yield MyConsumerScopesJsonV700(consumer.consumerId.get,
          scopes.map(scope => MyConsumerScopeJsonV700(scope.roleName, scope.bankId)).sortBy(scope => (scope.role_name, scope.bank_id)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyConsumerScopes),
    "GET",
    "/my/consumers/CONSUMER_ID/scopes",
    "Get My Consumer Scopes",
    s"""Get the Scopes one of your Consumers holds: the Roles it may use when it acts as itself, and where. A
       |bank_id of "" means a system Role.
       |
       |$consumerOwnerDescription""".stripMargin,
    EmptyBody,
    JSONFactory700Organisations.myConsumerScopesJsonV700Example,
    List($AuthenticatedUserIsRequired, MyConsumerNotFound, UnknownError),
    apiTagConsumer :: apiTagScope :: apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyConsumerScopes)
  )

  // Route: GET /obp/v7.0.0/my/consumers/CONSUMER_ID/api-product-subscriptions
  lazy val getMyConsumerApiProductSubscriptions: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "my" / "consumers" / consumerId / "api-product-subscriptions" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          consumer <- myConsumer(consumerId, user.userId, cc)
          (subscriptions, _) <- NewStyle.function.getApiProductSubscriptionsByConsumerId(consumer.consumerId.get, Some(cc))
          withAttributes <- Future.sequence(subscriptions.map { subscription =>
            NewStyle.function.getApiProductSubscriptionAttributes(subscription.apiProductSubscriptionId, Some(cc))
              .map { case (attributes, _) => JSONFactory700.createApiProductSubscriptionJsonV700(subscription, Some(attributes)) }
          })
        } yield JSONFactory700.ApiProductSubscriptionsJsonV700(withAttributes)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyConsumerApiProductSubscriptions),
    "GET",
    "/my/consumers/CONSUMER_ID/api-product-subscriptions",
    "Get My Consumer Api Product Subscriptions",
    s"""Get the API Product Subscriptions of one of your Consumers, at every bank, with their attributes.
       |
       |$consumerOwnerDescription""".stripMargin,
    EmptyBody,
    JSONFactory700.apiProductSubscriptionsJsonV700Example,
    List($AuthenticatedUserIsRequired, MyConsumerNotFound, UnknownError),
    apiTagConsumer :: apiTagApiProductSubscription :: apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(getMyConsumerApiProductSubscriptions)
  )

  // Route: PUT /obp/v7.0.0/my/consumers/CONSUMER_ID/organisation
  lazy val updateMyConsumerOrganisation: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "my" / "consumers" / consumerId / "organisation" =>
      EndpointHelpers.withUserAndBody[PutConsumerOrganisationJsonV700, MyConsumerJsonV700](req) { (user, body, cc) =>
        val targetOrganisationId = body.organisation_id.map(_.trim).filter(_.nonEmpty)
        for {
          consumer <- myConsumer(consumerId, user.userId, cc)
          // Taking a Consumer out of its Organisation, or into another one, takes an admin of its Organisation.
          _ <- Helper.booleanToFuture(OrganisationAdminRequired, 403, Some(cc)) {
            ConsumerOwnership.organisationIdOf(consumer).forall(_ => ConsumerOwnership.isAdminOfConsumersOrganisation(consumer, user.userId))
          }
          // Putting it into an Organisation takes an admin of that Organisation.
          _ <- targetOrganisationId match {
            case Some(organisationId) =>
              for {
                (organisation, membership) <- membershipOf(organisationId, user.userId, cc)
                _ <- requireAdmin(membership, cc)
                _ <- Helper.booleanToFuture(InvalidOrganisationStatus, 400, Some(cc))(organisation.status == Organisations.StatusActive)
              } yield ()
            case None => Future.successful(())
          }
          updated <- Future {
            net.liftweb.util.Helpers.tryo(consumer.organisationId(targetOrganisationId.getOrElse("")).saveMe())
          }.map(unboxFullOrFail(_, Some(cc), UpdateConsumerOrganisationError, 400))
        } yield JSONFactory700Organisations.createMyConsumerJson(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateMyConsumerOrganisation),
    "PUT",
    "/my/consumers/CONSUMER_ID/organisation",
    "Update My Consumer Organisation",
    s"""Move one of your Consumers into an Organisation, or out of it with organisation_id null.
       |
       |Moving a Consumer into an Organisation needs you to be an admin of that Organisation, which must be
       |active. A Consumer that already belongs to an Organisation can only be moved by an admin of it. Once a
       |Consumer belongs to an Organisation, its members act for it, and its creator no longer does on their own.
       |Moved out, it belongs to its creator again.
       |
       |$consumerOwnerDescription""".stripMargin,
    PutConsumerOrganisationJsonV700(Some("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0")),
    JSONFactory700Organisations.myConsumerJsonV700Example,
    List($AuthenticatedUserIsRequired, InvalidJsonFormat, MyConsumerNotFound, OrganisationNotFound, OrganisationAdminRequired,
      InvalidOrganisationStatus, UpdateConsumerOrganisationError, UnknownError),
    apiTagConsumer :: apiTagOrganisation :: Nil,
    None,
    http4sPartialFunction = Some(updateMyConsumerOrganisation)
  )
}
