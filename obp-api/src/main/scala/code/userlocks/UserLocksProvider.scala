package code.userlocks

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import code.users.Users
import code.util.Helper.MdcLoggable
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers._

/** One lock row, standing in for the Lift UserLocks entity in return types. */
case class UserLockRow(userId: String, typeOfLock: String, lastLockDate: Date) extends UserLocksTrait

trait UserLocksTrait {
  def userId: String
  def typeOfLock: String
  def lastLockDate: Date
}

/**
 * Doobie implementation of the user-lock store, replacing the Lift UserLocks entity.
 *
 * Every method still starts by resolving provider+username to a user and returns Empty when that
 * fails - the endpoints turn that Empty into a 404, so it is not an internal detail.
 *
 * lockUser keeps the upsert shape of the Mapper version: refresh the timestamp on an existing
 * lock, otherwise insert with typeOfLock "lock_via_api". Re-locking must not add a second row,
 * and the unique index on the user id backs that up.
 *
 * unlockUser returns Full(true) when there was nothing to unlock, as before. Callers treat it as
 * "the user is not locked now" rather than "a row was deleted".
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object UserLocksProvider extends MdcLoggable {

  private def findByUserId(userId: String): Option[UserLockRow] =
    DoobieUtil.runQuery(
      sql"""SELECT userid, typeoflock, lastlockdate FROM userlocks
            WHERE userid = $userId LIMIT 1"""
        .query[(String, String, Timestamp)].option
    ).map { case (u, t, d) => UserLockRow(u, t, new Date(d.getTime)) }

  def isLocked(provider: String, username: String): Boolean =
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) => findByUserId(user.userId).isDefined
      case _          => false
    }

  def lockUser(provider: String, username: String): Box[UserLocksTrait] =
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        val lockedAt = now
        val stamp = new Timestamp(lockedAt.getTime)
        findByUserId(user.userId) match {
          case Some(existing) =>
            DoobieUtil.runUpdate(
              sql"UPDATE userlocks SET lastlockdate = $stamp WHERE userid = ${user.userId}".update.run)
            Full(UserLockRow(user.userId, existing.typeOfLock, lockedAt))
          case None =>
            DoobieUtil.runUpdate(
              sql"""INSERT INTO userlocks (userid, typeoflock, lastlockdate)
                    VALUES (${user.userId}, 'lock_via_api', $stamp)"""
                .update.run)
            Full(UserLockRow(user.userId, "lock_via_api", lockedAt))
        }
      case _ =>
        Empty
    }

  def unlockUser(provider: String, username: String): Box[Boolean] =
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        DoobieUtil.runUpdate(sql"DELETE FROM userlocks WHERE userid = ${user.userId}".update.run)
        // True even when there was no row: callers read this as "not locked now".
        Full(true)
      case _ => Empty
    }
}
