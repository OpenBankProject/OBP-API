package code.transactionChallenge

import code.util.MappedUUID
import net.liftweb.mapper._

class MappedExpectedChallengeAnswer extends ExpectedChallengeAnswer with LongKeyedMapper[MappedExpectedChallengeAnswer] with IdPK with CreatedUpdated {

  def getSingleton = MappedExpectedChallengeAnswer

  // Unique
  object mChallengeId extends MappedUUID(this)
  object mTransactionRequestId extends MappedUUID(this)
  object mExpectedAnswer extends MappedString(this,50)
  object mExpectedUserId extends MappedUUID(this)
  object mSalt extends MappedString(this, 50)
  object mSuccessful extends MappedBoolean(this)
  
  override def challengeId: String = mChallengeId.get
  override def transactionRequestId: String = mTransactionRequestId.get
  override def expectedAnswer: String = mExpectedAnswer.get
  override def expectedUserId: String = mExpectedUserId.get
  override def salt: String = mSalt.get
  override def successful: Boolean = mSuccessful.get
}

object MappedExpectedChallengeAnswer extends MappedExpectedChallengeAnswer with LongKeyedMetaMapper[MappedExpectedChallengeAnswer] {
  override def dbIndexes = UniqueIndex(mChallengeId):: super.dbIndexes
}