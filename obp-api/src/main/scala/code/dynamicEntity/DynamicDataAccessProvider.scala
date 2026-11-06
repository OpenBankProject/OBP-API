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

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

/**
 * Row-level access control for Dynamic Entities.
 *
 * When a dynamic entity is defined with `useRowLevelAccess = true`, access to each
 * individual data row is decided by an ACL row in `DynamicDataAccess` rather than by the
 * per-entity owner/community scope. See ideas/DYNAMIC_ENTITY_ROW_LEVEL_ACCESS.md.
 */
object DynamicDataAccessProvider extends SimpleInjector {
  val provider = new Inject(() => buildOne) {}
  def buildOne: MappedDynamicDataAccessProvider.type = MappedDynamicDataAccessProvider
}

/** The four per-row permissions an ACL row can confer. */
sealed trait DynamicDataAccessPermission
object DynamicDataAccessPermission {
  case object Read   extends DynamicDataAccessPermission
  case object Update extends DynamicDataAccessPermission
  case object Delete extends DynamicDataAccessPermission
  case object Grant  extends DynamicDataAccessPermission
}

trait DynamicDataAccessT {
  def dynamicDataId: String
  def userId: String
  def canRead: Boolean
  def canUpdate: Boolean
  def canDelete: Boolean
  def canGrant: Boolean
  def grantedBy: String
  def entityName: String
  def bankId: Option[String]
}

trait DynamicDataAccessProvider {

  /**
   * Upsert one ACL row: grant (or update) `userId`'s permissions on `dynamicDataId`.
   * `grantedBy` records the userId who created the grant, for the revoke cascade.
   */
  def grant(dynamicDataId: String, userId: String,
            canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
            entityName: String, bankId: Option[String], grantedBy: String): Box[DynamicDataAccessT]

  /**
   * Revoke `userId`'s access to `dynamicDataId` AND cascade: every grant transitively
   * made by `userId` on the same row is removed too (walk `grantedBy` with a visited-set
   * so re-share cycles terminate). Returns the number of ACL rows removed.
   */
  def revoke(dynamicDataId: String, userId: String): Box[Int]

  /** All ACL rows for a single data row (for the GET .../access listing). */
  def getAccessForRow(dynamicDataId: String): List[DynamicDataAccessT]

  /** DynamicDataIds of `entityName`/`bankId` that `userId` may read — the get-all filter. */
  def getReadableDynamicDataIds(userId: String, entityName: String, bankId: Option[String]): List[String]

  /** Does `userId` hold `permission` on `dynamicDataId`? */
  def allows(dynamicDataId: String, userId: String, permission: DynamicDataAccessPermission): Boolean

  /** Cascade on row delete: remove every ACL row for the data row. */
  def deleteAllForRow(dynamicDataId: String): Box[Boolean]

  /** Cascade on entity delete: remove every ACL row for the entity/bank. */
  def deleteAllForEntity(entityName: String, bankId: Option[String]): Box[Boolean]
}
