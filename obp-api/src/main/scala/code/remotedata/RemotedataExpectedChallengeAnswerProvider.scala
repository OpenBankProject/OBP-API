package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.transactionChallenge.{ExpectedChallengeAnswer, ExpectedChallengeAnswerProvider, RemotedataExpectedChallengeAnswerProviderCaseClasses}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common._

object RemotedataExpectedChallengeAnswerProvider extends ObpActorInit with ExpectedChallengeAnswerProvider {
  
  val cc = RemotedataExpectedChallengeAnswerProviderCaseClasses
  
  override def saveExpectedChallengeAnswer(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String]
  ): Box[ExpectedChallengeAnswer] = 
    getValueFromFuture(
      (actor ? cc.saveExpectedChallengeAnswer(challengeId, transactionRequestId, salt, expectedAnswer, expectedUserId, scaMethod, scaStatus, consentId, authenticationMethodId))
        .mapTo[Box[ExpectedChallengeAnswer]]
    )
  
  override def getExpectedChallengeAnswer(challengeId: String): Box[ExpectedChallengeAnswer] = getValueFromFuture(
    (actor ? cc.getExpectedChallengeAnswer(challengeId: String)).mapTo[Box[ExpectedChallengeAnswer]]
  )
  
  override def validateChallengeAnswer(challengeId: String, challengeAnswer: String, userId: Option[String]): Box[ExpectedChallengeAnswer] = getValueFromFuture(
    (actor ? cc.validateChallengeAnswer(challengeId, challengeAnswer, userId)).mapTo[Box[ExpectedChallengeAnswer]]
  )

  override def getExpectedChallengeAnswersByTransactionRequestId(transactionRequestId: String): Box[List[ExpectedChallengeAnswer]] = getValueFromFuture(
    (actor ? cc.getExpectedChallengeAnswersByTransactionRequestId(transactionRequestId)).mapTo[Box[List[ExpectedChallengeAnswer]]]
  )
}
