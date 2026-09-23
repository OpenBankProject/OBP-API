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
   * Every method here is scoped by the space and the entity as well as by the record id.
   *
   * A record id is only unique within one space and one entity -- two spaces may each hold a record
   * whose natural key is the country code DE -- so a method that took the record id alone could not
   * tell those two records apart, and an access grant made in one space would be read as a grant in
   * the other. The parameters are ordered the way the things they name come into existence: the
   * bank (the space) first, then the entity defined within it, then the record.
   */

  /**
   * Upsert one ACL row: grant (or update) `userId`'s permissions on the named record.
   * `grantedBy` records the userId who created the grant, for the revoke cascade.
   */
  def grant(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String,
            canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
            grantedBy: String): Box[DynamicDataAccessT]

  /**
   * Revoke `userId`'s access to the named record AND cascade: every grant transitively
   * made by `userId` on the same record is removed too (walk `grantedBy` with a visited-set
   * so re-share cycles terminate). Returns the number of ACL rows removed.
   */
  def revoke(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String): Box[Int]

  /** All ACL rows for a single record (for the GET .../access listing). */
  def getAccessForRow(bankId: Option[String], entityName: String, dynamicDataId: String): List[DynamicDataAccessT]

  /** DynamicDataIds of the entity in this space that `userId` may read -- the get-all filter. */
  def getReadableDynamicDataIds(bankId: Option[String], entityName: String, userId: String): List[String]

  /** Does `userId` hold `permission` on the named record? */
  def allows(bankId: Option[String], entityName: String, dynamicDataId: String, userId: String,
             permission: DynamicDataAccessPermission): Boolean

  /** Cascade on record delete: remove every ACL row for that record. */
  def deleteAllForRow(bankId: Option[String], entityName: String, dynamicDataId: String): Box[Boolean]

  /** Cascade on entity delete: remove every ACL row for the entity in this space. */
  def deleteAllForEntity(bankId: Option[String], entityName: String): Box[Boolean]
}
