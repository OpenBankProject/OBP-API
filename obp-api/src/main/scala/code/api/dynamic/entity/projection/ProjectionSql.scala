package code.api.dynamic.entity.projection

import code.api.dynamic.entity.query._
import doobie._
import doobie.implicits._

/**
 * Compiles a validated [[QueryPlan]] into `SELECT data_id FROM <table> WHERE ... ORDER BY ... LIMIT ? OFFSET ?`
 * for the Postgres projection backend (DE_indexing, Phase 3).
 *
 * Safety: table/column identifiers come hashed from [[ProjectionNaming]] and are injected via
 * `Fragment.const`; every operand is a **bound param**. Operands bind as text and are `CAST` to the
 * column's SQL type in-query, so no per-type Doobie `Put` is needed and there is no injection surface
 * on values. Scalar operators only — spatial (`ST_DWithin`, …) is added in Phase 4.
 *
 * Returns `None` if any referenced field can't be resolved to a column/type or a spatial operator is
 * present (caller then errors / falls back rather than emitting bad SQL).
 */
object ProjectionSql {

  def selectDataIds(
    safeTable: String,
    plan: QueryPlan,
    columnOf: String => Option[String],
    sqlTypeOf: String => Option[String]
  ): Option[Fragment] =
    for {
      preds <- predicates(plan, columnOf, sqlTypeOf)
      ords  <- orderBy(plan, columnOf)
    } yield {
      val whereF = if (preds == Fragment.empty) Fragment.empty else fr"WHERE" ++ preds
      fr"SELECT data_id FROM" ++ Fragment.const(safeTable) ++ whereF ++ ords ++ limitOffset(plan)
    }

  /** AND-joined predicate fragment (no `WHERE` keyword). `Some(Fragment.empty)` if no filters; `None` if unresolvable. */
  def predicates(plan: QueryPlan, columnOf: String => Option[String], sqlTypeOf: String => Option[String]): Option[Fragment] = {
    val ps = plan.filters.map(predicate(_, columnOf, sqlTypeOf))
    if (ps.exists(_.isEmpty)) None
    else Some(ps.flatten match { case Nil => Fragment.empty; case xs => intercalate(xs, fr"AND") })
  }

  /** `ORDER BY ...` fragment (empty if no sort). `None` if a sort field is unresolvable. */
  def orderBy(plan: QueryPlan, columnOf: String => Option[String]): Option[Fragment] = {
    val os = plan.sort.map(s => columnOf(s.field).map(c => Fragment.const(c) ++ (if (s.direction == SortDirection.Desc) fr"DESC" else fr"ASC")))
    if (os.exists(_.isEmpty)) None
    else Some(os.flatten match { case Nil => Fragment.empty; case xs => fr"ORDER BY" ++ intercalate(xs, fr",") })
  }

  /** `LIMIT ? OFFSET ?` fragment (only the parts present). */
  def limitOffset(plan: QueryPlan): Fragment = {
    val limitF  = plan.page.limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = plan.page.offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)
    limitF ++ offsetF
  }

  private def predicate(f: Filter, columnOf: String => Option[String], sqlTypeOf: String => Option[String]): Option[Fragment] =
    if (FilterOp.spatial.contains(f.op)) None
    // Nullary value-absence ops: an absent field projects to a NULL column, so is_null and not_set both
    // compile to `IS NULL` (aliases — see ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md OQ1). No cast / operand.
    else if (FilterOp.nullary.contains(f.op)) columnOf(f.field).map(col => Fragment.const(col) ++ fr"IS NULL")
    else for {
      col     <- columnOf(f.field)
      sqlType <- sqlTypeOf(f.field)
    } yield {
      val c = Fragment.const(col)
      def cast(v: String): Fragment = fr"CAST(" ++ fr0"$v" ++ fr" AS" ++ Fragment.const(sqlType) ++ fr")"
      import FilterOp._
      f.op match {
        case Eq      => c ++ fr"=" ++ cast(f.values.head)
        case Ne      => c ++ fr"<>" ++ cast(f.values.head)
        case Lt      => c ++ fr"<" ++ cast(f.values.head)
        case Gt      => c ++ fr">" ++ cast(f.values.head)
        case Le      => c ++ fr"<=" ++ cast(f.values.head)
        case Ge      => c ++ fr">=" ++ cast(f.values.head)
        case Between => c ++ fr"BETWEEN" ++ cast(f.values(0)) ++ fr"AND" ++ cast(f.values(1))
        case In      => c ++ fr"IN (" ++ intercalate(f.values.map(cast), fr",") ++ fr")"
        case Like    => c ++ fr"ILIKE ('%' ||" ++ fr0"${f.values.head}" ++ fr"|| '%')" // case-insensitive contains, matches in-memory
        case _       => Fragment.empty // unreachable: spatial filtered above
      }
    }

  def intercalate(frags: List[Fragment], sep: Fragment): Fragment =
    frags match {
      case Nil          => Fragment.empty
      case head :: tail => tail.foldLeft(head)((acc, f) => acc ++ sep ++ f)
    }
}
