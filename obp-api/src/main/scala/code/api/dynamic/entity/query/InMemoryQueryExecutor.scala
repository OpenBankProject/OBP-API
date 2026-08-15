package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s.JsonAST._
import org.json4s.jvalue2monadic

import scala.util.Try

/**
 * Pure, side-effect-free evaluation of a [[QueryPlan]] over an in-memory list of records.
 *
 * This is (a) the implementation behind the in-memory portable-floor backend and (b) the test
 * oracle the projection (SQL) backends are checked against — same plan, same result. It handles
 * only scalar operators; spatial operators cannot be evaluated in memory and are excluded here
 * (the planner rejects spatial against a non-spatial backend before this point).
 *
 * Predictability over cleverness: a record whose field is missing or not coercible to the
 * declared type simply does not match a filter, and sorts last.
 */
object InMemoryQueryExecutor {

  /** Apply filter -> sort -> offset/limit. `fieldTypes` maps fieldName -> declared type. */
  def execute(records: List[JObject], plan: QueryPlan, fieldTypes: Map[String, DynamicEntityFieldType]): List[JObject] = {
    val filtered = records.filter(rec => plan.filters.forall(f => matches(f, rec, typeOf(f.field, fieldTypes))))
    val sorted   = if (plan.sort.isEmpty) filtered else filtered.sortWith((a, b) => compareRecords(a, b, plan.sort, fieldTypes) < 0)
    paginate(sorted, plan.page)
  }

  private def typeOf(field: String, fieldTypes: Map[String, DynamicEntityFieldType]): DynamicEntityFieldType =
    fieldTypes.getOrElse(field, DynamicEntityFieldType.string)

  private def paginate(records: List[JObject], page: Page): List[JObject] = {
    val afterOffset = page.offset.filter(_ > 0).fold(records)(records.drop)
    page.limit.filter(_ >= 0).fold(afterOffset)(afterOffset.take)
  }

  // ----- filtering -----

  private def matches(filter: Filter, record: JObject, ft: DynamicEntityFieldType): Boolean = {
    val jv = record \ filter.field
    import FilterOp._
    filter.op match {
      case Eq | In => filter.values.exists(v => cmp(ft, jv, v).contains(0))
      case Ne      => filter.values.nonEmpty && filter.values.forall(v => cmp(ft, jv, v).exists(_ != 0))
      case Lt      => single(filter).exists(v => cmp(ft, jv, v).exists(_ < 0))
      case Gt      => single(filter).exists(v => cmp(ft, jv, v).exists(_ > 0))
      case Le      => single(filter).exists(v => cmp(ft, jv, v).exists(_ <= 0))
      case Ge      => single(filter).exists(v => cmp(ft, jv, v).exists(_ >= 0))
      case Between => filter.values match {
        case lo :: hi :: _ => cmp(ft, jv, lo).exists(_ >= 0) && cmp(ft, jv, hi).exists(_ <= 0)
        case _             => false
      }
      case Like    => toStringValue(jv).exists(s => filter.values.exists(v => s.toLowerCase.contains(v.toLowerCase)))
      // Value-absence: matches an absent field or an explicit JSON null — mirrors the projection
      // backend, where both an absent field and JSON null project to a NULL column (is_null == not_set).
      case IsNull | NotSet => jv match { case JNothing | JNull => true; case _ => false }
      case _       => false // spatial operators are not evaluable in memory
    }
  }

  private def single(filter: Filter): Option[String] = filter.values.headOption

  /** Sign of record value compared to the operand string, or None if missing / not coercible. */
  private def cmp(ft: DynamicEntityFieldType, jv: JValue, operand: String): Option[Int] =
    if (isNumeric(ft)) for { a <- toBigDecimal(jv); b <- parseBigDecimal(operand) } yield a.compare(b)
    else if (isBoolean(ft)) for { a <- toBoolean(jv); b <- parseBoolean(operand) } yield Ordering.Boolean.compare(a, b)
    else toStringValue(jv).map(_.compareTo(operand)) // string, DATE_WITH_DAY (ISO sorts lexically), reference

  // ----- sorting -----

  private def compareRecords(a: JObject, b: JObject, keys: List[SortKey], fieldTypes: Map[String, DynamicEntityFieldType]): Int =
    keys.iterator.map { k =>
      val c = cmp2(typeOf(k.field, fieldTypes), a \ k.field, b \ k.field)
      k.direction match { case SortDirection.Asc => c; case SortDirection.Desc => -c }
    }.find(_ != 0).getOrElse(0)

  /** Compare two record values; present-and-coercible sorts before missing/uncoercible. */
  private def cmp2(ft: DynamicEntityFieldType, a: JValue, b: JValue): Int =
    if (isNumeric(ft)) compareOpt(toBigDecimal(a), toBigDecimal(b))
    else if (isBoolean(ft)) compareOpt(toBoolean(a), toBoolean(b))
    else compareOpt(toStringValue(a), toStringValue(b))

  private def compareOpt[T](a: Option[T], b: Option[T])(implicit ord: Ordering[T]): Int = (a, b) match {
    case (Some(x), Some(y)) => ord.compare(x, y)
    case (Some(_), None)    => -1 // present before missing
    case (None, Some(_))    => 1
    case (None, None)       => 0
  }

  // ----- type helpers -----

  private def isNumeric(ft: DynamicEntityFieldType): Boolean =
    ft == DynamicEntityFieldType.number || ft == DynamicEntityFieldType.integer
  private def isBoolean(ft: DynamicEntityFieldType): Boolean =
    ft == DynamicEntityFieldType.boolean

  private def toBigDecimal(jv: JValue): Option[BigDecimal] = jv match {
    case JInt(i)    => Some(BigDecimal(i))
    case JDouble(d) => Some(BigDecimal(d))
    case JString(s) => parseBigDecimal(s)
    case _          => None
  }
  private def parseBigDecimal(s: String): Option[BigDecimal] = Try(BigDecimal(s.trim)).toOption

  private def toBoolean(jv: JValue): Option[Boolean] = jv match {
    case JBool(b)   => Some(b)
    case JString(s) => parseBoolean(s)
    case _          => None
  }
  private def parseBoolean(s: String): Option[Boolean] = s.trim.toLowerCase match {
    case "true"  => Some(true)
    case "false" => Some(false)
    case _       => None
  }

  private def toStringValue(jv: JValue): Option[String] = jv match {
    case JString(s) => Some(s)
    case JInt(i)    => Some(i.toString)
    case JDouble(d) => Some(d.toString)
    case JBool(b)   => Some(b.toString)
    case _          => None
  }
}
