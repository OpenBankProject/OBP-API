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
