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
 * Atomic, guarded status transitions for business state-machine rows whose Lift updateStatus
 * methods were check-then-write (load row, compare status in memory, saveMe). Each method is a
 * single conditional UPDATE with a status guard, returning the affected-row count so the caller
 * can tell whether it won the transition (1) or lost it to a concurrent request (0).
 *
 * `updatedat` is bumped alongside each write so the UPDATE matches what the replaced Lift
 * saveMe() (CreatedUpdated trait) persisted.
 */
object DoobieBusinessStatusQueries {

  /** AccountAccessRequest: transition only from the guard status. Table has explicit dbTableName. */
  def conditionalAccountAccessRequestStatus(
    accountAccessRequestId: Long,
    guardStatus: String,
    newStatus: String,
    checkerUserId: String,
    checkerComment: String
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE AccountAccessRequest
          SET status = $newStatus,
              checkeruserid = $checkerUserId,
              checkercomment = $checkerComment,
              updatedat = NOW()
          WHERE id = $accountAccessRequestId
            AND status = $guardStatus""".update.run
  )

  /** DynamicChangeRequest (maker/checker for dynamic code): actioned once, from INITIATED. */
  def conditionalDynamicChangeRequestStatus(
    dynamicChangeRequestId: Long,
    guardStatus: String,
    newStatus: String,
    checkerUserId: String,
    checkerComment: String
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE DynamicChangeRequest
          SET status = $newStatus,
              checkeruserid = $checkerUserId,
              checkercomment = $checkerComment,
              actionedat = NOW(),
              updatedat = NOW()
          WHERE id = $dynamicChangeRequestId
            AND status = $guardStatus""".update.run
  )

  /** MappedAccountApplication: transition only from the guard status (a one-shot decision). */
  def conditionalAccountApplicationStatus(accountApplicationId: Long, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedaccountapplication
            SET mstatus = $newStatus,
                updatedat = NOW()
            WHERE id = $accountApplicationId
              AND mstatus = $guardStatus""".update.run
    )

  /** ExpectedChallengeAnswer: compare-and-set the success flag so only one correct answer wins.
   *  Booleans are bound as typed JDBC parameters (not SQL literals) so the driver maps them to the
   *  column type correctly across H2 and Postgres. */
  def conditionalChallengeSuccess(challengeId: String, finalisedScaStatus: String): Int =
    DoobieUtil.runUpdate(
      // NB: Lift MappedBoolean maps the `Successful` field to column `successful_c` (it appends _c).
      sql"""UPDATE ExpectedChallengeAnswer
            SET successful_c = ${true},
                scastatus = $finalisedScaStatus,
                updatedat = NOW()
            WHERE challengeid = $challengeId
              AND successful_c = ${false}""".update.run
    )
}
