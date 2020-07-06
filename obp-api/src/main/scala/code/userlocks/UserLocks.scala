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
