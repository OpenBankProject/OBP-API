package code.userlocks

import code.users.Users
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._

object UserLocksProvider extends MdcLoggable {
  def isLocked(provider: String, username: String): Boolean = {
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        UserLocks.find(By(UserLocks.UserId, user.userId)) match {
          case Full(_) => true
          case _ => false
        }
      case _ => false
    }
  }
  def lockUser(provider: String, username: String): Box[UserLocks] = {
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        UserLocks.find(By(UserLocks.UserId, user.userId)) match {
          case Full(userLocks) =>
            Some(
              userLocks
              .LastLockDate(now)
              .saveMe()
            )
          case _ =>
            Some(
              UserLocks.create
              .UserId(user.userId)
              .TypeOfLock("lock_via_api")
              .LastLockDate(now)
              .saveMe()
            )
        }
      case _ =>
        Empty
    }
  }
  def unlockUser(provider: String, username: String): Box[Boolean] = {
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        UserLocks.find(By(UserLocks.UserId, user.userId)) match {
          case Full(userLocks) => Some(userLocks.delete_!)
          case _               => Some(true)
        }
      case _ => Empty
    }
  }

}