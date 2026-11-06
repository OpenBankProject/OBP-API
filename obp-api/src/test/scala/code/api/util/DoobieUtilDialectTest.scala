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

import code.setup.ServerSetup
import doobie.implicits._

/**
 * The page clause and the row lock are written differently on SQL Server and elsewhere. These scenarios
 * check what DoobieUtil renders for the database under test and that the database accepts it.
 */
class DoobieUtilDialectTest extends ServerSetup {

  private val fiveRows = fr"SELECT id FROM (SELECT 1 AS id UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) rows_under_test ORDER BY id"

  feature("Dialect fragments of DoobieUtil") {

    scenario("The page clause is the form the database under test takes") {
      val rendered = DoobieUtil.pagination(2, 1).internals.sql.trim
      if (DBUtil.isSqlServer) rendered shouldBe "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
      else rendered shouldBe "LIMIT ? OFFSET ?"
    }

    scenario("A page of a query is the rows asked for, in order") {
      val page = DoobieUtil.runQuery((fiveRows ++ fr" " ++ DoobieUtil.pagination(2, 1)).query[Int].to[List])
      page shouldBe List(2, 3)

      val firstPage = DoobieUtil.runQuery((fiveRows ++ fr" " ++ DoobieUtil.pagination(3, 0)).query[Int].to[List])
      firstPage shouldBe List(1, 2, 3)
    }

    scenario("The row lock is a table hint on SQL Server and FOR UPDATE elsewhere, never both") {
      val hint = DoobieUtil.lockHint.internals.sql.trim
      val suffix = DoobieUtil.forUpdate.internals.sql.trim
      if (DBUtil.isSqlServer) {
        hint shouldBe "WITH (UPDLOCK, ROWLOCK)"
        suffix shouldBe ""
      } else {
        hint shouldBe ""
        suffix shouldBe "FOR UPDATE"
      }
    }

    scenario("A locking select with the hint and suffix in place is accepted by the database") {
      val select =
        fr"SELECT musername FROM mappedbadloginattempt" ++ DoobieUtil.lockHint ++
          fr"WHERE provider = ${"no-such-provider"}" ++ DoobieUtil.forUpdate
      DoobieUtil.runUpdate(select.query[String].to[List]) shouldBe Nil
    }
  }
}
