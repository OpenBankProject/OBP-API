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