package code.api.dynamic.entity.projection

import code.DynamicData.DynamicData
import doobie._
import doobie.implicits._

/**
 * Data-plane operations for per-entity projection tables (DE_indexing, Approach A): upsert/delete one
 * record's indexed columns, and read canonical blob rows for backfill. Used by the provisioner
 * (backfill) and the dual-write hook. All via Doobie; identifiers are hashed (`Fragment.const`),
 * values bound + `CAST` to the column type (no per-type `Put`, no value-injection).
 *
 * Canonical blob table/column identifiers are taken from the live Lift `DynamicData` mapper so they
 * stay correct regardless of Lift's naming.
 */
object ProjectionStore {

  // Real DB identifiers of the canonical blob table (Lift-mapped).
  val blobTable: String       = DynamicData.dbTableName
  val idColumn: String        = DynamicData.DynamicDataId.dbColumnName
  val jsonColumn: String      = DynamicData.DataJson.dbColumnName
  val entityNameColumn: String = DynamicData.DynamicEntityName.dbColumnName
  val bankIdColumn: String    = DynamicData.BankId.dbColumnName
  val userIdColumn: String    = DynamicData.UserId.dbColumnName
  val personalColumn: String  = DynamicData.IsPersonalEntity.dbColumnName

  // Row-level access ACL table (Lift-mapped), for user-scoped EXISTS / NOT EXISTS join evaluation:
  // a join onto a row-level child counts only child rows the caller can read.
  val aclTable: String         = code.DynamicData.DynamicDataAccess.dbTableName
  val aclDataIdColumn: String  = code.DynamicData.DynamicDataAccess.DynamicDataId.dbColumnName
  val aclUserIdColumn: String  = code.DynamicData.DynamicDataAccess.UserId.dbColumnName
  val aclCanReadColumn: String = code.DynamicData.DynamicDataAccess.CanRead.dbColumnName

  /** One indexed field's value for a record: safe column, SQL type, coerced text (None => NULL). */
  case class ColumnValue(safeColumn: String, sqlType: String, value: Option[String])

  /** INSERT (data_id, cols…) VALUES (?, CAST(? AS t)…) ON CONFLICT (data_id) DO UPDATE … */
  def upsert(safeTable: String, dataId: String, cols: List[ColumnValue]): ConnectionIO[Int] = {
    val colList   = Fragment.const("(data_id" + cols.map(", " + _.safeColumn).mkString + ")")
    val valFrags  = fr0"$dataId" :: cols.map(c => fr"CAST(" ++ fr0"${c.value}" ++ fr" AS" ++ Fragment.const(c.sqlType) ++ fr")")
    val values    = ProjectionSql.intercalate(valFrags, fr",")
    val conflict  =
      if (cols.isEmpty) fr"ON CONFLICT (data_id) DO NOTHING"
      else fr"ON CONFLICT (data_id) DO UPDATE SET" ++
        ProjectionSql.intercalate(cols.map(c => Fragment.const(s"${c.safeColumn} = EXCLUDED.${c.safeColumn}")), fr",")
    (fr"INSERT INTO" ++ Fragment.const(safeTable) ++ colList ++ fr"VALUES (" ++ values ++ fr")" ++ conflict).update.run
  }

  /** DELETE the projection row for a data_id (used by the delete dual-write hook; FK cascade also covers it). */
  def delete(safeTable: String, dataId: String): ConnectionIO[Int] =
    (fr"DELETE FROM" ++ Fragment.const(safeTable) ++ fr"WHERE data_id =" ++ fr0"$dataId").update.run

  /** Read (data_id, dataJson) for every canonical row of an entity, scoped like the connector get-all. */
  def readBlobRows(bankId: Option[String], entityName: String, isPersonalEntity: Boolean, userId: Option[String]): ConnectionIO[List[(String, String)]] =
    (fr"SELECT" ++ Fragment.const(idColumn) ++ fr"," ++ Fragment.const(jsonColumn) ++
      fr"FROM" ++ Fragment.const(blobTable) ++ fr"WHERE" ++ scope(bankId, entityName, isPersonalEntity, userId))
      .query[(String, String)].to[List]

  /**
   * Scope predicate mirroring `MappedDynamicDataProvider`'s get-all: entity name always; bankId via
   * IS NOT DISTINCT FROM (handles system-level NULL); personal flag; userId only when personal.
   * Returned without the `WHERE` keyword so callers can AND it with index predicates.
   */
  def scope(bankId: Option[String], entityName: String, isPersonalEntity: Boolean, userId: Option[String], alias: String = ""): Fragment = {
    val p = if (alias.isEmpty) "" else alias + "."
    // Bind as Option[String]: a system-level entity has bankId=None, which must bind SQL NULL — `orNull`
    // as a plain String trips doobie's non-nullable Put[String] ("oops, null"). Put[Option[String]]
    // emits NULL for None, and `IS NOT DISTINCT FROM NULL` is the intended null-safe match.
    val byEntity   = Fragment.const(p + entityNameColumn) ++ fr"=" ++ fr0"$entityName"
    val byBank     = Fragment.const(p + bankIdColumn) ++ fr"IS NOT DISTINCT FROM" ++ fr0"${bankId: Option[String]}"
    val byPersonal = Fragment.const(p + personalColumn) ++ fr"=" ++ fr0"$isPersonalEntity"
    val base = byEntity ++ fr"AND" ++ byBank ++ fr"AND" ++ byPersonal
    if (isPersonalEntity) base ++ fr"AND" ++ Fragment.const(p + userIdColumn) ++ fr"IS NOT DISTINCT FROM" ++ fr0"${userId: Option[String]}"
    else base
  }
}
