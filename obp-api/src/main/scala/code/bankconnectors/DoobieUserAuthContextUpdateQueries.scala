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

package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

/**
 * Atomic, guarded status transition for `mappeduserauthcontextupdate`.
 *
 * The challenge-answer path checks status == INITIATED then writes ACCEPTED/REJECTED as two
 * separate operations; this collapses them into one conditional UPDATE so two concurrent correct
 * answers cannot both be accepted. Returns affected rows (0 or 1).
 */
object DoobieUserAuthContextUpdateQueries {

  def conditionalStatusTransition(userAuthContextUpdateId: Long, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappeduserauthcontextupdate
            SET mstatus = $newStatus,
                updatedat = NOW()
            WHERE id = $userAuthContextUpdateId
              AND mstatus = $guardStatus""".update.run
    )
}
