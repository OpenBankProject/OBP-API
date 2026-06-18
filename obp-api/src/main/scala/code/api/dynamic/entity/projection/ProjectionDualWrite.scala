package code.api.dynamic.entity.projection

import code.api.dynamic.entity.helper.DynamicEntityHelper
import code.api.dynamic.entity.query.OperatorMatrix
import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import org.json4s.JsonAST.JObject

/**
 * Keeps a record's projection row in sync on the write path (DE_indexing, Phase 3). Guarded by
 * `projectionEnabled` and a no-op unless the entity has a `ready` projection — so it changes nothing
 * by default. Uses `DoobieUtil.runQuery`, which reuses Lift's request connection, so the projection
 * upsert/delete participates in the SAME transaction as the canonical blob write (commit/rollback
 * together). Scalar fields only (spatial dual-write is Phase 4).
 */
object ProjectionDualWrite extends MdcLoggable {

  /** Upsert the record's ready indexed scalar columns into the projection (called after the blob save). */
  def onSave(bankId: Option[String], entityName: String, dataId: String, body: JObject): Unit =
    withReadyScalarFields(bankId, entityName) { (safeTable, fields) =>
      val cols = fields.map { case (f, spec) =>
        ProjectionStore.ColumnValue(
          ProjectionNaming.columnName(f),
          ProjectionDDL.sqlColumnType(spec.fieldType.toString),
          ProjectionCoerce.toColumnValue(body \ f, spec.fieldType))
      }
      DoobieUtil.runQuery(ProjectionStore.upsert(safeTable, dataId, cols))
    }

  /** Delete the record's projection row (called after the blob delete; FK cascade is a backstop). */
  def onDelete(bankId: Option[String], entityName: String, dataId: String): Unit =
    withReadyScalarFields(bankId, entityName) { (safeTable, _) =>
      DoobieUtil.runQuery(ProjectionStore.delete(safeTable, dataId))
    }

  private def withReadyScalarFields(bankId: Option[String], entityName: String)
                                   (f: (String, List[(String, code.api.dynamic.entity.query.FieldSpec)]) => Any): Unit = {
    if (!IndexingCapabilities.projectionEnabled) return
    val ready = ProjectionProvisioner.readyFields(bankId, entityName)
    if (ready.isEmpty) return
    val indexed = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).map(_.indexedFields).getOrElse(Map.empty)
    val scalarReady = indexed.toList.filter { case (name, spec) => spec.indexKind != OperatorMatrix.SPATIAL && ready.contains(name) }
    if (scalarReady.nonEmpty) f(ProjectionNaming.tableName(bankId, entityName), scalarReady)
  }
}
