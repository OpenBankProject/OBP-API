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
    // PSD2 Dynamic Linking fields
    challengePurpose: Option[String] = None,         // Human-readable description shown to user
    challengeContextHash: Option[String] = None,     // SHA-256 hash of critical transaction fields
    challengeContextStructure: Option[String] = None // Comma-separated list of field names in hash
  ): Box[ChallengeTrait]
  
  def getChallenge(challengeId: String): Box[ChallengeTrait]
  
  def getChallengesByTransactionRequestId(transactionRequestId: String): Box[List[ChallengeTrait]]
  
  def getChallengesByConsentId(consentId: String): Box[List[ChallengeTrait]]
  def getChallengesByBasketId(basketId: String): Box[List[ChallengeTrait]]

  /**
    * There is another method:  Connector.validateChallengeAnswer, it validates the challenge over CBS.
    * This method, will validate the answer in OBP side. 
    */
  def validateChallenge(challengeId: String, challengeAnswer: String, userId: Option[String]) : Box[ChallengeTrait] 
}


