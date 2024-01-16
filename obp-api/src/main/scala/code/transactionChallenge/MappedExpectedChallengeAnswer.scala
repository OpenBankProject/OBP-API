package code.transactionChallenge

import code.util.MappedUUID
import com.openbankproject.commons.model.ChallengeTrait
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums.{StrongCustomerAuthentication, StrongCustomerAuthenticationStatus}
import net.liftweb.mapper._

class MappedExpectedChallengeAnswer extends ChallengeTrait with LongKeyedMapper[MappedExpectedChallengeAnswer] with IdPK with CreatedUpdated {

  def getSingleton = MappedExpectedChallengeAnswer

  // Unique
  object ChallengeId extends MappedUUID(this)
  object ChallengeType extends MappedString(this, 100)
  object TransactionRequestId extends MappedUUID(this)
  object ExpectedAnswer extends MappedString(this,50)
  object ExpectedUserId extends MappedUUID(this)
  object Salt extends MappedString(this, 50)
  object Successful extends MappedBoolean(this)

  object ScaMethod extends MappedString(this,100)
  object ScaStatus extends MappedString(this,100)
  object ConsentId extends MappedString(this,100)
  object BasketId extends MappedString(this,100)
  object AuthenticationMethodId extends MappedString(this,100)
  object AttemptCounter extends MappedInt(this){
    override def defaultValue = 0
  }

  override def challengeId: String = ChallengeId.get
  override def challengeType: String = ChallengeType.get
  override def transactionRequestId: String = TransactionRequestId.get
  override def expectedAnswer: String = ExpectedAnswer.get
  override def expectedUserId: String = ExpectedUserId.get
  override def salt: String = Salt.get
  override def successful: Boolean = Successful.get
  override def consentId: Option[String] = Option(ConsentId.get)
  override def basketId: Option[String] = Option(BasketId.get)
  override def scaMethod: Option[SCA] = Option(StrongCustomerAuthentication.withName(ScaMethod.get))
  override def scaStatus: Option[SCAStatus] = Option(StrongCustomerAuthenticationStatus.withName(ScaStatus.get))
  override def authenticationMethodId: Option[String] = Option(AuthenticationMethodId.get)
  override def attemptCounter: Int = AttemptCounter.get
}

object MappedExpectedChallengeAnswer extends MappedExpectedChallengeAnswer with LongKeyedMetaMapper[MappedExpectedChallengeAnswer] {
  override def dbTableName = "ExpectedChallengeAnswer" // define the DB table name
  override def dbIndexes = UniqueIndex(ChallengeId):: super.dbIndexes
}