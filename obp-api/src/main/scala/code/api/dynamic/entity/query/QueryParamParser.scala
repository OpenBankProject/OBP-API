package code.api.dynamic.entity.query

import scala.util.Try

/**
 * Parses Dynamic Entity list-read query params into the raw, unvalidated pieces of a query
 * (DE_indexing, Phase 1). Validation against the entity definition happens in [[QueryPlanner]].
 *
 * Param contract (OBP-prefixed for consistency with the rest of the API):
 *   - pagination : `obp_offset`, `obp_limit`            (offset/limit; no page-number, no total count)
 *   - sort       : `obp_sort_by` (comma-separated for multi-key), `obp_sort_direction` (ASC|DESC)
 *   - filter     : `obp_filter[FIELD]=OP:VALUE`         (repeat per field; repeat the same key for
 *                  multiple constraints on one field). VALUE after the first ':' is opaque; `in`
 *                  and `between` split it on commas. OP is required (e.g. `eq:`), which also lets a
 *                  value legitimately contain ':' (only the first ':' is the separator).
 *
 * This is the one syntax-specific function; everything downstream is syntax-neutral.
 */
object QueryParamParser {

  val Limit            = "obp_limit"
  val Offset           = "obp_offset"
  val SortBy           = "obp_sort_by"
  val SortDirectionKey = "obp_sort_direction"

  private val FilterKey = """^obp_filter\[(.+)\]$""".r

  def parse(params: Map[String, List[String]]): Either[QueryError, (List[Filter], List[SortKey], Page)] =
    for {
      page    <- parsePage(params)
      sort    <- parseSort(params)
      filters <- parseFilters(params)
    } yield (filters, sort, page)

  // ----- pagination -----

  private def parsePage(params: Map[String, List[String]]): Either[QueryError, Page] =
    for {
      limit  <- parseNonNegativeInt(params, Limit)
      offset <- parseNonNegativeInt(params, Offset)
    } yield Page(offset, limit)

  private def parseNonNegativeInt(params: Map[String, List[String]], key: String): Either[QueryError, Option[Int]] =
    firstValue(params, key) match {
      case None => Right(None)
      case Some(raw) => Try(raw.trim.toInt).toOption match {
        case Some(n) if n >= 0 => Right(Some(n))
        case Some(_)           => Left(QueryError(s"$key must be a non-negative integer."))
        case None              => Left(QueryError(s"$key must be an integer."))
      }
    }

  // ----- sort -----

  private def parseSort(params: Map[String, List[String]]): Either[QueryError, List[SortKey]] =
    firstValue(params, SortBy).map(_.trim).filter(_.nonEmpty) match {
      case None => Right(Nil)
      case Some(sortByRaw) =>
        parseDirection(params).map { dir =>
          sortByRaw.split(",").toList.map(_.trim).filter(_.nonEmpty).map(SortKey(_, dir))
        }
    }

  private def parseDirection(params: Map[String, List[String]]): Either[QueryError, SortDirection] =
    firstValue(params, SortDirectionKey).map(_.trim) match {
      case None                                  => Right(SortDirection.Asc)
      case Some(d) if d.equalsIgnoreCase("ASC")  => Right(SortDirection.Asc)
      case Some(d) if d.equalsIgnoreCase("DESC") => Right(SortDirection.Desc)
      case Some(d)                               => Left(QueryError(s"$SortDirection must be ASC or DESC, not '$d'."))
    }

  // ----- filter -----

  private def parseFilters(params: Map[String, List[String]]): Either[QueryError, List[Filter]] = {
    val perKey: List[Either[QueryError, List[Filter]]] = params.toList.collect {
      case (FilterKey(field), values) => traverse(values)(parseOneFilter(field, _))
    }
    sequence(perKey).map(_.flatten)
  }

  private def parseOneFilter(field: String, raw: String): Either[QueryError, Filter] = {
    val idx = raw.indexOf(':')
    if (idx < 0)
      Left(QueryError(s"Filter obp_filter[$field] must be of the form <operator>:<value>, e.g. obp_filter[$field]=eq:..."))
    else {
      val opToken = raw.substring(0, idx)
      val operand = raw.substring(idx + 1)
      FilterOp.byName.get(opToken) match {
        case None =>
          Left(QueryError(s"Unknown filter operator '$opToken' in obp_filter[$field]. Valid operators: ${FilterOp.byName.keys.toList.sorted.mkString(", ")}."))
        case Some(op) =>
          val vals =
            if (op == FilterOp.In || op == FilterOp.Between) operand.split(",", -1).toList.map(_.trim)
            else List(operand)
          Right(Filter(field, op, vals))
      }
    }
  }

  // ----- helpers -----

  private def firstValue(params: Map[String, List[String]], key: String): Option[String] =
    params.get(key).flatMap(_.headOption)

  private def traverse[A, B](xs: List[A])(f: A => Either[QueryError, B]): Either[QueryError, List[B]] =
    xs.foldRight(Right(Nil): Either[QueryError, List[B]]) { (a, acc) =>
      for { b <- f(a); rest <- acc } yield b :: rest
    }

  private def sequence[B](xs: List[Either[QueryError, B]]): Either[QueryError, List[B]] =
    traverse(xs)(identity)
}
