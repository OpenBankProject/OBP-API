package code.api.dynamic.entity.projection

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

/**
 * Registry of per-entity projection state (DE_indexing, Approach A). One row per declared `indexed`
 * field, recording the provisioning state machine, the safe (hashed) table/column identifiers, and
 * backfill bookkeeping. The projection *tables* are created by the Doobie provisioner outside any
 * schema tool; this registry is a normal migrated table.
 *
 * The index on (entityname, bankid, fieldname) is plain, not unique, though markReady looks a row
 * up by exactly that triple and inserts when absent — so a concurrent double-provision would leave
 * two rows. Pre-existing; the lookup pins id ASC.
 *
 * Only the columns the provisioner actually reads or writes are modelled. The backfill bookkeeping
 * columns (backfillcheckpoint, rowcountexpected, coercionerrors, lasterror, provisionerversion)
 * exist in the schema but no code path touches them yet, so they are left out of the row rather
 * than carried as always-default fields.
 */
case class DynamicEntityIndex(
  entityName: String,
  bankId: String,
  fieldName: String,
  fieldType: String,
  indexKind: String,
  safeTableName: String,
  safeColumnName: String,
  state: String
)

object DynamicEntityIndex {

  private val selectColumns =
    fr"""SELECT entityname, bankid, fieldname, fieldtype, indexkind, safetablename, safecolumnname,
                state
         FROM dynamicentityindex"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): DynamicEntityIndex = row match {
    case (entityName, bankId, fieldName, fieldType, indexKind, safeTableName, safeColumnName, state) =>
      DynamicEntityIndex(entityName.orNull, bankId.orNull, fieldName.orNull, fieldType.orNull,
        indexKind.orNull, safeTableName.orNull, safeColumnName.orNull, state.orNull)
  }

  private def query(condition: Fragment): List[DynamicEntityIndex] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAllByEntityAndState(entityName: String, bankId: String, state: String): List[DynamicEntityIndex] =
    query(fr"""WHERE entityname = $entityName AND bankid = $bankId AND state = $state
               ORDER BY id ASC""")

  /** Insert or update the one row describing this field's projection column. */
  def markState(entityName: String, bankId: String, fieldName: String, fieldType: String,
                indexKind: String, safeTableName: String, safeColumnName: String,
                state: String): Unit = {
    val existingId = DoobieUtil.runQuery(
      sql"""SELECT id FROM dynamicentityindex
            WHERE entityname = $entityName AND bankid = $bankId AND fieldname = $fieldName
            ORDER BY id ASC LIMIT 1"""
        .query[Long].option)
    existingId match {
      case Some(id) =>
        DoobieUtil.runUpdate(
          sql"""UPDATE dynamicentityindex SET fieldtype = $fieldType, indexkind = $indexKind,
                  safetablename = $safeTableName, safecolumnname = $safeColumnName, state = $state
                WHERE id = $id""".update.run)
      case None =>
        DoobieUtil.runUpdate(
          sql"""INSERT INTO dynamicentityindex
                (entityname, bankid, fieldname, fieldtype, indexkind, safetablename, safecolumnname,
                 state)
                VALUES ($entityName, $bankId, $fieldName, $fieldType, $indexKind, $safeTableName,
                 $safeColumnName, $state)"""
            .update.run)
    }
    ()
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicentityindex".update.run)
    ()
  }
}

/** Provisioning state machine states (see DE_indexing_plan.md). */
object ProjectionState {
  val Provisioning = "provisioning"
  val Backfilling  = "backfilling"
  val Verifying    = "verifying"
  val Ready        = "ready"
  val Failed       = "failed"
  val Retiring     = "retiring"
  val Rebuilding   = "rebuilding"
}
