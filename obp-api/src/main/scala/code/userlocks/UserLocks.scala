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

import java.util.Date

import code.util.MappedUUID
import net.liftweb.mapper._

class UserLocks extends UserLocksTrait with LongKeyedMapper[UserLocks] with IdPK {
  def getSingleton = UserLocks

  object UserId extends MappedUUID(this)
  object TypeOfLock extends MappedString(this, 100)
  object LastLockDate extends MappedDateTime(this)

  override def userId: String = UserId.get
  override def typeOfLock: String = TypeOfLock.get
  override def lastLockDate: Date = LastLockDate.get
}

object UserLocks extends UserLocks with LongKeyedMetaMapper[UserLocks] {
  override def dbIndexes: List[BaseIndex[UserLocks]] = UniqueIndex(UserId) :: super.dbIndexes
}

trait UserLocksTrait {
  def userId: String
  def typeOfLock: String
  def lastLockDate: Date
}
