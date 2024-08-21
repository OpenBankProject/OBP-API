package code.transactionChallenge


import com.openbankproject.commons.model.ChallengeTrait
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common.Box


trait ChallengeProvider {
  def saveChallenge(
    challengeId: String,
    transactionRequestId: String, // Note: basketId, consentId and transactionRequestId are exclusive here.
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: basketId, consentId and transactionRequestId are exclusive here.
    basketId: Option[String], // Note: basketId, consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String],
    challengeType: String,
  ): Box[ChallengeTrait]
  
  def getChallenge(challengeId: String): Box[ChallengeTrait]
  
  def getChallengesByTransactionRequestId(transactionRequestId: String): Box[List[ChallengeTrait]]
  
  def getChallengesByConsentId(consentId: String): Box[List[ChallengeTrait]]
  def getChallengesByBasketId(basketId: String): Box[List[ChallengeTrait]]

  /**
    * There is another method:  Connector.validateChallengeAnswer, it validate the challenge over Kafka.
    * This method, will validate the answer in OBP side. 
    */
  def validateChallenge(challengeId: String, challengeAnswer: String, userId: Option[String]) : Box[ChallengeTrait] 
}


