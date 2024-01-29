package code.transactionChallenge

import code.api.util.APIUtil.{allowedAnswerTransactionRequestChallengeAttempts, transactionRequestChallengeTtl}
import code.api.util.ErrorMessages.InvalidChallengeAnswer
import code.api.util.{APIUtil, ErrorMessages}
import com.openbankproject.commons.model.{ChallengeTrait, ErrorMessage}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers
import org.mindrot.jbcrypt.BCrypt
import net.liftweb.util.Helpers.tryo

import scala.compat.Platform

object MappedChallengeProvider extends ChallengeProvider {
  
  override def saveChallenge(
    challengeId: String,
    transactionRequestId: String, // Note: consentId and transactionRequestId and basketId are exclusive here.
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    basketId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    authenticationMethodId: Option[String],
    challengeType: String, 
  ): Box[ChallengeTrait] = 
    tryo (
      MappedExpectedChallengeAnswer
        .create
        .ChallengeId(challengeId)
        .ChallengeType(challengeType)
        .TransactionRequestId(transactionRequestId)
        .Salt(salt)
        .ExpectedAnswer(expectedAnswer)
        .ExpectedUserId(expectedUserId)
        .ScaMethod(scaMethod.map(_.toString).getOrElse(""))
        .ScaStatus(scaStatus.map(_.toString).getOrElse(""))
        .ConsentId(consentId.getOrElse(""))
        .BasketId(basketId.getOrElse(""))
        .AuthenticationMethodId(expectedUserId)
        .saveMe()
    )
  
  override def getChallenge(challengeId: String): Box[MappedExpectedChallengeAnswer] =
      MappedExpectedChallengeAnswer.find(By(MappedExpectedChallengeAnswer.ChallengeId,challengeId))

  override def getChallengesByTransactionRequestId(transactionRequestId: String): Box[List[ChallengeTrait]] =
    Full(MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.TransactionRequestId,transactionRequestId)))
  
  override def getChallengesByConsentId(consentId: String): Box[List[ChallengeTrait]] =
    Full(MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.ConsentId,consentId)))
  override def getChallengesByBasketId(basketId: String): Box[List[ChallengeTrait]] =
    Full(MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.BasketId,basketId)))
  
  override def validateChallenge(
    challengeId: String,
    challengeAnswer: String,
    userId: Option[String]
  ): Box[ChallengeTrait] = {
    for{
       challenge <-  getChallenge(challengeId) ?~! s"${ErrorMessages.InvalidTransactionRequestChallengeId}"
       currentAttemptCounterValue = challenge.attemptCounter
        //We update the counter anyway.
       _ = challenge.AttemptCounter(currentAttemptCounterValue+1).saveMe()
       createDateTime = challenge.createdAt.get
       challengeTTL : Long = Helpers.seconds(APIUtil.transactionRequestChallengeTtl)

       expiredDateTime: Long = createDateTime.getTime+challengeTTL
       currentTime: Long = Platform.currentTime
       challenge <- if(currentAttemptCounterValue < APIUtil.allowedAnswerTransactionRequestChallengeAttempts){
        if(expiredDateTime > currentTime) {
          val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, challenge.salt).substring(0, 44)
          val expectedHashedAnswer = challenge.expectedAnswer
          userId match {
            case None =>
              if(currentHashedAnswer==expectedHashedAnswer) {
                tryo{challenge.Successful(true).ScaStatus(StrongCustomerAuthenticationStatus.finalised.toString).saveMe()}
              } else {
                Failure(s"${
                  s"${
                    InvalidChallengeAnswer
                      .replace("answer may be expired.", s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                      .replace("up your allowed attempts.", s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
                  }"}")
              }
            case Some(id) =>
              if(currentHashedAnswer==expectedHashedAnswer && id==challenge.expectedUserId) {
                tryo{challenge.Successful(true).ScaStatus(StrongCustomerAuthenticationStatus.finalised.toString).saveMe()}
              } else {
                Failure(s"${
                  s"${
                    InvalidChallengeAnswer
                      .replace("answer may be expired.", s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                      .replace("up your allowed attempts.", s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
                  }"}")
              }
          }
        }else{
          Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is $transactionRequestChallengeTtl seconds")
        }
      }else{
        Failure(s"${ErrorMessages.AllowedAttemptsUsedUp}")
      }
    } yield{
      challenge
    }
  }
}