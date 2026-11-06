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

package code.api.util

import doobie._
import doobie.implicits._

/**
 * Common Doobie queries used across OBP-API.
 *
 * These replace raw SQL queries that were using Lift's DB.runQuery (via DBUtil.runQuery).
 * Doobie provides type-safe query results and proper JDBC type handling for all databases,
 * including SQL Server's NVARCHAR type which Lift doesn't handle correctly.
 */
object DoobieQueries {

  /**
   * Get distinct providers from the resourceuser table.
   * Used by ResourceUser.getDistinctProviders
   *
   * @return List of distinct provider names, sorted alphabetically
   */
  def getDistinctProviders: List[String] = {
    val query: ConnectionIO[List[String]] =
      sql"""SELECT DISTINCT provider_ FROM resourceuser WHERE provider_ IS NOT NULL ORDER BY provider_"""
        .query[String]
        .to[List]

    DoobieUtil.runQuery(query)
  }
}
