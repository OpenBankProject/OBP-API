package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType

import java.time.LocalDate
import scala.util.Try

/** What the planner knows about one declared-`indexed` field. */
case class FieldSpec(fieldType: DynamicEntityFieldType, indexKind: String)

/** What the planner needs to know about a join target (child) entity: its indexed fields (for nested
 *  predicate validation) and its declared reference fields (for edge inference). */
case class JoinTargetInfo(indexedFields: Map[String, FieldSpec], referenceFields: Map[String, String])

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
    plan(filters, Nil, sort, page, "", indexedFields, Map.empty, _ => None)

  /**
   * Full planner including one-hop join clauses. `parentReferenceFields` are the queried entity's
   * declared reference fields (for parent→child edges); `childInfoOf` resolves a join-target entity's
   * indexed + reference fields (None if no such entity). Joins are resolved to a concrete link field +
   * direction here, or rejected with a clear 400.
   */
  def plan(
    filters: List[Filter],
    rawJoins: List[RawJoin],
    sort: List[SortKey],
    page: Page,
    parentEntityName: String,
    parentIndexedFields: Map[String, FieldSpec],
    parentReferenceFields: Map[String, String],
    childInfoOf: String => Option[JoinTargetInfo]
  ): Either[QueryError, QueryPlan] =
    for {
      _     <- firstError(filters.map(validateFilter(_, parentIndexedFields)))
      _     <- firstError(sort.map(validateSort(_, parentIndexedFields)))
      joins <- traverse(rawJoins)(resolveJoin(_, parentEntityName, parentReferenceFields, childInfoOf))
    } yield QueryPlan(filters, joins, sort, page)

  // ----- join resolution -----

  private def resolveJoin(
    raw: RawJoin,
    parentEntityName: String,
    parentReferenceFields: Map[String, String],
    childInfoOf: String => Option[JoinTargetInfo]
  ): Either[QueryError, JoinClause] =
    childInfoOf(raw.childEntity) match {
      case None => Left(QueryError(s"Cannot join '${raw.childEntity}': no such Dynamic Entity."))
      case Some(childInfo) =>
        // Candidate edges: a child field referencing the parent (onChild=true), or a parent field
        // referencing the child (onChild=false). Edge = a declared `reference:` field only.
        val childToParent = childInfo.referenceFields.collect { case (f, t) if t == parentEntityName => (f, true) }.toList
        val parentToChild = parentReferenceFields.collect { case (f, t) if t == raw.childEntity => (f, false) }.toList
        val candidates    = childToParent ++ parentToChild
        for {
          edge <- selectEdge(raw, candidates)
          // Nested predicate validates against the CHILD's indexed fields.
          _    <- firstError(raw.predicate.map(validateFilter(_, childInfo.indexedFields)))
        } yield JoinClause(raw.quantifier, raw.childEntity, edge._1, edge._2, raw.predicate)
    }

  private def selectEdge(raw: RawJoin, candidates: List[(String, Boolean)]): Either[QueryError, (String, Boolean)] =
    raw.via match {
      case Some(field) =>
        candidates.filter(_._1 == field) match {
          case single :: Nil => Right(single)
          case Nil =>
            Left(QueryError(s"No reference field '$field' links '${raw.childEntity}' to the queried entity." +
              candidateHint(raw, candidates)))
          case _ => Left(QueryError(s"Ambiguous link field '$field' for join with '${raw.childEntity}'."))
        }
      case None =>
        candidates match {
          case single :: Nil => Right(single)
          case Nil           => Left(QueryError(s"Cannot join '${raw.childEntity}': no declared reference links it to the queried entity. " +
                                  "A join edge must be a field typed 'reference:<Entity>'."))
          case many          => Left(QueryError(s"Ambiguous join with '${raw.childEntity}': multiple reference edges " +
                                  s"(${many.map(_._1).mkString(", ")}). Specify via:<field>."))
        }
    }

  private def candidateHint(raw: RawJoin, candidates: List[(String, Boolean)]): String =
    if (candidates.isEmpty) "" else s" Candidates: ${candidates.map(_._1).mkString(", ")}."

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
      case _ if FilterOp.nullary.contains(f.op) =>
        if (f.values.nonEmpty) Some(QueryError(s"Operator '${f.op.name}' on '${f.field}' takes no value.")) else None
      case Between if f.values.size != 2 => Some(QueryError(s"Operator 'between' on '${f.field}' requires exactly two values."))
      case In if f.values.isEmpty        => Some(QueryError(s"Operator 'in' on '${f.field}' requires at least one value."))
      case In                            => None
      case _ if FilterOp.spatial.contains(f.op) => None // spatial operand shape validated by the spatial backend
      case _ if f.values.size != 1       => Some(QueryError(s"Operator '${f.op.name}' on '${f.field}' requires exactly one value."))
      case _                             => None
    }
  }

  /** Scalar values must coerce to the declared type (spatial / like / nullary operands are not coerced here). */
  private def coercionError(f: Filter, spec: FieldSpec): Option[QueryError] = {
    if (FilterOp.spatial.contains(f.op) || f.op == FilterOp.Like || FilterOp.nullary.contains(f.op)) None
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

  private def traverse[A, B](xs: List[A])(f: A => Either[QueryError, B]): Either[QueryError, List[B]] =
    xs.foldRight(Right(Nil): Either[QueryError, List[B]]) { (a, acc) =>
      for { b <- f(a); rest <- acc } yield b :: rest
    }
}
