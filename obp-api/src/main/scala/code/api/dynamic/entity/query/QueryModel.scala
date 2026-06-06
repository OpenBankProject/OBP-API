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
  // spatial (served only by a spatial-capable backend; never in-memory)
  case object Within     extends FilterOp { val name = "within" }
  case object Contains   extends FilterOp { val name = "contains" }
  case object Intersects extends FilterOp { val name = "intersects" }
  case object DWithin    extends FilterOp { val name = "dwithin" }

  val all: List[FilterOp] =
    List(Eq, Ne, In, Lt, Gt, Le, Ge, Between, Like, Within, Contains, Intersects, DWithin)

  val byName: Map[String, FilterOp] = all.map(op => op.name -> op).toMap

  val spatial: Set[FilterOp] = Set(Within, Contains, Intersects, DWithin)
}

sealed trait SortDirection
object SortDirection {
  case object Asc  extends SortDirection
  case object Desc extends SortDirection
}

/** A single filter predicate: `field op values`. `between` carries two values, `in` carries N, others one. */
case class Filter(field: String, op: FilterOp, values: List[String])

/** A single sort key. */
case class SortKey(field: String, direction: SortDirection)

/** Offset/limit pagination. No page-number / total-count scheme by design (see DE_indexing_plan.md). */
case class Page(offset: Option[Int], limit: Option[Int])
object Page {
  val empty: Page = Page(None, None)
}

/** The fully-parsed, validated query. */
case class QueryPlan(filters: List[Filter], sort: List[SortKey], page: Page)
object QueryPlan {
  val empty: QueryPlan = QueryPlan(Nil, Nil, Page.empty)
}
