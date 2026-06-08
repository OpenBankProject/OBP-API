package code.api.dynamic.entity.projection

import net.liftweb.mapper._

/**
 * Registry of per-entity projection state (DE_indexing, Approach A). One row per declared `indexed`
 * field, recording the provisioning state machine, the safe (hashed) table/column identifiers, and
 * backfill bookkeeping. Managed by the provisioner via Doobie DDL — the projection *tables* live
 * outside Lift Schemifier, but this registry itself is a normal Schemifier-managed table.
 *
 * Naming follows project convention: no `Mapped` prefix, columns are plain Capitalised objects.
 */
class DynamicEntityIndex extends LongKeyedMapper[DynamicEntityIndex] with IdPK {
  def getSingleton = DynamicEntityIndex

  object EntityName         extends MappedString(this, 255)
  object BankId             extends MappedString(this, 255)  // "" for system-level entities
  object FieldName          extends MappedString(this, 255)
  object FieldType          extends MappedString(this, 64)   // DynamicEntityFieldType name
  object IndexKind          extends MappedString(this, 32)   // "scalar" | "spatial"
  object SafeTableName      extends MappedString(this, 128)
  object SafeColumnName     extends MappedString(this, 128)
  object State              extends MappedString(this, 32)   // provisioning|backfilling|verifying|ready|failed|retiring|rebuilding
  object BackfillCheckpoint extends MappedString(this, 255)  // resumable cursor (last PK processed)
  object RowCountExpected   extends MappedLong(this)
  object CoercionErrors     extends MappedLong(this)
  object LastError          extends MappedText(this)
  object ProvisionerVersion extends MappedInt(this)
}

object DynamicEntityIndex extends DynamicEntityIndex with LongKeyedMetaMapper[DynamicEntityIndex] {
  override def dbIndexes = Index(EntityName, BankId, FieldName) :: super.dbIndexes
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
