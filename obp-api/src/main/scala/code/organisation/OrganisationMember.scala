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
package code.organisation

import code.api.util.APIUtil
import code.users.{UserReference, Users}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

/**
 * A User's membership of an Organisation: one row per User and Organisation.
 *
 * A membership grants no Roles. It says who belongs to an Organisation and in what capacity
 * (member_role), and apps decide what that lets a member do. Leaving is a status, `left`, so the row,
 * and the history it carries, stays.
 */
object OrganisationMembers extends SimpleInjector {
  val organisationMember = new Inject(() => buildOne) {}

  def buildOne: OrganisationMemberProvider = MappedOrganisationMemberProvider

  val ValidMemberRoles: Set[String] = Set("owner", "admin", "member")
  val ValidStatuses: Set[String] = Set("invited", "active", "suspended", "left")
  /** The member roles that may manage the Organisation's members without holding a Role. */
  val ManagingMemberRoles: Set[String] = Set("owner", "admin")
}

trait OrganisationMemberProvider {
  /** `callerUserId` is whoever made the call; the person it acted for is recorded beside it. */
  def createMember(organisationId: String, userId: String, memberRole: String, title: Option[String],
                   status: String, isPrimary: Boolean, callerUserId: String): Box[OrganisationMemberTrait]

  def getMember(organisationId: String, userId: String): Box[OrganisationMemberTrait]

  def getMembers(organisationId: String): Box[List[OrganisationMemberTrait]]

  def getMembershipsOfUser(userId: String): Box[List[OrganisationMemberTrait]]

  def updateMember(organisationId: String, userId: String, memberRole: Option[String], title: Option[String],
                   status: Option[String], isPrimary: Option[Boolean], callerUserId: String): Box[OrganisationMemberTrait]

  def deleteMember(organisationId: String, userId: String): Box[Boolean]

  def deleteMembersOf(organisationId: String): Box[Boolean]
}

trait OrganisationMemberTrait {
  def organisationMemberId: String
  def organisationId: String
  def userId: String
  def memberRole: String
  def title: Option[String]
  def status: String
  def isPrimary: Boolean
  def createdByUserId: String
  def createdByOnBehalfOfUserId: String
  def updatedByUserId: String
  def updatedByOnBehalfOfUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}

object MappedOrganisationMemberProvider extends OrganisationMemberProvider {

  /**
   * The two ids recorded for a write: the caller itself, which is an agent's own id when an agent made
   * the call, and the person the call was made for. A membership says who may act for an Organisation
   * and outlives the Consent that created it, so both "which agent did this?" and "on whose authority?"
   * must be answerable from the row.
   */
  private def callerAndPerson(callerUserId: String, ref: UserReference): (String, String) =
    if (callerUserId == null || callerUserId.isEmpty) (callerUserId, callerUserId)
    else Users.users.vend.attributionOf(callerUserId, ref) match {
      case Full(attribution) => (attribution.userId, attribution.onBehalfOfUserId)
      case _                 => (callerUserId, callerUserId)
    }

  private def find(organisationId: String, userId: String): Box[OrganisationMember] =
    OrganisationMember.find(By(OrganisationMember.OrganisationId, organisationId), By(OrganisationMember.UserId, userId))

  /** A User has at most one primary Organisation: making one primary clears the flag on the others. */
  private def clearOtherPrimaries(userId: String, keepOrganisationId: String): Unit =
    OrganisationMember.findAll(By(OrganisationMember.UserId, userId), By(OrganisationMember.IsPrimary, true))
      .filterNot(_.OrganisationId.get == keepOrganisationId)
      .foreach(_.IsPrimary(false).save)

  override def createMember(organisationId: String, userId: String, memberRole: String, title: Option[String],
                            status: String, isPrimary: Boolean, callerUserId: String): Box[OrganisationMemberTrait] = {
    val (caller, person) = callerAndPerson(callerUserId, UserReference.OrganisationMember_CreatedByUserId)
    tryo {
      if (isPrimary) clearOtherPrimaries(userId, organisationId)
      OrganisationMember.create
        .OrganisationMemberId(APIUtil.generateUUID())
        .OrganisationId(organisationId)
        .UserId(userId)
        .MemberRole(memberRole)
        .Title(title.getOrElse(""))
        .Status(status)
        .IsPrimary(isPrimary)
        .CreatedByUserId(caller)
        .CreatedByOnBehalfOfUserId(person)
        .UpdatedByUserId(caller)
        .UpdatedByOnBehalfOfUserId(person)
        .saveMe()
    }
  }

  override def getMember(organisationId: String, userId: String): Box[OrganisationMemberTrait] =
    find(organisationId, userId)

  override def getMembers(organisationId: String): Box[List[OrganisationMemberTrait]] =
    tryo(OrganisationMember.findAll(By(OrganisationMember.OrganisationId, organisationId)))

  override def getMembershipsOfUser(userId: String): Box[List[OrganisationMemberTrait]] =
    tryo(OrganisationMember.findAll(By(OrganisationMember.UserId, userId)))

  override def updateMember(organisationId: String, userId: String, memberRole: Option[String], title: Option[String],
                            status: Option[String], isPrimary: Option[Boolean], callerUserId: String): Box[OrganisationMemberTrait] = {
    val (caller, person) = callerAndPerson(callerUserId, UserReference.OrganisationMember_UpdatedByUserId)
    find(organisationId, userId).flatMap { member =>
      tryo {
        if (isPrimary.contains(true)) clearOtherPrimaries(userId, organisationId)
        memberRole.foreach(v => member.MemberRole(v))
        title.foreach(v => member.Title(v))
        status.foreach(v => member.Status(v))
        isPrimary.foreach(v => member.IsPrimary(v))
        member.UpdatedByUserId(caller).UpdatedByOnBehalfOfUserId(person)
        member.LastUpdate(new java.util.Date())
        member.saveMe()
      }
    }
  }

  override def deleteMember(organisationId: String, userId: String): Box[Boolean] =
    find(organisationId, userId).flatMap(member => tryo(member.delete_!))

  override def deleteMembersOf(organisationId: String): Box[Boolean] =
    tryo {
      OrganisationMember.bulkDelete_!!(By(OrganisationMember.OrganisationId, organisationId))
      true
    }
}

class OrganisationMember extends OrganisationMemberTrait with LongKeyedMapper[OrganisationMember] with IdPK {

  def getSingleton = OrganisationMember

  object OrganisationMemberId extends MappedString(this, 36)
  object OrganisationId extends MappedString(this, 64)
  object UserId extends MappedString(this, 255)
  object MemberRole extends MappedString(this, 32)
  object Title extends MappedString(this, 255)
  object Status extends MappedString(this, 32)
  object IsPrimary extends MappedBoolean(this) {
    override def defaultValue = false
  }
  object CreatedByUserId extends MappedString(this, 255)
  object CreatedByOnBehalfOfUserId extends MappedString(this, 255)
  object UpdatedByUserId extends MappedString(this, 255)
  object UpdatedByOnBehalfOfUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def organisationMemberId: String = OrganisationMemberId.get
  override def organisationId: String = OrganisationId.get
  override def userId: String = UserId.get
  override def memberRole: String = MemberRole.get
  override def title: Option[String] = {
    val v = Title.get
    if (v == null || v.isEmpty) None else Some(v)
  }
  override def status: String = Status.get
  override def isPrimary: Boolean = IsPrimary.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdByOnBehalfOfUserId: String = CreatedByOnBehalfOfUserId.get
  override def updatedByUserId: String = UpdatedByUserId.get
  override def updatedByOnBehalfOfUserId: String = UpdatedByOnBehalfOfUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get
}

object OrganisationMember extends OrganisationMember with LongKeyedMetaMapper[OrganisationMember] {
  override def dbTableName = "OrganisationMember"
  override def dbIndexes =
    UniqueIndex(OrganisationMemberId) :: UniqueIndex(OrganisationId, UserId) :: Index(UserId) :: super.dbIndexes
}
