package code.UserRefreshes

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

import java.util.{Calendar, Date}

/** One user-refreshes row, standing in for the Lift entity in return types. */
case class UserRefreshesRow(userId: String) extends UserRefreshes

/**
 * Doobie implementation of the user-refresh-tracking store, replacing the Lift
 * MappedUserRefreshes entity.
 *
 * One unique index on muserid - one row per user, matching the entity's own dbIndexes.
 * createOrUpdateRefreshUser finds-then-updates-or-creates, same shape as the Mapper version.
 */
object DoobieUserRefreshesProvider extends UserRefreshesProvider {

  override def needToRefreshUser(userId: String): Boolean =
    DoobieUtil.runQuery(
      sql"SELECT updatedat FROM mappeduserrefreshes WHERE muserid = $userId LIMIT 1".query[java.sql.Timestamp].option
    ) match {
      case Some(updatedAt) =>
        val userRefreshesInterval = APIUtil.getPropsAsIntValue("refresh_user.interval", 30)
        val lastUpdatePlusInterval: Calendar = Calendar.getInstance()
        lastUpdatePlusInterval.setTime(new Date(updatedAt.getTime))
        lastUpdatePlusInterval.add(Calendar.MINUTE, userRefreshesInterval)
        val currentDate = Calendar.getInstance()
        lastUpdatePlusInterval.before(currentDate)
      case None => true
    }

  override def createOrUpdateRefreshUser(userId: String): UserRefreshes = {
    val exists = DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM mappeduserrefreshes WHERE muserid = $userId".query[Int].unique) > 0
    if (exists) {
      DoobieUtil.runUpdate(
        sql"UPDATE mappeduserrefreshes SET updatedat = CURRENT_TIMESTAMP WHERE muserid = $userId".update.run)
    } else {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappeduserrefreshes (muserid, createdat, updatedat)
              VALUES ($userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
          .update.run)
    }
    UserRefreshesRow(userId)
  }

  def bulkDelete(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserrefreshes".update.run)
    true
  }

  def count(): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM mappeduserrefreshes".query[Long].unique)
}
