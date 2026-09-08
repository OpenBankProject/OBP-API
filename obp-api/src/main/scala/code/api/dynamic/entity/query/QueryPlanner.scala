package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType

import java.time.LocalDate
import scala.util.Try

/** What the planner knows about one declared-`indexed` field. */
case class FieldSpec(fieldType: DynamicEntityFieldType, indexKind: String)

/** What the planner needs to know about a join target (child) entity: its indexed fields (for nested
 *  predicate validation), its indexed reference fields (for edge inference), and its reference fields
 *  that are declared but not indexed (only used to word a precise rejection). */
case class JoinTargetInfo(
  indexedFields: Map[String, FieldSpec],
  referenceFields: Map[String, String],
  unindexedReferenceFields: Map[String, String] = Map.empty
)

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
   * declared and indexed reference fields (for parent→child edges); `childInfoOf` resolves a join-target
   * entity's indexed + reference fields (None if no such entity); `parentUnindexedReferenceFields` are
   * the queried entity's reference fields that are declared but not indexed, used only to word a
   * precise rejection. Joins are resolved to a concrete link field + direction here, or rejected with
   * a clear 400.
   */
  def plan(
    filters: List[Filter],
    rawJoins: List[RawJoin],
    sort: List[SortKey],
    page: Page,
    parentEntityName: String,
    parentIndexedFields: Map[String, FieldSpec],
    parentReferenceFields: Map[String, String],
    childInfoOf: String => Option[JoinTargetInfo],
    parentUnindexedReferenceFields: Map[String, String] = Map.empty
  ): Either[QueryError, QueryPlan] =
    for {
      _     <- firstError(filters.map(validateFilter(_, parentIndexedFields)))
      _     <- firstError(sort.map(validateSort(_, parentIndexedFields)))
      _     <- if (rawJoins.nonEmpty && parentIndexedFields.isEmpty) Left(parentNotIndexed(parentEntityName)) else Right(())
      joins <- traverse(rawJoins)(resolveJoin(_, parentEntityName, parentReferenceFields, parentUnindexedReferenceFields, childInfoOf))
    } yield QueryPlan(filters, joins, sort, page)

  // ----- join resolution -----

  /** A reference field that could have been a join edge but is not `indexed`: (field, onChild). */
  private type UnindexedEdge = (String, Boolean)

  private def resolveJoin(
    raw: RawJoin,
    parentEntityName: String,
    parentReferenceFields: Map[String, String],
    parentUnindexedReferenceFields: Map[String, String],
    childInfoOf: String => Option[JoinTargetInfo]
  ): Either[QueryError, JoinClause] =
    childInfoOf(raw.childEntity) match {
      case None => Left(QueryError(s"Cannot join '${raw.childEntity}': no such Dynamic Entity."))
      case Some(childInfo) =>
        // Candidate edges: a child field referencing the parent (onChild=true), or a parent field
        // referencing the child (onChild=false). Edge = a declared AND indexed `reference:` field only.
        val childToParent = childInfo.referenceFields.collect { case (f, t) if t == parentEntityName => (f, true) }.toList
        val parentToChild = parentReferenceFields.collect { case (f, t) if t == raw.childEntity => (f, false) }.toList
        val candidates    = childToParent ++ parentToChild
        // Declared-but-unindexed references in either direction: not edges, but the reason to report.
        val unindexed: List[UnindexedEdge] =
          childInfo.unindexedReferenceFields.collect { case (f, t) if t == parentEntityName => (f, true) }.toList ++
          parentUnindexedReferenceFields.collect { case (f, t) if t == raw.childEntity => (f, false) }.toList
        for {
          edge <- selectEdge(raw, parentEntityName, candidates, unindexed)
          // Nested predicate validates against the CHILD's indexed fields.
          _    <- firstError(raw.predicate.map(validateFilter(_, childInfo.indexedFields)))
        } yield JoinClause(raw.quantifier, raw.childEntity, edge._1, edge._2, raw.predicate)
    }

  private def selectEdge(
    raw: RawJoin,
    parentEntityName: String,
    candidates: List[(String, Boolean)],
    unindexed: List[UnindexedEdge]
  ): Either[QueryError, (String, Boolean)] =
    raw.via match {
      case Some(field) =>
        candidates.filter(_._1 == field) match {
          case single :: Nil => Right(single)
          case Nil =>
            unindexed.find(_._1 == field) match {
              case Some(u) => Left(unindexedEdgeError(raw, parentEntityName, List(u)))
              case None =>
                Left(QueryError(s"No reference field '$field' links '${raw.childEntity}' to the queried entity." +
                  candidateHint(raw, candidates)))
            }
          case _ => Left(QueryError(s"Ambiguous link field '$field' for join with '${raw.childEntity}'."))
        }
      case None =>
        candidates match {
          case single :: Nil => Right(single)
          case Nil if unindexed.nonEmpty => Left(unindexedEdgeError(raw, parentEntityName, unindexed))
          case Nil           => Left(QueryError(s"Cannot join '${raw.childEntity}': no declared reference links it to the queried entity. " +
                                  "A join edge must be a field typed 'reference:<Entity>' and declared \"indexed\": true."))
          case many          => Left(QueryError(s"Ambiguous join with '${raw.childEntity}': multiple reference edges " +
                                  s"(${many.map(_._1).mkString(", ")}). Specify via:<field>."))
        }
    }

  /** The reference exists but is not indexed: say exactly which field on which entity needs `"indexed": true`. */
  private def unindexedEdgeError(raw: RawJoin, parentEntityName: String, unindexed: List[UnindexedEdge]): QueryError =
    unindexed match {
      case (field, onChild) :: Nil =>
        val (owner, target) = if (onChild) (raw.childEntity, parentEntityName) else (parentEntityName, raw.childEntity)
        QueryError(s"Cannot join '${raw.childEntity}' via '$field': the field '$field' on '$owner' is typed 'reference:$target' " +
          s"but is not declared \"indexed\": true. Add \"indexed\": true to that field on '$owner' " +
          "(and to any field used in the nested filter) and let the index build.")
      case many =>
        val described = many.map { case (field, onChild) => s"'$field' on '${if (onChild) raw.childEntity else parentEntityName}'" }
        QueryError(s"Cannot join '${raw.childEntity}': the reference fields linking it to the queried entity " +
          s"(${described.mkString(", ")}) are not declared \"indexed\": true. Add \"indexed\": true to the one you " +
          "want to join on (and to any field used in the nested filter), let the index build, then specify via:<field>.")
    }

  /** Joins run on the SQL projection, which only exists for entities with at least one indexed field. */
  private def parentNotIndexed(parentEntityName: String): QueryError =
    QueryError(s"Cannot join from '$parentEntityName': none of its fields are declared \"indexed\": true, so it has no " +
      s"SQL projection to join on. Declare at least one field on '$parentEntityName' as \"indexed\": true and let the index build.")

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
