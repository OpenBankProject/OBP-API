/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
    // PSD2 Dynamic Linking fields
    challengePurpose: Option[String] = None,
    challengeContextHash: Option[String] = None,
    challengeContextStructure: Option[String] = None
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
        // PSD2 Dynamic Linking
        .ChallengePurpose(challengePurpose.getOrElse(""))
        .ChallengeContextHash(challengeContextHash.getOrElse(""))
        .ChallengeContextStructure(challengeContextStructure.getOrElse(""))
        .saveMe()
    )
  
  override def getChallenge(challengeId: String): Box[MappedExpectedChallengeAnswer] =
      MappedExpectedChallengeAnswer.find(By(MappedExpectedChallengeAnswer.ChallengeId,challengeId))

  /** Compare-and-set the success flag: only the first correct answer flips
   *  successful=false -> true. A second concurrent correct answer gets 0 rows and a
   *  Failure, so one challenge can never green-light a payment twice (MFA double-spend). */
  private def markChallengeSuccessful(challengeId: String): Box[MappedExpectedChallengeAnswer] = {
    val rows = code.bankconnectors.DoobieBusinessStatusQueries
      .conditionalChallengeSuccess(challengeId, StrongCustomerAuthenticationStatus.finalised.toString)
    if (rows == 1) getChallenge(challengeId)
    else Failure(s"${ErrorMessages.InvalidTransactionRequestChallengeId} Challenge already answered.")
  }

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
       newAttemptCounterValue <- tryo(code.bankconnectors.DoobieChallengeQueries.incrementAndGetChallengeCounter(challengeId)) ?~! "Failed to update challenge attempt counter"
       createDateTime = challenge.createdAt.get
       challengeTTL : Long = Helpers.seconds(APIUtil.transactionRequestChallengeTtl)

       expiredDateTime: Long = createDateTime.getTime+challengeTTL
       currentTime: Long = Platform.currentTime
       challenge <- if(newAttemptCounterValue <= APIUtil.allowedAnswerTransactionRequestChallengeAttempts){
        if(expiredDateTime > currentTime) {
          val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, challenge.salt).substring(0, 44)
          val expectedHashedAnswer = challenge.expectedAnswer
          val answerMatches = currentHashedAnswer == expectedHashedAnswer
          val userMatches = userId.forall(_ == challenge.expectedUserId)
          if (answerMatches && userMatches) {
            markChallengeSuccessful(challengeId)
          } else {
            Failure(
              InvalidChallengeAnswer
                .replace("answer may be expired.", s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                .replace("up your allowed attempts.", s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
            )
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