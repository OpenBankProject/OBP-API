package code.api.dynamic.entity.query

/**
 * Abstract, backend-neutral query model for Dynamic Entity list reads (DE_indexing, Phase 0).
 *
 * A [[QueryPlan]] is produced by the planner from request query params (filter/sort/paginate),
 * validated against the entity definition's declared `indexed` fields, then handed to a
 * [[DynamicEntityQueryBackend]] which compiles it to its own dialect (in-memory, Postgres SQL, ...).
 *
 * Filter operand values are kept as raw strings here (as received from query params); typed
 * coercion against the field's declared type happens inside each backend, so the model itself
 * carries no vendor- or type-specific representation.
 */

sealed trait FilterOp { def name: String }
object FilterOp {
  // scalar
  case object Eq      extends FilterOp { val name = "eq" }
  case object Ne      extends FilterOp { val name = "ne" }
  case object In      extends FilterOp { val name = "in" }
  case object Lt      extends FilterOp { val name = "lt" }
  case object Gt      extends FilterOp { val name = "gt" }
  case object Le      extends FilterOp { val name = "le" }
  case object Ge      extends FilterOp { val name = "ge" }
  case object Between extends FilterOp { val name = "between" }
  case object Like    extends FilterOp { val name = "like" }
  // value-absence (zero operands). is_null and not_set are aliases at the projection layer — an absent
  // field and a JSON-null both project to a NULL column, so both compile to SQL `IS NULL` (see
  // ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md OQ1). Kept as two names for caller intent / future split.
  case object IsNull extends FilterOp { val name = "is_null" }
  case object NotSet extends FilterOp { val name = "not_set" }
  // spatial (served only by a spatial-capable backend; never in-memory)
  case object Within     extends FilterOp { val name = "within" }
  case object Contains   extends FilterOp { val name = "contains" }
  case object Intersects extends FilterOp { val name = "intersects" }
  case object DWithin    extends FilterOp { val name = "dwithin" }

  val all: List[FilterOp] =
    List(Eq, Ne, In, Lt, Gt, Le, Ge, Between, Like, IsNull, NotSet, Within, Contains, Intersects, DWithin)

  val byName: Map[String, FilterOp] = all.map(op => op.name -> op).toMap

  val spatial: Set[FilterOp] = Set(Within, Contains, Intersects, DWithin)

  /** Operators taking no operand. */
  val nullary: Set[FilterOp] = Set(IsNull, NotSet)
}

sealed trait SortDirection
object SortDirection {
  case object Asc  extends SortDirection
  case object Desc extends SortDirection
}

/** A single filter predicate: `field op values`. `between` carries two values, `in` carries N, `is_null`/`not_set` zero, others one. */
case class Filter(field: String, op: FilterOp, values: List[String])

/** EXISTS vs NOT EXISTS for a one-hop join clause. */
sealed trait Quantifier
object Quantifier {
  case object Exists    extends Quantifier
  case object NotExists extends Quantifier
}

/**
 * A one-hop existence constraint relating the queried (parent) entity to a related (child) entity via a
 * declared `reference:` field. Resolved (link field + direction concrete) by the planner.
 *   childEntity : the related entity's name
 *   linkField   : the reference field carrying the edge
 *   onChild     : true if linkField lives on the child (child references parent); false if on the parent
 *   predicate   : nested scalar filters on the child, AND-composed (may be empty)
 * See ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md.
 */
case class JoinClause(
  quantifier: Quantifier,
  childEntity: String,
  linkField: String,
  onChild: Boolean,
  predicate: List[Filter]
)

/**
 * A join clause as parsed from query params, before edge resolution: the link field (`via`) and
 * direction are not yet known (the planner resolves them from the entity definitions).
 */
case class RawJoin(quantifier: Quantifier, childEntity: String, via: Option[String], predicate: List[Filter])

/** A single sort key. */
case class SortKey(field: String, direction: SortDirection)

/** Offset/limit pagination. No page-number / total-count scheme by design (see DE_indexing_plan.md). */
case class Page(offset: Option[Int], limit: Option[Int])
object Page {
  val empty: Page = Page(None, None)
}

/** The fully-parsed, validated query. `joins` are one-hop EXISTS/NOT EXISTS constraints (projection backend only). */
case class QueryPlan(filters: List[Filter], joins: List[JoinClause], sort: List[SortKey], page: Page)
object QueryPlan {
  val empty: QueryPlan = QueryPlan(Nil, Nil, Nil, Page.empty)
  /** No-join convenience constructor (the common case; in-memory executor never uses joins). */
  def apply(filters: List[Filter], sort: List[SortKey], page: Page): QueryPlan = QueryPlan(filters, Nil, sort, page)
}
