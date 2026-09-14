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

package code.api.dynamic.entity.projection

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Deterministic, collision-resistant, length-safe SQL identifiers for per-entity projection tables
 * (DE_indexing, Approach A). User-supplied entity/field names are NEVER used raw in DDL — they are
 * sanitised + hashed here, then injected via `Fragment.const`. Doobie binds values but not
 * identifiers, so identifier safety rests entirely on this object.
 *
 * Output stays well under both Postgres (63) and SQL Server (128) identifier limits.
 */
object ProjectionNaming {

  private def hash(s: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(StandardCharsets.UTF_8))
      .take(6).map(b => "%02x".format(b & 0xff)).mkString // 12 hex chars

  private def sanitize(s: String, max: Int): String = {
    val cleaned = s.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "")
    if (cleaned.length > max) cleaned.substring(0, max) else cleaned
  }

  private def entityKey(bankId: Option[String], entityName: String): String =
    bankId.getOrElse("") + ":" + entityName

  /** Stable projection table name for an entity (system- or bank-level). e.g. `de_parcel_a1b2c3d4e5f6`. */
  def tableName(bankId: Option[String], entityName: String): String =
    s"de_${sanitize(entityName, 24)}_${hash(entityKey(bankId, entityName))}"

  /** Stable column name for an indexed field within its entity's table. e.g. `c_price_9f8e7d6c5b4a`. */
  def columnName(fieldName: String): String =
    s"c_${sanitize(fieldName, 24)}_${hash(fieldName)}"

  /** Stable index name for a column. */
  def indexName(safeTable: String, safeColumn: String): String =
    s"idx_${safeTable}_$safeColumn"
}
