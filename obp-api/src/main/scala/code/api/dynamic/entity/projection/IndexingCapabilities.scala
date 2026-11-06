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

import code.api.util.{APIUtil, DBUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._

import scala.util.Try

/**
 * Startup capability detection for the DE projection backend (Shape B selection).
 *
 * Decides, once, which backend can run: the SQL projection (Approach A) is only used when the
 * vendor supports it AND an operator opts in via prop; otherwise the in-memory portable floor is
 * used. Spatial requires the PostGIS *extension*, not merely Postgres — a finer check than the
 * generic JSON accelerator.
 */
object IndexingCapabilities extends MdcLoggable {

  sealed trait Vendor
  case object Postgres    extends Vendor
  case object SqlServer   extends Vendor
  case object OtherVendor extends Vendor

  lazy val vendor: Vendor =
    if (Try(DoobieUtil.isSqlServer).getOrElse(false)) SqlServer
    else if (Try(DBUtil.dbUrl).getOrElse("").toLowerCase.contains("postgresql")) Postgres
    else OtherVendor

  /** True only on Postgres with the PostGIS extension installed. Probed once; failures => false. */
  lazy val postgisAvailable: Boolean = vendor match {
    case Postgres =>
      Try {
        DoobieUtil.runQuery(sql"SELECT 1 FROM pg_extension WHERE extname = 'postgis'".query[Int].option).isDefined
      }.getOrElse(false)
    case _ => false
  }

  /**
   * Operator kill-switch `dynamic_entity.indexing.backend`:
   *   - "inmemory" (default) -> always the in-memory portable floor (no projection / DDL)
   *   - "auto"               -> use the SQL projection backend where the vendor supports it
   */
  def backendMode: String = APIUtil.getPropsValue("dynamic_entity.indexing.backend", "inmemory")

  /** Whether the SQL projection backend (and its automatic DDL) is enabled for this deployment. */
  def projectionEnabled: Boolean =
    backendMode.equalsIgnoreCase("auto") && (vendor == Postgres || vendor == SqlServer)
}
