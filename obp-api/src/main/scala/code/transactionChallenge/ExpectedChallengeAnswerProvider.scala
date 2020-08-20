package code.transactionChallenge


import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common.Box


trait ExpectedChallengeAnswerProvider {
  def saveExpectedChallengeAnswer(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
     authenticationMethodId: Option[String], 
  ): Box[ExpectedChallengeAnswer]
  
  def getExpectedChallengeAnswer(challengeId: String): Box[ExpectedChallengeAnswer]
  
  def getExpectedChallengeAnswersByTransactionRequestId(transactionRequestId: String): Box[List[ExpectedChallengeAnswer]]
  
  /**
    * There is another method:  Connector.validateChallengeAnswer, it validate the challenge over Kafka.
    * This method, will validate the answer in OBP side. 
    */
  def validateChallengeAnswer(challengeId: String, challengeAnswer: String, userId: Option[String]) : Box[ExpectedChallengeAnswer] 
}



class RemotedataExpectedChallengeAnswerProviderCaseClasses {
  case class saveExpectedChallengeAnswer(
    challengeId: String,
    transactionRequestId: String,
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String]
  )
  case class getExpectedChallengeAnswer(challengeId: String)
  case class getExpectedChallengeAnswersByTransactionRequestId(transactionRequestId: String)
  case class validateChallengeAnswer(challengeId: String, challengeAnswer: String, userId: Option[String])
}

object RemotedataExpectedChallengeAnswerProviderCaseClasses extends RemotedataExpectedChallengeAnswerProviderCaseClasses


