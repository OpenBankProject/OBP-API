package code.users

import java.util.UUID.randomUUID

import code.api.util.SecureRandomUtil
import code.util.UUIDString
import com.openbankproject.commons.model.BankId
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedUserInvitationProvider extends UserInvitationProvider {
  override def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String, company: String, country: String, purpose: String): Box[UserInvitation] = tryo {
    UserInvitation.create
      .BankId(bankId.value)
      .FirstName(firstName)
      .LastName(lastName)
      .Email(email)
      .Company(company)
      .Country(country)
      .Status("CREATED")
      .Purpose(purpose)
      .saveMe()
  }
  override def getUserInvitation(bankId: BankId, secretLink: Long): Box[UserInvitation] = {
    UserInvitation.find(
      By(UserInvitation.BankId, bankId.value),
      By(UserInvitation.SecretKey, secretLink)
    )
  }
  override def getUserInvitations(bankId: BankId): Box[List[UserInvitation]] = tryo {
    UserInvitation.findAll(By(UserInvitation.BankId, bankId.value))
  }
}
class UserInvitation extends UserInvitationTrait with LongKeyedMapper[UserInvitation] with IdPK with CreatedUpdated {

  def getSingleton = UserInvitation
  
  object UserInvitationId extends UUIDString(this) {
    override def defaultValue = randomUUID().toString
  }
  object BankId extends MappedString(this, 255)
  object FirstName extends MappedString(this, 50)
  object LastName extends MappedString(this, 50)
  object Email extends MappedString(this, 50)
  object Company extends MappedString(this, 50)
  object Country extends MappedString(this, 50)
  object Status extends MappedString(this, 50)
  object Purpose extends MappedString(this, 50)
  object SecretKey extends MappedLong(this) {
    override def defaultValue: Long = SecureRandomUtil.csprng.nextLong()
  }

  override def userInvitationId: String = UserInvitationId.get
  override def bankId: String = BankId.get
  override def firstName: String = FirstName.get
  override def lastName: String = LastName.get
  override def email: String = Email.get
  override def company: String = Company.get
  override def country: String = Country.get
  override def status: String = Status.get
  override def purpose: String = Purpose.get
  override def secretLink: Long = SecretKey.get
}

object UserInvitation extends UserInvitation with LongKeyedMetaMapper[UserInvitation] {
  override def dbIndexes: List[BaseIndex[UserInvitation]] = UniqueIndex(UserInvitationId) :: super.dbIndexes
}

