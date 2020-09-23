package code.transactionChallenge

import code.util.MappedUUID
import com.openbankproject.commons.model.ChallengeTrait
import com.openbankproject.commons.model.enums.{StrongCustomerAuthentication, StrongCustomerAuthenticationStatus}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.mapper._

class MappedExpectedChallengeAnswer extends ChallengeTrait with LongKeyedMapper[MappedExpectedChallengeAnswer] with IdPK with CreatedUpdated {

  def getSingleton = MappedExpectedChallengeAnswer

  // Unique
  object mChallengeId extends MappedUUID(this)
  object mChallengeType extends MappedUUID(this)
  object mTransactionRequestId extends MappedUUID(this)
  object mExpectedAnswer extends MappedString(this,50)
  object mExpectedUserId extends MappedUUID(this)
  object mSalt extends MappedString(this, 50)
  object mSuccessful extends MappedBoolean(this)
  
  object mScaMethod extends MappedString(this,100)
  object mScaStatus extends MappedString(this,100)
  object mConsentId extends MappedString(this,100)
  object mAuthenticationMethodId extends MappedString(this,100)
  
  override def challengeId: String = mChallengeId.get
  override def challengeType: String = mChallengeType.get
  override def transactionRequestId: String = mTransactionRequestId.get
  override def expectedAnswer: String = mExpectedAnswer.get
  override def expectedUserId: String = mExpectedUserId.get
  override def salt: String = mSalt.get
  override def successful: Boolean = mSuccessful.get
  override def consentId: Option[String] = Option(mConsentId.get)
  override def scaMethod: Option[SCA] = Option(StrongCustomerAuthentication.withName(mScaMethod.get))
  override def scaStatus: Option[SCAStatus] = Option(StrongCustomerAuthenticationStatus.withName(mScaStatus.get))
  override def authenticationMethodId: Option[String] = Option(mAuthenticationMethodId.get)
}

object MappedExpectedChallengeAnswer extends MappedExpectedChallengeAnswer with LongKeyedMetaMapper[MappedExpectedChallengeAnswer] {
  override def dbIndexes = UniqueIndex(mChallengeId):: super.dbIndexes
}