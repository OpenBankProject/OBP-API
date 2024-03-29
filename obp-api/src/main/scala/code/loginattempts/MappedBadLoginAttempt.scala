package code.loginattempts

import java.util.Date

import net.liftweb.mapper._

class MappedBadLoginAttempt extends BadLoginAttempt with LongKeyedMapper[MappedBadLoginAttempt] with IdPK {
  def getSingleton = MappedBadLoginAttempt

  object mUsername extends MappedString(this, 100) {
    override def dbNotNull_? = true
  }
  object Provider extends MappedString(this, 100)
  object mBadAttemptsSinceLastSuccessOrReset extends MappedInt(this)
  object mLastFailureDate extends MappedDateTime(this)

  override def username: String = mUsername.get
  override def provider: String = Provider.get
  override def badAttemptsSinceLastSuccessOrReset: Int = mBadAttemptsSinceLastSuccessOrReset.get
  override def lastFailureDate: Date = mLastFailureDate.get
}

object MappedBadLoginAttempt extends MappedBadLoginAttempt with LongKeyedMetaMapper[MappedBadLoginAttempt] {
  override def dbIndexes = UniqueIndex(Provider,mUsername) :: super.dbIndexes
}

trait BadLoginAttempt {
  def username: String
  def provider: String
  def badAttemptsSinceLastSuccessOrReset : Int
  def lastFailureDate : Date
}
