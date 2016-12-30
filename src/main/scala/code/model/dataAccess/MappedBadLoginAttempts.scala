package code.model.dataAccess

import java.util.Date

import net.liftweb.mapper._

class MappedBadLoginAttempts extends LoginAttempt with LongKeyedMapper[MappedBadLoginAttempts] with IdPK {
  def getSingleton = MappedBadLoginAttempts

  object mUsername extends MappedString(this, 255)
  object mBadAttemptsSinceLastSuccess extends MappedInt(this)
  object mLastFailureDate extends MappedDateTime(this)

  override def username: String = mUsername.get

  override def badAttemptsSinceLastSuccess: Int = mBadAttemptsSinceLastSuccess.get

  override def lastFailureDate: Date = mLastFailureDate.get
}

object MappedBadLoginAttempts extends MappedBadLoginAttempts with LongKeyedMetaMapper[MappedBadLoginAttempts] {
  override def dbIndexes = UniqueIndex(mUsername) :: super.dbIndexes
}

trait LoginAttempt {
  def username: String
  def badAttemptsSinceLastSuccess : Int
  def lastFailureDate : Date
}
