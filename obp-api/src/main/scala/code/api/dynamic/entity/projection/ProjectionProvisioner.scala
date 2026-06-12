package code.api.dynamic.entity.projection

import cats.effect.IO
import cats.implicits._
import code.api.dynamic.entity.helper.DynamicEntityHelper
import code.api.dynamic.entity.query.{FieldSpec, OperatorMatrix}
import code.util.Helper.MdcLoggable
import net.liftweb.mapper.By

/**
 * Provisions per-entity projection tables for an entity's declared `indexed` scalar fields
 * (DE_indexing, Approach A, Phase 3): create table → add columns → backfill from the canonical blob
 * (coerce-or-null) → build indexes → mark fields `ready` in the [[DynamicEntityIndex]] registry.
 *
 * Phase 3 covers scalar fields; spatial (`index: spatial`) is provisioned in Phase 4. Backfill is
 * app-side (parse each blob, coerce per type) — simple and predictable; batched/resumable backfill
 * and `CREATE INDEX CONCURRENTLY` are production refinements.
 */
object ProjectionProvisioner extends MdcLoggable {

  /** Ensure the projection for (bankId, entityName) exists, is backfilled, indexed and marked ready.
   *  Reads the entity's indexed fields from the (committed) definition map. */
  def ensureProvisioned(bankId: Option[String], entityName: String, isPersonalEntity: Boolean = false, userId: Option[String] = None): IO[Unit] =
    ensureProvisionedFields(bankId, entityName, indexedScalarFields(bankId, entityName), isPersonalEntity, userId)

  /** As [[ensureProvisioned]] but with the indexed scalar fields passed in explicitly — used at
   *  definition-create time, where the new definition isn't committed/visible in the map yet. */
  def ensureProvisionedFields(bankId: Option[String], entityName: String, indexed: List[(String, FieldSpec)],
                              isPersonalEntity: Boolean = false, userId: Option[String] = None): IO[Unit] = {
    if (indexed.isEmpty) IO.unit
    else {
      val safeTable = ProjectionNaming.tableName(bankId, entityName)
      for {
        _ <- ProjectionDDL.createTableIO(safeTable)
        _ <- indexed.traverse_ { case (f, spec) =>
               ProjectionDDL.addColumnIO(safeTable, ProjectionNaming.columnName(f), ProjectionDDL.sqlColumnType(spec.fieldType.toString))
             }
        _ <- backfill(bankId, entityName, isPersonalEntity, userId, safeTable, indexed)
        _ <- indexed.traverse_ { case (f, _) => ProjectionDDL.createIndexIO(safeTable, ProjectionNaming.columnName(f)) }
        _ <- IO(markReady(bankId, entityName, indexed))
      } yield ()
    }
  }

  /** Filter a definition's indexed fields to the scalar ones this phase provisions (spatial = Phase 4). */
  def scalarFieldsOf(indexed: Map[String, FieldSpec]): List[(String, FieldSpec)] =
    indexed.toList.filter(_._2.indexKind != OperatorMatrix.SPATIAL)

  /** Field names whose projection column is `ready` (used by backend selection). */
  def readyFields(bankId: Option[String], entityName: String): Set[String] =
    DynamicEntityIndex.findAll(
      By(DynamicEntityIndex.EntityName, entityName),
      By(DynamicEntityIndex.BankId, bankId.getOrElse("")),
      By(DynamicEntityIndex.State, ProjectionState.Ready)
    ).map(_.FieldName.get).toSet

  // ----- internals -----

  private def indexedScalarFields(bankId: Option[String], entityName: String): List[(String, FieldSpec)] =
    DynamicEntityHelper.definitionsMap.get((bankId, entityName))
      .map(_.indexedFields).getOrElse(Map.empty)
      .toList.filter(_._2.indexKind != OperatorMatrix.SPATIAL)

  private def backfill(bankId: Option[String], entityName: String, isPersonalEntity: Boolean, userId: Option[String],
                       safeTable: String, fields: List[(String, FieldSpec)]): IO[Unit] =
    for {
      rows <- ProjectionDb.run(ProjectionStore.readBlobRows(bankId, entityName, isPersonalEntity, userId))
      _    <- rows.traverse_ { case (id, jsonStr) =>
                val obj = com.openbankproject.commons.util.JsonAliases.parse(jsonStr)
                val cols = fields.map { case (f, spec) =>
                  ProjectionStore.ColumnValue(
                    ProjectionNaming.columnName(f),
                    ProjectionDDL.sqlColumnType(spec.fieldType.toString),
                    ProjectionCoerce.toColumnValue(obj \ f, spec.fieldType))
                }
                ProjectionDb.run(ProjectionStore.upsert(safeTable, id, cols))
              }
    } yield ()

  private def markReady(bankId: Option[String], entityName: String, fields: List[(String, FieldSpec)]): Unit =
    fields.foreach { case (f, spec) =>
      val row = DynamicEntityIndex.find(
        By(DynamicEntityIndex.EntityName, entityName),
        By(DynamicEntityIndex.BankId, bankId.getOrElse("")),
        By(DynamicEntityIndex.FieldName, f)
      ).openOr(DynamicEntityIndex.create)
      row.EntityName(entityName).BankId(bankId.getOrElse(""))
        .FieldName(f).FieldType(spec.fieldType.toString).IndexKind(spec.indexKind)
        .SafeTableName(ProjectionNaming.tableName(bankId, entityName))
        .SafeColumnName(ProjectionNaming.columnName(f))
        .State(ProjectionState.Ready)
        .save
    }
}
