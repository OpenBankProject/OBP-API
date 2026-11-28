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

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date

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
        val organisation = Organisation.insert(APIUtil.generateUUID(), bankId, name, description, website.getOrElse(""),
          StatusActive, createdByUserId)
        OrganisationMember.insert(organisation.organisationId, firstAdminUserId, RoleAdmin, createdByUserId)
        organisation
      }
    }

  def getOrganisation(organisationId: String): Box[OrganisationTrait] =
    Organisation.findByOrganisationId(organisationId)

  def getOrganisations(): List[OrganisationTrait] = Organisation.findAllOrderedByName()

  def getOrganisationsOfUser(userId: String): List[(OrganisationTrait, OrganisationMemberTrait)] = {
    val memberships = OrganisationMember.findAllByUserId(userId)
    memberships.flatMap(membership => getOrganisation(membership.organisationId).toList.map(organisation => (organisation, membership)))
      .sortBy(_._1.name.toLowerCase)
  }

  def updateOrganisation(organisationId: String, name: Option[String], description: Option[String], website: Option[String]): Box[OrganisationTrait] =
    Organisation.findByOrganisationId(organisationId).flatMap { organisation =>
      tryo {
        Organisation.update(organisation.copy(
          name = name.getOrElse(organisation.name),
          description = description.getOrElse(organisation.description),
          websiteRaw = website.getOrElse(organisation.websiteRaw),
          updatedAt = new Date()))
      }
    }

  def updateStatus(organisationId: String, status: String): Box[OrganisationTrait] =
    Organisation.findByOrganisationId(organisationId).flatMap { organisation =>
      tryo { Organisation.update(organisation.copy(status = status, updatedAt = new Date())) }
    }

  /** Deletes the Organisation and its memberships. The caller checks first that it has no Consumers. */
  def deleteOrganisation(organisationId: String): Box[Boolean] =
    Organisation.findByOrganisationId(organisationId).flatMap { organisation =>
      tryo {
        OrganisationMember.deleteAllOfOrganisation(organisationId)
        // Its grants on Dynamic Entity records go too, so a later Organisation can never inherit them.
        code.DynamicData.DynamicDataAccessProvider.provider.vend.deleteAllForOrganisation(organisationId)
        Organisation.delete(organisation.organisationId)
      }
    }

  def getMembers(organisationId: String): List[OrganisationMemberTrait] =
    OrganisationMember.findAllOfOrganisation(organisationId)

  def getMember(organisationId: String, userId: String): Box[OrganisationMemberTrait] =
    OrganisationMember.find(organisationId, userId)

  def addMember(organisationId: String, userId: String, memberRole: String, createdByUserId: String): Box[OrganisationMemberTrait] =
    tryo {
      OrganisationMember.insert(organisationId, userId, memberRole, createdByUserId)
    }

  def updateMemberRole(organisationId: String, userId: String, memberRole: String): Box[OrganisationMemberTrait] =
    OrganisationMember.find(organisationId, userId).flatMap { member =>
      tryo { OrganisationMember.updateRole(member.copy(memberRole = memberRole)) }
    }

  def removeMember(organisationId: String, userId: String): Box[Boolean] =
    OrganisationMember.find(organisationId, userId).flatMap { member =>
      tryo { OrganisationMember.delete(member.organisationId, member.userId) }
    }

  def adminCount(organisationId: String): Long =
    OrganisationMember.countWithRole(organisationId, RoleAdmin)
}

/**
 * One Organisation. The bank_id is the space that administers it: a bank id, or SYS. Staff Roles are checked
 * there. An empty website reads back as None, and a missing description as "", as Mapper's did.
 */
case class Organisation(
  organisationId: String,
  bankId: String,
  name: String,
  description: String,
  private[organisation] val websiteRaw: String,
  status: String,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends OrganisationTrait {
  override def website: Option[String] = Option(websiteRaw).filter(_.nonEmpty)
}

object Organisation {

  // Not "organisation": the table of that name, from before Organisations were renamed Tenants, may still
  // exist with old rows in it. ProjectionStore names these in raw SQL for its organisation-grant joins.
  val tableName: String = "obporganisation"
  val organisationIdColumn: String = "organisationid"
  val statusColumn: String = "status"

  private val selectColumns =
    fr"""SELECT organisationid, bankid, name, description, website, status, createdbyuserid, creationdate,
                lastupdate
         FROM obporganisation"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def fromRow(r: Row): Organisation =
    Organisation(r._1.orNull, r._2.orNull, r._3.orNull, r._4.getOrElse(""), r._5.orNull, r._6.orNull, r._7.orNull,
      readDate(r._8), readDate(r._9))

  private def query(condition: Fragment): List[Organisation] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def ts(d: Date): java.sql.Timestamp = new java.sql.Timestamp(d.getTime)

  def findByOrganisationId(organisationId: String): Box[Organisation] =
    query(fr"WHERE organisationid = ${Option(organisationId)} ORDER BY id ASC").headOption match {
      case Some(o) => Full(o)
      case None => Empty
    }

  def findAllOrderedByName(): List[Organisation] = query(fr"ORDER BY name ASC, id ASC")

  def insert(organisationId: String, bankId: String, name: String, description: String, website: String,
             status: String, createdByUserId: String): Organisation = {
    val now = new Date()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO obporganisation (organisationid, bankid, name, description, website, status, createdbyuserid,
              creationdate, lastupdate)
            VALUES (${Option(organisationId)}, ${Option(bankId)}, ${Option(name)}, ${Option(description)},
              ${Option(website)}, ${Option(status)}, ${Option(createdByUserId)}, ${ts(now)}, ${ts(now)})"""
        .update.run)
    Organisation(organisationId, bankId, name, Option(description).getOrElse(""), website, status, createdByUserId, now, now)
  }

  def update(o: Organisation): Organisation = {
    DoobieUtil.runUpdate(
      sql"""UPDATE obporganisation SET name = ${Option(o.name)}, description = ${Option(o.description)},
              website = ${Option(o.websiteRaw)}, status = ${Option(o.status)}, lastupdate = ${ts(o.updatedAt)}
            WHERE organisationid = ${Option(o.organisationId)}""".update.run)
    o
  }

  def delete(organisationId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM obporganisation WHERE organisationid = ${Option(organisationId)}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM obporganisation".update.run)
    ()
  }
}

case class OrganisationMember(
  organisationId: String,
  userId: String,
  memberRole: String,
  createdByUserId: String,
  createdAt: Date
) extends OrganisationMemberTrait

object OrganisationMember {

  val tableName: String = "obporganisationmember"
  val organisationIdColumn: String = "organisationid"
  val userIdColumn: String = "userid"

  private val selectColumns =
    fr"""SELECT organisationid, userid, memberrole, createdbyuserid, creationdate
         FROM obporganisationmember"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[java.sql.Timestamp])

  private def fromRow(r: Row): OrganisationMember =
    OrganisationMember(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.map(t => new Date(t.getTime)).orNull)

  private def query(condition: Fragment): List[OrganisationMember] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(organisationId: String, userId: String): Box[OrganisationMember] =
    query(fr"WHERE organisationid = ${Option(organisationId)} AND userid = ${Option(userId)} ORDER BY id ASC")
      .headOption match {
        case Some(m) => Full(m)
        case None => Empty
      }

  def findAllByUserId(userId: String): List[OrganisationMember] =
    query(fr"WHERE userid = ${Option(userId)} ORDER BY id ASC")

  def findAllOfOrganisation(organisationId: String): List[OrganisationMember] =
    query(fr"WHERE organisationid = ${Option(organisationId)} ORDER BY creationdate ASC, id ASC")

  def countWithRole(organisationId: String, memberRole: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM obporganisationmember
            WHERE organisationid = ${Option(organisationId)} AND memberrole = ${Option(memberRole)}"""
        .query[Long].unique)

  /** The unique index on (organisation, user) is what rejects a second membership of the same User. */
  def insert(organisationId: String, userId: String, memberRole: String, createdByUserId: String): OrganisationMember = {
    val now = new Date()
    DoobieUtil.runUpdate(
      sql"""INSERT INTO obporganisationmember (organisationid, userid, memberrole, createdbyuserid, creationdate)
            VALUES (${Option(organisationId)}, ${Option(userId)}, ${Option(memberRole)}, ${Option(createdByUserId)},
              ${new java.sql.Timestamp(now.getTime)})"""
        .update.run)
    OrganisationMember(organisationId, userId, memberRole, createdByUserId, now)
  }

  def updateRole(m: OrganisationMember): OrganisationMember = {
    DoobieUtil.runUpdate(
      sql"""UPDATE obporganisationmember SET memberrole = ${Option(m.memberRole)}
            WHERE organisationid = ${Option(m.organisationId)} AND userid = ${Option(m.userId)}""".update.run)
    m
  }

  def delete(organisationId: String, userId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM obporganisationmember
            WHERE organisationid = ${Option(organisationId)} AND userid = ${Option(userId)}""".update.run) > 0

  def deleteAllOfOrganisation(organisationId: String): Int =
    DoobieUtil.runUpdate(
      sql"DELETE FROM obporganisationmember WHERE organisationid = ${Option(organisationId)}".update.run)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM obporganisationmember".update.run)
    ()
  }
}
