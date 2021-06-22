package code.users

import java.util.Date
import java.util.UUID.randomUUID

import code.api.util.{HashUtil, SecureRandomUtil}
import code.util.UUIDString
import com.openbankproject.commons.model.BankId
import net.liftweb.common.Box
import net.liftweb.mapper.{MappedDateTime, _}
import net.liftweb.util.Helpers.tryo

object MappedUserAgreementProvider extends UserAgreementProvider {
  override def createUserAgreement(userId: String, summary: String, agreementText: String): Box[UserAgreement] = tryo {
    UserAgreement.create
      .UserId(userId)
      .Summary(summary)
      .AgreementText(agreementText)
      .Date(new Date)
      .saveMe()
  }
}
class UserAgreement extends UserAgreementTrait with LongKeyedMapper[UserAgreement] with IdPK with CreatedUpdated {

  def getSingleton = UserAgreement
  
  object UserAgreementId extends UUIDString(this) {
    override def defaultValue = randomUUID().toString
  }
  object UserId extends MappedString(this, 255)
  object Date extends MappedDate(this)
  object Summary extends MappedString(this, 50)
  object AgreementText extends MappedText(this)
  object AgreementHash extends MappedString(this, 50) {
    override def defaultValue: String = HashUtil.Sha256Hash(AgreementText.get)
  }

  override def userInvitationId: String = UserAgreementId.get
  override def userId: String = UserId.get
  override def summary: String = Summary.get
  override def agreementText: String = AgreementText.get
  override def agreementHash: String = AgreementHash.get
}

object UserAgreement extends UserAgreement with LongKeyedMetaMapper[UserAgreement] {
  override def dbIndexes: List[BaseIndex[UserAgreement]] = UniqueIndex(UserAgreementId) :: super.dbIndexes
}

