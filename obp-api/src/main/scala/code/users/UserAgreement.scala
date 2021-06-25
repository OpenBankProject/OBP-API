package code.users

import java.util.Date
import java.util.UUID.randomUUID

import code.api.util.HashUtil
import code.util.UUIDString
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._

object MappedUserAgreementProvider extends UserAgreementProvider {
  override def createOrUpdateUserAgreement(userId: String, summary: String, agreementText: String, acceptMarketingInfo: Boolean): Box[UserAgreement] = {
    UserAgreement.find(By(UserAgreement.UserId, userId)) match {
      case Full(existingUser) =>
        Full(
          existingUser
            .Summary(summary)
            .AgreementText(agreementText)
            .AcceptMarketingInfo(acceptMarketingInfo)
            .saveMe()
        )
      case Empty =>
        Full(
          UserAgreement.create
            .UserId(userId)
            .Summary(summary)
            .AgreementText(agreementText)
            .AcceptMarketingInfo(acceptMarketingInfo)
            .Date(new Date)
            .saveMe()
        )
      case everythingElse => everythingElse
    }
  }
}
class UserAgreement extends UserAgreementTrait with LongKeyedMapper[UserAgreement] with IdPK with CreatedUpdated {

  def getSingleton = UserAgreement
  
  object UserAgreementId extends UUIDString(this) {
    override def defaultValue = randomUUID().toString
  }
  object UserId extends MappedString(this, 255)
  object Date extends MappedDate(this)
  object Summary extends MappedText(this)
  object AgreementText extends MappedText(this)
  object AgreementHash extends MappedString(this, 64) {
    override def defaultValue: String = HashUtil.Sha256Hash(AgreementText.get)
  }
  object AcceptMarketingInfo extends MappedBoolean(this)

  override def userInvitationId: String = UserAgreementId.get
  override def userId: String = UserId.get
  override def summary: String = Summary.get
  override def agreementText: String = AgreementText.get
  override def agreementHash: String = AgreementHash.get
  override def acceptMarketingInfo: Boolean = AcceptMarketingInfo.get
}

object UserAgreement extends UserAgreement with LongKeyedMetaMapper[UserAgreement] {
  override def dbIndexes: List[BaseIndex[UserAgreement]] = UniqueIndex(UserAgreementId) :: super.dbIndexes
}

