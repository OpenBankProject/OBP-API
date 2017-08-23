package code.transactionChallenge

import code.util.MappedUUID
import net.liftweb.mapper._

class MappedExpectedChallengeAnswer extends ExpectedChallengeAnswer with LongKeyedMapper[MappedExpectedChallengeAnswer] with IdPK with CreatedUpdated {

  def getSingleton = MappedExpectedChallengeAnswer

  // Unique
  object mChallengeId extends MappedUUID(this)
  object mExpectedAnswer extends MappedString(this,50)
  object mSalt extends MappedString(this, 50)
  
  override def challengeId: String = mChallengeId.get
  override def expectedAnswer: String = mExpectedAnswer.get
  override def salt: String = mSalt.get
}

object MappedExpectedChallengeAnswer extends MappedExpectedChallengeAnswer with LongKeyedMetaMapper[MappedExpectedChallengeAnswer] {
  override def dbIndexes = UniqueIndex(mChallengeId):: super.dbIndexes
}