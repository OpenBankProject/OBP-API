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
import code.organisation.Organisations
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.mutable

object MappedDynamicDataAccessProvider extends DynamicDataAccessProvider {

  /**
   * This turns the optional bank id the caller supplies into the value actually stored in the
   * BankId column. An ACL row for a record that belongs to no bank is stored under
   * Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID rather than as a SQL NULL, for the same reason the
   * record itself is: the unique index over these rows has to include the bank id, and Postgres
   * treats NULLs as distinct, so a nullable column would make the index enforce nothing for the
   * system level rows.
   */
  private def storedBankId(bankId: Option[String]): String =
    bankId.getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

  /**
   * The parameters that pin an ACL row to one record: the space, the entity and the record id.
   *
   * A record id alone does not identify a record, because ids are only unique within one space and
   * one entity. Every query below starts from this list so that a grant made on the country code DE
   * in one space can never be read, revoked or cascaded as a grant on DE in another.
   *
   * The columns are always named in the order the things they identify come into existence -- the
   * bank, then the entity defined within it, then the record, then the user the grant is for --
   * which is also the column order of the unique index these rows live under. Anything a query adds
   * beyond these three is appended after them, so every query in this file reads the same way down.
   */
  private def whereClauseBankOrSystemAndEntityAndDataId(bankId: Option[String], entityName: String, dynamicDataId: String): List[QueryParam[DynamicDataAccess]] =
    List(
      By(DynamicDataAccess.BankId, storedBankId(bankId)),
      By(DynamicDataAccess.EntityName, entityName),
      By(DynamicDataAccess.DynamicDataId, dynamicDataId)
    )

  override def grant(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String,
                     canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
                     grantedBy: String): Box[DynamicDataAccessT] = tryo {
    val row = DynamicDataAccess.find(
      (whereClauseBankOrSystemAndEntityAndDataId(bankId, entityName, dynamicDataId) :+ By(DynamicDataAccess.UserId, userId)): _*
    ).getOrElse(
      DynamicDataAccess.create
        .BankId(storedBankId(bankId))
        .EntityName(entityName)
        .DynamicDataId(dynamicDataId)
        .UserId(userId)
    )
    row.BankId(storedBankId(bankId))
      .EntityName(entityName)
      .CanRead(canRead)
      .CanUpdate(canUpdate)
      .CanDelete(canDelete)
      .CanGrant(canGrant)
      .GrantedBy(grantedBy)
      .saveMe()
  }

  override def revoke(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String): Box[Int] = tryo {
    // Walk the GrantedBy edges within this single data row: remove the target user and
    // everyone they granted downstream. The visited-set makes re-share cycles terminate
    // and absorbs the owner row's self-edge (GrantedBy == UserId).
    val whereThisRecord = whereClauseBankOrSystemAndEntityAndDataId(bankId, entityName, dynamicDataId)
    val toRemove = mutable.LinkedHashSet[String](userId)
    val visited  = mutable.Set[String]()
    var frontier = List(userId)
    while (frontier.nonEmpty) {
      val current = frontier.head
      frontier = frontier.tail
      if (!visited.contains(current)) {
        visited += current
        val children = DynamicDataAccess.findAll(
          (whereThisRecord :+ By(DynamicDataAccess.GrantedBy, current)): _*
        ).map(_.UserId.get).filterNot(visited.contains)
        children.foreach { child =>
          toRemove += child
          frontier = child :: frontier
        }
      }
    }
    toRemove.toList.flatMap { uid =>
      DynamicDataAccess.findAll((whereThisRecord :+ By(DynamicDataAccess.UserId, uid)): _*)
    }.map(_.delete_!).count(identity)
  }

  override def getAccessForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataAccessT] =
    DynamicDataAccess.findAll(whereClauseBankOrSystemAndEntityAndDataId(bankId, entityName, dynamicDataId): _*)

  override def getReadableDynamicDataIds(bankId: Option[String], entityName: String, userId: String): List[String] = {
    val grantedToUser = DynamicDataAccess.findAll(
      By(DynamicDataAccess.BankId, storedBankId(bankId)),
      By(DynamicDataAccess.EntityName, entityName),
      By(DynamicDataAccess.UserId, userId),
      By(DynamicDataAccess.CanRead, true)
    ).map(_.DynamicDataId.get)
    val organisationIds = activeOrganisationIdsOf(userId)
    val grantedToOrganisations =
      if (organisationIds.isEmpty) Nil
      else DynamicDataOrganisationAccess.findAll(
        By(DynamicDataOrganisationAccess.BankId, storedBankId(bankId)),
        By(DynamicDataOrganisationAccess.EntityName, entityName),
        ByList(DynamicDataOrganisationAccess.OrganisationId, organisationIds),
        By(DynamicDataOrganisationAccess.CanRead, true)
      ).map(_.DynamicDataId.get)
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
    val allowedByUserGrant = DynamicDataAccess.find(
      (whereClauseBankOrSystemAndEntityAndDataId(bankId, entityName, dynamicDataId) :+ By(DynamicDataAccess.UserId, userId)): _*
    ).exists(row => holds(row.canRead, row.canUpdate, row.canDelete, row.canGrant))
    allowedByUserGrant || {
      val organisationGrants = getOrganisationAccessForRow(bankId, entityName, dynamicDataId)
        .filter(grant => holds(grant.canRead, grant.canUpdate, grant.canDelete, grant.canGrant))
      organisationGrants.nonEmpty && {
        val organisationIds = activeOrganisationIdsOf(userId).toSet
        organisationGrants.exists(grant => organisationIds.contains(grant.organisationId))
      }
    }
  }

  private def whereClauseOrganisationGrantsOfRecord(bankId: Option[String], entityName: String, dynamicDataId: String): List[QueryParam[DynamicDataOrganisationAccess]] =
    List(
      By(DynamicDataOrganisationAccess.BankId, storedBankId(bankId)),
      By(DynamicDataOrganisationAccess.EntityName, entityName),
      By(DynamicDataOrganisationAccess.DynamicDataId, dynamicDataId)
    )

  override def grantToOrganisation(bankId: Option[String], entityName: String, dynamicDataId: String, organisationId: String,
                                   canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
                                   grantedBy: String): Box[DynamicDataOrganisationAccessT] = tryo {
    val row = DynamicDataOrganisationAccess.find(
      (whereClauseOrganisationGrantsOfRecord(bankId, entityName, dynamicDataId) :+ By(DynamicDataOrganisationAccess.OrganisationId, organisationId)): _*
    ).getOrElse(
      DynamicDataOrganisationAccess.create
        .BankId(storedBankId(bankId))
        .EntityName(entityName)
        .DynamicDataId(dynamicDataId)
        .OrganisationId(organisationId)
    )
    row.CanRead(canRead)
      .CanUpdate(canUpdate)
      .CanDelete(canDelete)
      .CanGrant(canGrant)
      .GrantedBy(grantedBy)
      .saveMe()
  }

  override def revokeOrganisation(bankId: Option[String], entityName: String, dynamicDataId: String, organisationId: String): Box[Int] = tryo {
    DynamicDataOrganisationAccess.findAll(
      (whereClauseOrganisationGrantsOfRecord(bankId, entityName, dynamicDataId) :+ By(DynamicDataOrganisationAccess.OrganisationId, organisationId)): _*
    ).map(_.delete_!).count(identity)
  }

  override def getOrganisationAccessForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataOrganisationAccessT] =
    DynamicDataOrganisationAccess.findAll(whereClauseOrganisationGrantsOfRecord(bankId, entityName, dynamicDataId): _*)

  override def deleteAllForOrganisation(organisationId: String): Box[Boolean] = tryo {
    DynamicDataOrganisationAccess.bulkDelete_!!(By(DynamicDataOrganisationAccess.OrganisationId, organisationId))
  }

  override def deleteAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): Box[Boolean] = tryo {
    DynamicDataOrganisationAccess.findAll(whereClauseOrganisationGrantsOfRecord(bankId, entityName, dynamicDataId): _*).foreach(_.delete_!)
    DynamicDataAccess.findAll(whereClauseBankOrSystemAndEntityAndDataId(bankId, entityName, dynamicDataId): _*).forall(_.delete_!)
  }

  override def deleteAllForEntity(bankId: Option[String], entityName: String): Box[Boolean] = tryo {
    DynamicDataOrganisationAccess.bulkDelete_!!(
      By(DynamicDataOrganisationAccess.BankId, storedBankId(bankId)),
      By(DynamicDataOrganisationAccess.EntityName, entityName)
    )
    DynamicDataAccess.findAll(
      By(DynamicDataAccess.BankId, storedBankId(bankId)),
      By(DynamicDataAccess.EntityName, entityName)
    ).forall(_.delete_!)
  }
}

class DynamicDataAccess extends DynamicDataAccessT with LongKeyedMapper[DynamicDataAccess] with IdPK {

  override def getSingleton = DynamicDataAccess

  object DynamicDataId extends MappedString(this, 255)
  object UserId extends MappedString(this, 255)
  object CanRead extends MappedBoolean(this)
  object CanUpdate extends MappedBoolean(this)
  object CanDelete extends MappedBoolean(this)
  object CanGrant extends MappedBoolean(this)
  object GrantedBy extends MappedString(this, 255)
  object EntityName extends MappedString(this, 255)
  object BankId extends MappedString(this, 255)

  override def dynamicDataId: String = DynamicDataId.get
  override def userId: String = UserId.get
  override def canRead: Boolean = CanRead.get
  override def canUpdate: Boolean = CanUpdate.get
  override def canDelete: Boolean = CanDelete.get
  override def canGrant: Boolean = CanGrant.get
  override def grantedBy: String = GrantedBy.get
  override def entityName: String = EntityName.get
  // A system level ACL row stores the sentinel rather than a SQL NULL; it is filtered back out
  // here so every reader still sees None, exactly as before.
  override def bankId: Option[String] = Option(BankId.get).filterNot(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
}

object DynamicDataAccess extends DynamicDataAccess with LongKeyedMetaMapper[DynamicDataAccess] {
  override def dbIndexes =
    // One ACL row per (space, entity, record, user). DynamicDataId alone does not identify a
    // record -- ids repeat across spaces -- so this index carries the same discriminators, in the
    // same existence order, as DynamicData's own unique index.
    UniqueIndex(BankId, EntityName, DynamicDataId, UserId) ::
    Index(UserId, EntityName, BankId) ::
    Index(DynamicDataId, GrantedBy) ::
    super.dbIndexes
}

/**
 * A grant on one record to an Organisation. Kept in its own table rather than as a DynamicDataAccess row
 * with an empty user id, so that the unique index of each table names exactly who the grant is for.
 */
class DynamicDataOrganisationAccess extends DynamicDataOrganisationAccessT with LongKeyedMapper[DynamicDataOrganisationAccess] with IdPK {

  override def getSingleton = DynamicDataOrganisationAccess

  object DynamicDataId extends MappedString(this, 255)
  object OrganisationId extends MappedString(this, 36)
  object CanRead extends MappedBoolean(this)
  object CanUpdate extends MappedBoolean(this)
  object CanDelete extends MappedBoolean(this)
  object CanGrant extends MappedBoolean(this)
  object GrantedBy extends MappedString(this, 255)
  object EntityName extends MappedString(this, 255)
  object BankId extends MappedString(this, 255)

  override def dynamicDataId: String = DynamicDataId.get
  override def organisationId: String = OrganisationId.get
  override def canRead: Boolean = CanRead.get
  override def canUpdate: Boolean = CanUpdate.get
  override def canDelete: Boolean = CanDelete.get
  override def canGrant: Boolean = CanGrant.get
  override def grantedBy: String = GrantedBy.get
  override def entityName: String = EntityName.get
  override def bankId: Option[String] = Option(BankId.get).filterNot(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
}

object DynamicDataOrganisationAccess extends DynamicDataOrganisationAccess with LongKeyedMetaMapper[DynamicDataOrganisationAccess] {
  override def dbIndexes =
    // One grant per (space, entity, record, Organisation), in the same order as DynamicDataAccess's index.
    UniqueIndex(BankId, EntityName, DynamicDataId, OrganisationId) ::
    Index(OrganisationId, EntityName, BankId) ::
    super.dbIndexes
}
