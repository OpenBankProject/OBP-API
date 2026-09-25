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

import code.api.dynamic.entity.helper.DynamicEntityHelper
import code.api.dynamic.entity.query.OperatorMatrix
import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import org.json4s.JsonAST.JObject

/**
 * Keeps a record's projection row in sync on the write path (DE_indexing, Phase 3). Guarded by
 * `projectionEnabled` and a no-op unless the entity has a `ready` projection — so it changes nothing
 * by default. Uses `DoobieUtil.runUpdate` so the INSERT is committed even when called outside an
 * explicit request-scope transaction (e.g. dynamic-entity POST handlers that don't wrap in
 * `withBusinessDBTransaction`). When a request-scope proxy IS present, `runUpdate` still reuses it
 * via `transactorFromConnection`. Scalar fields only (spatial dual-write is Phase 4).
 */
object ProjectionDualWrite extends MdcLoggable {

  def onSave(bankId: Option[String], entityName: String, dataId: String, body: JObject): Unit =
    withReadyScalarFields(bankId, entityName) { (safeTable, fields) =>
      val cols = fields.map { case (f, spec) =>
        ProjectionStore.ColumnValue(
          ProjectionNaming.columnName(f),
          ProjectionDDL.sqlColumnType(spec.fieldType.toString),
          ProjectionCoerce.toColumnValue(body \ f, spec.fieldType))
      }
      DoobieUtil.runUpdate(ProjectionStore.upsert(safeTable, dataId, cols))
    }

  def onDelete(bankId: Option[String], entityName: String, dataId: String): Unit =
    withReadyScalarFields(bankId, entityName) { (safeTable, _) =>
      DoobieUtil.runUpdate(ProjectionStore.delete(safeTable, dataId))
    }

  private def withReadyScalarFields(bankId: Option[String], entityName: String)
                                   (f: (String, List[(String, code.api.dynamic.entity.query.FieldSpec)]) => Any): Unit = {
    if (!IndexingCapabilities.projectionEnabled) return
    val ready = ProjectionProvisioner.readyFields(bankId, entityName)
    if (ready.isEmpty) return
    val indexed = DynamicEntityHelper.definitionOf(bankId, entityName).map(_.indexedFields).getOrElse(Map.empty)
    val scalarReady = indexed.toList.filter { case (name, spec) => spec.indexKind != OperatorMatrix.SPATIAL && ready.contains(name) }
    if (scalarReady.nonEmpty) f(ProjectionNaming.tableName(bankId, entityName), scalarReady)
  }
}
