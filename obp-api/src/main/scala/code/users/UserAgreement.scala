package code.users

import java.util.Date
import java.util.UUID.randomUUID

import code.api.util.HashUtil
import code.util.UUIDString
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.common.Box.tryo

object MappedUserAgreementProvider extends UserAgreementProvider {
  override def createUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement] = {
    Full(
      UserAgreement.create
        .UserId(userId)
        .AgreementType(agreementType)
        .AgreementText(agreementText)
        .Date(new Date)
        .saveMe()
    )
  }
  override def getLastUserAgreement(userId: String, agreementType: String): Box[UserAgreement] = {
    UserAgreement.findAll(
      By(UserAgreement.UserId, userId),
      By(UserAgreement.AgreementType, agreementType)
    ).sortBy(_.Date.get)(Ordering[Date].reverse).headOption
  }
}
class UserAgreement extends UserAgreementTrait with LongKeyedMapper[UserAgreement] with IdPK with CreatedUpdated {

  def getSingleton = UserAgreement
  
  object UserAgreementId extends UUIDString(this) {
    override def defaultValue = randomUUID().toString
  }
  object UserId extends MappedString(this, 255)
  object Date extends MappedDate(this)
  object AgreementType extends MappedString(this, 64)
  object AgreementText extends MappedText(this)
  object AgreementHash extends MappedString(this, 64) {
    override def defaultValue: String = HashUtil.Sha256Hash(AgreementText.get)
  }

  override def userInvitationId: String = UserAgreementId.get
  override def userId: String = UserId.get
  override def agreementType: String = AgreementType.get
  override def agreementText: String = AgreementText.get
  override def agreementHash: String = AgreementHash.get
  override def date: Date = Date.get
}

object UserAgreement extends UserAgreement with LongKeyedMetaMapper[UserAgreement] {
  override def dbIndexes: List[BaseIndex[UserAgreement]] = UniqueIndex(UserAgreementId) :: super.dbIndexes
  override def beforeSave = List(
    agreement =>
      tryo {
        val hash = HashUtil.Sha256Hash(agreement.agreementText)
        agreement.AgreementHash(hash)
      }
  )

}

