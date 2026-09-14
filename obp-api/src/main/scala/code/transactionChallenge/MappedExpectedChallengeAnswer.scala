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

  // PSD2 Dynamic Linking fields
  object ChallengePurpose extends MappedString(this, 2000)
  object ChallengeContextHash extends MappedString(this, 64)
  object ChallengeContextStructure extends MappedString(this, 500)

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

  // PSD2 Dynamic Linking
  override def challengePurpose: Option[String] = Option(ChallengePurpose.get).filter(_.nonEmpty)
  override def challengeContextHash: Option[String] = Option(ChallengeContextHash.get).filter(_.nonEmpty)
  override def challengeContextStructure: Option[String] = Option(ChallengeContextStructure.get).filter(_.nonEmpty)
}

object MappedExpectedChallengeAnswer extends MappedExpectedChallengeAnswer with LongKeyedMetaMapper[MappedExpectedChallengeAnswer] {
  override def dbTableName = "ExpectedChallengeAnswer" // define the DB table name
  override def dbIndexes = UniqueIndex(ChallengeId):: super.dbIndexes
}