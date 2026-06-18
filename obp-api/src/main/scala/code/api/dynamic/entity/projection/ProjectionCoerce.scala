package code.api.dynamic.entity.projection

import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s.JsonAST._

import java.time.LocalDate
import scala.util.Try

/**
 * Coerce a record's JSON field value to the text form bound into its typed projection column
 * (DE_indexing). Returns `None` for missing / not-coercible values → the column is stored as NULL
 * (coerce-or-null: one bad value never aborts a backfill or a write). Mirrors the planner's
 * coercion rules so the SQL projection and the in-memory executor agree on what's queryable.
 */
object ProjectionCoerce {

  def toColumnValue(jv: JValue, ft: DynamicEntityFieldType): Option[String] = {
    import DynamicEntityFieldType._
    def keepIf(cond: Boolean, out: String): Option[String] = if (cond) Some(out) else None
    asText(jv).flatMap { v =>
      val t = v.trim
      if (ft == number)             keepIf(Try(BigDecimal(t)).isSuccess, t)
      else if (ft == integer)       keepIf(Try(BigInt(t)).isSuccess, t)
      else if (ft == boolean)       keepIf(t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false"), t.toLowerCase)
      else if (ft == DATE_WITH_DAY) keepIf(Try(LocalDate.parse(t)).isSuccess, t)
      else                          Some(v) // string + reference types: store as-is
    }
  }

  private def asText(jv: JValue): Option[String] = jv match {
    case JString(s) => Some(s)
    case JInt(i)    => Some(i.toString)
    case JDouble(d) => Some(d.toString)
    case JBool(b)   => Some(b.toString)
    case _          => None // JNothing / JNull / JObject / JArray → NULL
  }
}
