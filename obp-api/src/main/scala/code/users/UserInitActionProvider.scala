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

package code.users

import java.sql.Timestamp

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Full}

/**
 * Doobie implementation of the user-init-action store, replacing the Lift UserInitAction entity.
 *
 * Fired from AfterApiAuth on every login to record one-off "has this user done X yet" flags
 * (create-or-update-bank, add-entitlement, add-bank-account, ...). Every caller discards the
 * return value - only the write matters - so the return type only needs to satisfy the callers
 * that exist, and none of them do.
 *
 * createOrUpdateInitAction is find-then-write on the full (userId, actionName, actionValue)
 * triple: a fresh triple is inserted, an existing one has its success flag and updatedAt
 * refreshed in place. The unique index on that triple is what makes "in place" safe.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object UserInitActionProvider extends MdcLoggable {

  def createOrUpdateInitAction(userId: String, actionName: String, actionValue: String, success: Boolean): Box[UserInitActionTrait] = {
    val now = new Timestamp(System.currentTimeMillis)
    val existing = DoobieUtil.runQuery(
      sql"""SELECT 1 FROM userinitaction
            WHERE userid = $userId AND actionname = $actionName AND actionvalue = $actionValue LIMIT 1"""
        .query[Int].option)

    if (existing.isDefined) {
      DoobieUtil.runUpdate(
        sql"""UPDATE userinitaction SET success = $success, updatedat = $now
              WHERE userid = $userId AND actionname = $actionName AND actionvalue = $actionValue"""
          .update.run)
    } else {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO userinitaction (userid, actionname, actionvalue, success, createdat, updatedat)
              VALUES ($userId, $actionName, $actionValue, $success, $now, $now)"""
          .update.run)
    }
    Full(UserInitActionRow(userId, actionName, actionValue, success))
  }
}
