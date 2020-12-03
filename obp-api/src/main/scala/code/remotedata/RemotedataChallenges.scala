package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.transactionChallenge.{ChallengeProvider, RemotedataChallengeProviderCaseClasses}
import com.openbankproject.commons.model.ChallengeTrait
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common._

object RemotedataChallenges extends ObpActorInit with ChallengeProvider {
  
  val cc = RemotedataChallengeProviderCaseClasses
  
  override def saveChallenge(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String]
  ): Box[ChallengeTrait] = 
    getValueFromFuture(
      (actor ? cc.saveChallenge(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId, scaMethod, scaStatus, consentId, authenticationMethodId))
        .mapTo[Box[ChallengeTrait]]
    )
  
  override def getChallenge(challengeId: String): Box[ChallengeTrait] = getValueFromFuture(
    (actor ? cc.getChallenge(challengeId: String)).mapTo[Box[ChallengeTrait]]
  )
  
  override def validateChallenge(challengeId: String, challengeAnswer: String, userId: Option[String]): Box[ChallengeTrait] = getValueFromFuture(
    (actor ? cc.validateChallenge(challengeId, challengeAnswer, userId)).mapTo[Box[ChallengeTrait]]
  )

  override def getChallengesByTransactionRequestId(transactionRequestId: String): Box[List[ChallengeTrait]] = getValueFromFuture(
    (actor ? cc.getChallengesByTransactionRequestId(transactionRequestId)).mapTo[Box[List[ChallengeTrait]]]
  )
  
  override def getChallengesByConsentId(consentId: String): Box[List[ChallengeTrait]] = getValueFromFuture(
    (actor ? cc.getChallengesByConsentId(consentId)).mapTo[Box[List[ChallengeTrait]]]
  )
  
}
