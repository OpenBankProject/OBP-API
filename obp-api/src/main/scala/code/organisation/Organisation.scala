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
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

/**
 * An Organisation is a group of people that uses the APIs and owns Consumers, for example a fintech. It has
 * members, each an admin or a member. Its bank_id is the space that administers it: SYS unless a bank is
 * given. It is not a legal relationship with that bank (that is a Customer), and it is not a Tenant (a customer
 * of the operator of this instance). See todo/todo_organisations.md.
 */
trait OrganisationTrait {
  def organisationId: String
  def bankId: String
  def name: String
  def description: String
  def website: Option[String]
  def status: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}

trait OrganisationMemberTrait {
  def organisationId: String
  def userId: String
  def memberRole: String
  def createdByUserId: String
  def createdAt: java.util.Date
}

object Organisations {
  val StatusActive = "active"
  val StatusSuspended = "suspended"
  val ValidStatuses: Set[String] = Set(StatusActive, StatusSuspended)

  val RoleAdmin = "admin"
  val RoleMember = "member"
  val ValidMemberRoles: Set[String] = Set(RoleAdmin, RoleMember)

  /** Creates the Organisation with its first admin. The request's transaction covers both rows. */
  def createOrganisation(bankId: String, name: String, description: String, website: Option[String], firstAdminUserId: String,
                         createdByUserId: String): Box[OrganisationTrait] =
    tryo {
      {
        val organisation = Organisation.create
          .OrganisationId(APIUtil.generateUUID())
          .BankId(bankId)
          .Name(name)
          .Description(description)
          .Website(website.getOrElse(""))
          .Status(StatusActive)
          .CreatedByUserId(createdByUserId)
          .saveMe()
        OrganisationMember.create
          .OrganisationId(organisation.organisationId)
          .UserId(firstAdminUserId)
          .MemberRole(RoleAdmin)
          .CreatedByUserId(createdByUserId)
          .saveMe()
        organisation
      }
    }

  def getOrganisation(organisationId: String): Box[OrganisationTrait] =
    Organisation.find(By(Organisation.OrganisationId, organisationId))

  def getOrganisations(): List[OrganisationTrait] = Organisation.findAll(OrderBy(Organisation.Name, Ascending))

  def getOrganisationsOfUser(userId: String): List[(OrganisationTrait, OrganisationMemberTrait)] = {
    val memberships = OrganisationMember.findAll(By(OrganisationMember.UserId, userId))
    memberships.flatMap(membership => getOrganisation(membership.organisationId).toList.map(organisation => (organisation, membership)))
      .sortBy(_._1.name.toLowerCase)
  }

  def updateOrganisation(organisationId: String, name: Option[String], description: Option[String], website: Option[String]): Box[OrganisationTrait] =
    Organisation.find(By(Organisation.OrganisationId, organisationId)).flatMap { organisation =>
      tryo {
        name.foreach(value => organisation.Name(value))
        description.foreach(value => organisation.Description(value))
        website.foreach(value => organisation.Website(value))
        organisation.LastUpdate(new java.util.Date()).saveMe()
      }
    }

  def updateStatus(organisationId: String, status: String): Box[OrganisationTrait] =
    Organisation.find(By(Organisation.OrganisationId, organisationId)).flatMap { organisation =>
      tryo { organisation.Status(status).LastUpdate(new java.util.Date()).saveMe() }
    }

  /** Deletes the Organisation and its memberships. The caller checks first that it has no Consumers. */
  def deleteOrganisation(organisationId: String): Box[Boolean] =
    Organisation.find(By(Organisation.OrganisationId, organisationId)).flatMap { organisation =>
      tryo {
        OrganisationMember.bulkDelete_!!(By(OrganisationMember.OrganisationId, organisationId))
        // Its grants on Dynamic Entity records go too, so a later Organisation can never inherit them.
        code.DynamicData.DynamicDataAccessProvider.provider.vend.deleteAllForOrganisation(organisationId)
        organisation.delete_!
      }
    }

  def getMembers(organisationId: String): List[OrganisationMemberTrait] =
    OrganisationMember.findAll(By(OrganisationMember.OrganisationId, organisationId), OrderBy(OrganisationMember.CreationDate, Ascending))

  def getMember(organisationId: String, userId: String): Box[OrganisationMemberTrait] =
    OrganisationMember.find(By(OrganisationMember.OrganisationId, organisationId), By(OrganisationMember.UserId, userId))

  def addMember(organisationId: String, userId: String, memberRole: String, createdByUserId: String): Box[OrganisationMemberTrait] =
    tryo {
      OrganisationMember.create
        .OrganisationId(organisationId)
        .UserId(userId)
        .MemberRole(memberRole)
        .CreatedByUserId(createdByUserId)
        .saveMe()
    }

  def updateMemberRole(organisationId: String, userId: String, memberRole: String): Box[OrganisationMemberTrait] =
    OrganisationMember.find(By(OrganisationMember.OrganisationId, organisationId), By(OrganisationMember.UserId, userId)).flatMap { member =>
      tryo { member.MemberRole(memberRole).saveMe() }
    }

  def removeMember(organisationId: String, userId: String): Box[Boolean] =
    OrganisationMember.find(By(OrganisationMember.OrganisationId, organisationId), By(OrganisationMember.UserId, userId)).flatMap { member =>
      tryo { member.delete_! }
    }

  def adminCount(organisationId: String): Long =
    OrganisationMember.count(By(OrganisationMember.OrganisationId, organisationId), By(OrganisationMember.MemberRole, RoleAdmin))
}

class Organisation extends OrganisationTrait with LongKeyedMapper[Organisation] with IdPK {

  def getSingleton = Organisation

  object OrganisationId extends MappedString(this, 36)
  // The space that administers the Organisation: a bank id, or SYS. Staff Roles are checked here.
  object BankId extends MappedString(this, 255) {
    override def defaultValue = code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  }
  object Name extends MappedString(this, 255)
  object Description extends MappedText(this)
  object Website extends MappedString(this, 1024)
  object Status extends MappedString(this, 32)
  object CreatedByUserId extends MappedString(this, 36)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def organisationId: String = OrganisationId.get
  override def bankId: String = BankId.get
  override def name: String = Name.get
  override def description: String = Option(Description.get).getOrElse("")
  override def website: Option[String] = Option(Website.get).filter(_.nonEmpty)
  override def status: String = Status.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get
}

object Organisation extends Organisation with LongKeyedMetaMapper[Organisation] {
  // Not "Organisation": the table of that name, from before Organisations were renamed Tenants, may still
  // exist with old rows in it.
  override def dbTableName = "ObpOrganisation"
  override def dbIndexes = UniqueIndex(OrganisationId) :: Index(BankId) :: super.dbIndexes
}

class OrganisationMember extends OrganisationMemberTrait with LongKeyedMapper[OrganisationMember] with IdPK {

  def getSingleton = OrganisationMember

  object OrganisationId extends MappedString(this, 36)
  object UserId extends MappedString(this, 36)
  object MemberRole extends MappedString(this, 32)
  object CreatedByUserId extends MappedString(this, 36)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def organisationId: String = OrganisationId.get
  override def userId: String = UserId.get
  override def memberRole: String = MemberRole.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
}

object OrganisationMember extends OrganisationMember with LongKeyedMetaMapper[OrganisationMember] {
  override def dbTableName = "ObpOrganisationMember"
  override def dbIndexes = UniqueIndex(OrganisationId, UserId) :: Index(UserId) :: super.dbIndexes
}
