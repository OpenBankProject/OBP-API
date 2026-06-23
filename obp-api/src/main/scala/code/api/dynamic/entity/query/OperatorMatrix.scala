package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType

/**
 * The closed allow-list of which [[FilterOp]]s are legal for each Dynamic Entity field type
 * (and whether the type is sortable), plus the index kind ("scalar" | "spatial").
 *
 * This is the contract-layer rule the planner enforces — identical on every backend (Shape B),
 * so a query is accepted/rejected the same way regardless of the underlying database. Anything
 * not explicitly permitted here is rejected; new types/operators stay rejected until added.
 */
object OperatorMatrix {
  import DynamicEntityFieldType._
  import FilterOp._

  val SCALAR  = "scalar"
  val SPATIAL = "spatial"

  // Value-absence ops are legal for every non-json type (json is never a plain scalar column).
  private val nullOps:    Set[FilterOp] = Set(IsNull, NotSet)
  private val numericOps: Set[FilterOp] = Set(Eq, Ne, In, Lt, Gt, Le, Ge, Between) ++ nullOps
  private val dateOps:    Set[FilterOp] = Set(Eq, Ne, In, Lt, Gt, Le, Ge, Between) ++ nullOps
  private val stringOps:  Set[FilterOp] = Set(Eq, Ne, In, Like) ++ nullOps
  private val boolOps:    Set[FilterOp] = Set(Eq, Ne) ++ nullOps

  /** Operators permitted for a field of this type + index kind. Empty = field is not filterable. */
  def allowedOps(fieldType: DynamicEntityFieldType, indexKind: String): Set[FilterOp] =
    (fieldType, indexKind) match {
      case (`json`, SPATIAL)        => spatial
      case (`json`, _)              => Set.empty             // non-spatial json is never filterable
      case (`number`, _)            => numericOps
      case (`integer`, _)           => numericOps
      case (`DATE_WITH_DAY`, _)     => dateOps
      case (`boolean`, _)           => boolOps
      case (`string`, _)            => stringOps
      case _                        => stringOps             // reference types behave like string ids
    }

  /** Whether a field of this type + index kind may appear in `obp_sort_by`. */
  def sortable(fieldType: DynamicEntityFieldType, indexKind: String): Boolean =
    (fieldType, indexKind) match {
      case (`json`, _)    => false   // neither whole-json nor geometry is orderable
      case (`boolean`, _) => false   // ordering booleans is meaningless — keep it simple
      case _              => true
    }
}
