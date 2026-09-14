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
 * Atomic, guarded status transitions for `mappedconsent`, used by the HTTP-facing
 * consent state machine (checkAnswer / revoke / skip-SCA accept).
 *
 * Each method is a single conditional UPDATE keyed by the row id with a status guard, so the
 * check and the write cannot interleave across concurrent requests. The returned affected-row
 * count tells the caller whether it won the transition (1) or lost it to a concurrent request (0).
 *
 * `updatedat` is bumped alongside the status so the write matches what the replaced Lift
 * saveMe() (CreatedUpdated trait) persisted.
 */
object DoobieConsentStatusQueries {

  /** Transition mstatus from an expected guard value to a new value, keyed by primary key
   *  (for call sites already holding the loaded MappedConsent). Returns affected rows (0 or 1). */
  def conditionalStatusTransition(consentPrimaryKey: Long, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $newStatus,
                mlastactiondate = NOW(),
                updatedat = NOW()
            WHERE id = $consentPrimaryKey
              AND mstatus = $guardStatus""".update.run
    )

  /** Transition mstatus from an expected guard value to a new value, keyed by consent id.
   *  Used by the skip-SCA auto-accept in the createConsent endpoints (v3.1.0 / v5.0.0 / v5.1.0),
   *  which hold only the consentId — no extra SELECT needed to obtain the primary key.
   *  Returns affected rows (0 or 1). */
  def conditionalStatusTransitionByConsentId(consentId: String, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $newStatus,
                mlastactiondate = NOW(),
                updatedat = NOW()
            WHERE mconsentid = $consentId
              AND mstatus = $guardStatus""".update.run
    )

  /** Revoke unless already at the given terminal status. Returns affected rows (0 or 1). */
  def conditionalRevoke(consentPrimaryKey: Long, revokedStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $revokedStatus,
                mlastactiondate = NOW(),
                updatedat = NOW()
            WHERE id = $consentPrimaryKey
              AND mstatus <> $revokedStatus""".update.run
    )
}
