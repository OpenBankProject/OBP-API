package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType

import java.time.LocalDate
import scala.util.Try

/** What the planner knows about one declared-`indexed` field. */
case class FieldSpec(fieldType: DynamicEntityFieldType, indexKind: String)

/** A contract-layer validation failure (maps to HTTP 400 at the endpoint). */
case class QueryError(message: String)

/**
 * The definition-driven planner (DE_indexing, Phase 1).
 *
 * Validates parsed filter/sort terms against the entity's declared `indexed` fields and the
 * [[OperatorMatrix]], producing a [[QueryPlan]] only if every term is legal. This is the closed
 * allow-list enforced identically on every backend (Shape B): a field must be declared queryable,
 * the operator must be legal for the field's type, and scalar values must coerce to that type.
 *
 * Four checks per the design doc: (1) field indexed? (2) operator legal for type? (3) value
 * coerces? (4) sort field sortable?
 */
object QueryPlanner {

  def plan(
    filters: List[Filter],
    sort: List[SortKey],
    page: Page,
    indexedFields: Map[String, FieldSpec]
  ): Either[QueryError, QueryPlan] =
    for {
      _ <- firstError(filters.map(validateFilter(_, indexedFields)))
      _ <- firstError(sort.map(validateSort(_, indexedFields)))
    } yield QueryPlan(filters, sort, page)

  // ----- per-term validation -----

  private def validateFilter(f: Filter, indexedFields: Map[String, FieldSpec]): Option[QueryError] =
    indexedFields.get(f.field) match {
      case None => Some(QueryError(s"Field '${f.field}' is not queryable (it is not declared indexed)."))
      case Some(spec) =>
        val allowed = OperatorMatrix.allowedOps(spec.fieldType, spec.indexKind)
        if (!allowed.contains(f.op))
          Some(QueryError(s"Operator '${f.op.name}' is not valid for field '${f.field}' of type '${spec.fieldType}'."))
        else
          arityError(f).orElse(coercionError(f, spec))
    }

  private def validateSort(s: SortKey, indexedFields: Map[String, FieldSpec]): Option[QueryError] =
    indexedFields.get(s.field) match {
      case None => Some(QueryError(s"Cannot sort by '${s.field}': it is not declared indexed."))
      case Some(spec) =>
        if (OperatorMatrix.sortable(spec.fieldType, spec.indexKind)) None
        else Some(QueryError(s"Field '${s.field}' of type '${spec.fieldType}' is not sortable."))
    }

  /** Operand count must match the operator. */
  private def arityError(f: Filter): Option[QueryError] = {
    import FilterOp._
    f.op match {
      case Between if f.values.size != 2 => Some(QueryError(s"Operator 'between' on '${f.field}' requires exactly two values."))
      case In if f.values.isEmpty        => Some(QueryError(s"Operator 'in' on '${f.field}' requires at least one value."))
      case _ if FilterOp.spatial.contains(f.op) => None // spatial operand shape validated by the spatial backend
      case _ if f.values.size != 1       => Some(QueryError(s"Operator '${f.op.name}' on '${f.field}' requires exactly one value."))
      case _                             => None
    }
  }

  /** Scalar values must coerce to the declared type (spatial / like operands are not coerced here). */
  private def coercionError(f: Filter, spec: FieldSpec): Option[QueryError] = {
    if (FilterOp.spatial.contains(f.op) || f.op == FilterOp.Like) None
    else f.values.find(v => !coerces(spec.fieldType, v))
      .map(bad => QueryError(s"Value '$bad' is not a valid '${spec.fieldType}' for field '${f.field}'."))
  }

  private def coerces(ft: DynamicEntityFieldType, v: String): Boolean = {
    import DynamicEntityFieldType._
    val s = v.trim
    if (ft == number)            Try(BigDecimal(s)).isSuccess
    else if (ft == integer)      Try(BigInt(s)).isSuccess
    else if (ft == boolean)      s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")
    else if (ft == DATE_WITH_DAY) Try(LocalDate.parse(s)).isSuccess // ISO yyyy-MM-dd
    else true // string and reference types accept any value
  }

  private def firstError(results: List[Option[QueryError]]): Either[QueryError, Unit] =
    results.flatten.headOption.toLeft(())
}
