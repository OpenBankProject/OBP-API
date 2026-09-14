/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
