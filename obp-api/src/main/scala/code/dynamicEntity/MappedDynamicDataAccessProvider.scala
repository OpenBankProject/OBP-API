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

package code.DynamicData

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.DoobieUtil
import code.organisation.Organisations
import cats.data.NonEmptyList
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.mutable

/**
 * One row of a Dynamic Entity record's access list: what one user may do with one record.
 *
 * A system level row stores the SYS sentinel (Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) in bankid
 * rather than a SQL NULL; it is filtered back out of `bankId`, so every reader still sees None.
 */
case class DynamicDataAccess(
  dynamicDataId: String,
  userId: String,
  canRead: Boolean,
  canUpdate: Boolean,
  canDelete: Boolean,
  canGrant: Boolean,
  grantedBy: String,
  entityName: String,
  private val bankIdRaw: Option[String]
) extends DynamicDataAccessT {
  override def bankId: Option[String] =
    bankIdRaw.filter(b => b.nonEmpty && b != DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
}

object DynamicDataAccess {

  // ProjectionStore builds raw SQL against this table for user-scoped EXISTS joins, and used to
  // read the names off the Lift metadata. They live here so the DDL and that SQL cannot drift.
  val tableName: String = "dynamicdataaccess"
  val dataIdColumn: String = "dynamicdataid"
  val userIdColumn: String = "userid"
  val canReadColumn: String = "canread"

  private val selectColumns =
    fr"""SELECT dynamicdataid, userid, canread, canupdate, candelete, cangrant, grantedby,
                entityname, bankid
         FROM dynamicdataaccess"""

  // grantedby and entityname are bound through Option on insert, so they are read as Option; the
  // flags follow MappedBoolean, which read a NULL column as false rather than throwing.
  private type Row = (Option[String], Option[String], Option[Boolean], Option[Boolean],
    Option[Boolean], Option[Boolean], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): DynamicDataAccess = row match {
    case (dynamicDataId, userId, canRead, canUpdate, canDelete, canGrant, grantedBy, entityName,
          bankId) =>
      DynamicDataAccess(dynamicDataId.orNull, userId.orNull, canRead.getOrElse(false),
        canUpdate.getOrElse(false), canDelete.getOrElse(false), canGrant.getOrElse(false),
        grantedBy.orNull, entityName.orNull, bankId)
  }

  private def query(condition: Fragment): List[DynamicDataAccess] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** The value bankid holds for a space: the bank's id, or SYS for the system space (None). */
  def storedBankId(bankId: Option[String]): String =
    bankId.filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

  /**
   * One record: DynamicDataId alone does not identify a record -- ids repeat across spaces -- so every
   * query names the space and the entity as well, in the order of the unique index.
   */
  private def thisRecord(bankId: Option[String], entityName: String, dynamicDataId: String): Fragment =
    fr"WHERE bankid = ${storedBankId(bankId)} AND entityname = $entityName AND dynamicdataid = $dynamicDataId"

  def find(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String): Box[DynamicDataAccess] =
    query(thisRecord(bankId, entityName, dynamicDataId) ++ fr"AND userid = $userId ORDER BY id ASC")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataAccess] =
    query(thisRecord(bankId, entityName, dynamicDataId) ++ fr"ORDER BY id ASC")

  def findGrantedBy(bankId: Option[String], entityName: String, dynamicDataId: String,
                    grantedBy: String): List[DynamicDataAccess] =
    query(thisRecord(bankId, entityName, dynamicDataId) ++ fr"AND grantedby = $grantedBy ORDER BY id ASC")

  def findReadable(bankId: Option[String], entityName: String, userId: String): List[DynamicDataAccess] =
    query(fr"WHERE bankid = ${storedBankId(bankId)} AND entityname = $entityName AND userid = $userId" ++
      fr"AND canread = true ORDER BY id ASC")

  /** Upsert on (space, entity, record, user) — the unique index is what makes that tuple single-valued. */
  def grant(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String,
            canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
            grantedBy: String): DynamicDataAccess = {
    val updated = DoobieUtil.runUpdate(
      (fr"""UPDATE dynamicdataaccess SET canread = $canRead, canupdate = $canUpdate,
              candelete = $canDelete, cangrant = $canGrant, grantedby = ${Option(grantedBy)}""" ++
        thisRecord(bankId, entityName, dynamicDataId) ++ fr"AND userid = $userId").update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO dynamicdataaccess
              (dynamicdataid, userid, canread, canupdate, candelete, cangrant, grantedby,
               entityname, bankid)
              VALUES ($dynamicDataId, $userId, $canRead, $canUpdate, $canDelete, $canGrant,
               ${Option(grantedBy)}, ${Option(entityName)}, ${storedBankId(bankId)})"""
          .update.run)
    }
    find(bankId, entityName, dynamicDataId, userId)
      .openOrThrowException("the access row just written must be readable")
  }

  def delete(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicdataaccess" ++ thisRecord(bankId, entityName, dynamicDataId) ++
        fr"AND userid = $userId").update.run)

  def deleteAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): Boolean = {
    DoobieUtil.runUpdate((fr"DELETE FROM dynamicdataaccess" ++ thisRecord(bankId, entityName, dynamicDataId)).update.run)
    true
  }

  def deleteAllForEntity(bankId: Option[String], entityName: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM dynamicdataaccess
            WHERE bankid = ${storedBankId(bankId)} AND entityname = $entityName""".update.run)
    true
  }

  /** Every access row held at one bank, whatever its entity. Never the system space: SYS is not a bank. */
  def deleteAllAtBank(bankId: String): Int =
    if (bankId == null || bankId.isEmpty || bankId == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) 0
    else DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataaccess WHERE bankid = $bankId".update.run)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataaccess".update.run)
    ()
  }
}

object MappedDynamicDataAccessProvider extends DynamicDataAccessProvider {

  override def grant(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String,
                     canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
                     grantedBy: String): Box[DynamicDataAccessT] = tryo {
    DynamicDataAccess.grant(bankId, entityName, dynamicDataId, userId, canRead, canUpdate, canDelete,
      canGrant, grantedBy)
  }

  override def revoke(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String): Box[Int] = tryo {
    // Walk the GrantedBy edges within this single data row: remove the target user and
    // everyone they granted downstream. The visited-set makes re-share cycles terminate
    // and absorbs the owner row's self-edge (GrantedBy == UserId).
    val toRemove = mutable.LinkedHashSet[String](userId)
    val visited  = mutable.Set[String]()
    var frontier = List(userId)
    while (frontier.nonEmpty) {
      val current = frontier.head
      frontier = frontier.tail
      if (!visited.contains(current)) {
        visited += current
        val children = DynamicDataAccess.findGrantedBy(bankId, entityName, dynamicDataId, current)
          .map(_.userId).filterNot(visited.contains)
        children.foreach { child =>
          toRemove += child
          frontier = child :: frontier
        }
      }
    }
    // Counts the users whose row actually went, as the Mapper version's count(identity) over
    // delete_! results did — a user in the walk with no row here contributes nothing.
    toRemove.toList.map(uid => DynamicDataAccess.delete(bankId, entityName, dynamicDataId, uid)).count(_ > 0)
  }

  override def getAccessForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataAccessT] =
    DynamicDataAccess.findAllForRow(bankId, entityName, dynamicDataId)

  override def getReadableDynamicDataIds(bankId: Option[String], entityName: String, userId: String): List[String] = {
    val grantedToUser = DynamicDataAccess.findReadable(bankId, entityName, userId).map(_.dynamicDataId)
    val organisationIds = activeOrganisationIdsOf(userId)
    val grantedToOrganisations =
      if (organisationIds.isEmpty) Nil
      else DynamicDataOrganisationAccess.findReadable(bankId, entityName, organisationIds).map(_.dynamicDataId)
    (grantedToUser ++ grantedToOrganisations).distinct
  }

  /** The Organisations the User is a member of that are active. A suspended Organisation grants nothing. */
  private def activeOrganisationIdsOf(userId: String): List[String] =
    Organisations.getOrganisationsOfUser(userId)
      .collect { case (organisation, _) if organisation.status == Organisations.StatusActive => organisation.organisationId }

  override def allows(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String,
                      permission: DynamicDataAccessPermission): Boolean = {
    import DynamicDataAccessPermission._
    def holds(canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean): Boolean =
      permission match {
        case Read   => canRead
        case Update => canUpdate
        case Delete => canDelete
        case Grant  => canGrant
      }
    val allowedByUserGrant = DynamicDataAccess.find(bankId, entityName, dynamicDataId, userId)
      .exists(row => holds(row.canRead, row.canUpdate, row.canDelete, row.canGrant))
    allowedByUserGrant || {
      val organisationGrants = getOrganisationAccessForRow(bankId, entityName, dynamicDataId)
        .filter(grant => holds(grant.canRead, grant.canUpdate, grant.canDelete, grant.canGrant))
      organisationGrants.nonEmpty && {
        val organisationIds = activeOrganisationIdsOf(userId).toSet
        organisationGrants.exists(grant => organisationIds.contains(grant.organisationId))
      }
    }
  }

  override def grantToOrganisation(bankId: Option[String], entityName: String, dynamicDataId: String, organisationId: String,
                                   canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
                                   grantedBy: String): Box[DynamicDataOrganisationAccessT] = tryo {
    DynamicDataOrganisationAccess.grant(bankId, entityName, dynamicDataId, organisationId, canRead, canUpdate,
      canDelete, canGrant, grantedBy)
  }

  override def revokeOrganisation(bankId: Option[String], entityName: String, dynamicDataId: String, organisationId: String): Box[Int] = tryo {
    DynamicDataOrganisationAccess.delete(bankId, entityName, dynamicDataId, organisationId)
  }

  override def getOrganisationAccessForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataOrganisationAccessT] =
    DynamicDataOrganisationAccess.findAllForRow(bankId, entityName, dynamicDataId)

  override def deleteAllForOrganisation(organisationId: String): Box[Boolean] = tryo {
    DynamicDataOrganisationAccess.deleteAllForOrganisation(organisationId)
    true
  }

  override def deleteAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): Box[Boolean] = tryo {
    DynamicDataOrganisationAccess.deleteAllForRow(bankId, entityName, dynamicDataId)
    DynamicDataAccess.deleteAllForRow(bankId, entityName, dynamicDataId)
  }

  override def deleteAllForEntity(bankId: Option[String], entityName: String): Box[Boolean] = tryo {
    DynamicDataOrganisationAccess.deleteAllForEntity(bankId, entityName)
    DynamicDataAccess.deleteAllForEntity(bankId, entityName)
  }
}

/**
 * A grant on one record to an Organisation. Kept in its own table rather than as a DynamicDataAccess row
 * with an empty user id, so that the unique index of each table names exactly who the grant is for.
 *
 * Like DynamicDataAccess, a system level grant stores the SYS sentinel in bankid, and `bankId` reads it back
 * as None.
 */
case class DynamicDataOrganisationAccess(
  dynamicDataId: String,
  organisationId: String,
  canRead: Boolean,
  canUpdate: Boolean,
  canDelete: Boolean,
  canGrant: Boolean,
  grantedBy: String,
  entityName: String,
  private val bankIdRaw: String
) extends DynamicDataOrganisationAccessT {
  override def bankId: Option[String] = Option(bankIdRaw).filterNot(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
}

object DynamicDataOrganisationAccess {

  // ProjectionStore builds raw SQL against this table for its organisation-grant EXISTS joins.
  val tableName: String = "dynamicdataorganisationaccess"
  val dataIdColumn: String = "dynamicdataid"
  val organisationIdColumn: String = "organisationid"
  val canReadColumn: String = "canread"

  private val selectColumns =
    fr"""SELECT dynamicdataid, organisationid, canread, canupdate, candelete, cangrant, grantedby,
                entityname, bankid
         FROM dynamicdataorganisationaccess"""

  // The flags follow MappedBoolean, which read a NULL column as false rather than throwing.
  private type Row = (Option[String], Option[String], Option[Boolean], Option[Boolean],
    Option[Boolean], Option[Boolean], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): DynamicDataOrganisationAccess = row match {
    case (dynamicDataId, organisationId, canRead, canUpdate, canDelete, canGrant, grantedBy, entityName,
          bankId) =>
      DynamicDataOrganisationAccess(dynamicDataId.orNull, organisationId.orNull, canRead.getOrElse(false),
        canUpdate.getOrElse(false), canDelete.getOrElse(false), canGrant.getOrElse(false),
        grantedBy.orNull, entityName.orNull, bankId.orNull)
  }

  private def query(condition: Fragment): List[DynamicDataOrganisationAccess] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def storedBankId(bankId: Option[String]): String = DynamicDataAccess.storedBankId(bankId)

  /** One record, named by its space, entity and id, in the order of the unique index. */
  private def thisRecord(bankId: Option[String], entityName: String, dynamicDataId: String): Fragment =
    fr"WHERE bankid = ${storedBankId(bankId)} AND entityname = ${Option(entityName)} AND dynamicdataid = ${Option(dynamicDataId)}"

  def find(bankId: Option[String], entityName: String, dynamicDataId: String,
           organisationId: String): Box[DynamicDataOrganisationAccess] =
    query(thisRecord(bankId, entityName, dynamicDataId) ++ fr"AND organisationid = ${Option(organisationId)} ORDER BY id ASC")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataOrganisationAccess] =
    query(thisRecord(bankId, entityName, dynamicDataId) ++ fr"ORDER BY id ASC")

  /** The grants of the entity in this space that let any of these Organisations read. An empty list matches nothing. */
  def findReadable(bankId: Option[String], entityName: String, organisationIds: List[String]): List[DynamicDataOrganisationAccess] =
    organisationIds.distinct match {
      case Nil => Nil
      case ids =>
        query(fr"WHERE bankid = ${storedBankId(bankId)} AND entityname = ${Option(entityName)} AND" ++
          Fragments.in(fr"organisationid", NonEmptyList.fromListUnsafe(ids)) ++ fr"AND canread = true ORDER BY id ASC")
    }

  /** Upsert on (space, entity, record, Organisation): the unique index makes that tuple single-valued. */
  def grant(bankId: Option[String], entityName: String, dynamicDataId: String, organisationId: String,
            canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
            grantedBy: String): DynamicDataOrganisationAccess = {
    val updated = DoobieUtil.runUpdate(
      (fr"""UPDATE dynamicdataorganisationaccess SET canread = $canRead, canupdate = $canUpdate,
              candelete = $canDelete, cangrant = $canGrant, grantedby = ${Option(grantedBy)}""" ++
        thisRecord(bankId, entityName, dynamicDataId) ++ fr"AND organisationid = ${Option(organisationId)}").update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO dynamicdataorganisationaccess
              (dynamicdataid, organisationid, canread, canupdate, candelete, cangrant, grantedby,
               entityname, bankid)
              VALUES (${Option(dynamicDataId)}, ${Option(organisationId)}, $canRead, $canUpdate, $canDelete,
               $canGrant, ${Option(grantedBy)}, ${Option(entityName)}, ${storedBankId(bankId)})"""
          .update.run)
    }
    find(bankId, entityName, dynamicDataId, organisationId)
      .openOrThrowException("the organisation grant just written must be readable")
  }

  def delete(bankId: Option[String], entityName: String, dynamicDataId: String, organisationId: String): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicdataorganisationaccess" ++ thisRecord(bankId, entityName, dynamicDataId) ++
        fr"AND organisationid = ${Option(organisationId)}").update.run)

  def deleteAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicdataorganisationaccess" ++ thisRecord(bankId, entityName, dynamicDataId)).update.run)

  def deleteAllForEntity(bankId: Option[String], entityName: String): Int =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM dynamicdataorganisationaccess
            WHERE bankid = ${storedBankId(bankId)} AND entityname = ${Option(entityName)}""".update.run)

  def deleteAllForOrganisation(organisationId: String): Int =
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicdataorganisationaccess WHERE organisationid = ${Option(organisationId)}".update.run)

  /** Every Organisation grant held at one bank, whatever its entity. Never the system space: SYS is not a bank. */
  def deleteAllAtBank(bankId: String): Int =
    if (bankId == null || bankId.isEmpty || bankId == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) 0
    else DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataorganisationaccess WHERE bankid = $bankId".update.run)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataorganisationaccess".update.run)
    ()
  }
}
