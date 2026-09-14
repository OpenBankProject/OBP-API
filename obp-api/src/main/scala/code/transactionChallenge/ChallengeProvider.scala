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


