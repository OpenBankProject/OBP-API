package code.users

import code.util.UUIDString
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedUserInvitationProvider extends UserInvitationProvider {
  override def createUserInvitation(firstName: String, lastName: String, email: String, company: String, country: String): Box[UserInvitation] = tryo {
    UserInvitation.create.FirstName(firstName).saveMe()
  }
}
class UserInvitation extends UserInvitationTrait with LongKeyedMapper[UserInvitation] with IdPK with CreatedUpdated {

  def getSingleton = UserInvitation
  
  object UserInvitationId extends UUIDString(this)
  object FirstName extends MappedString(this, 50)
  object LastName extends MappedString(this, 50)
  object Email extends MappedString(this, 50)
  object Company extends MappedString(this, 50)
  object Country extends MappedString(this, 50)
  object Status extends MappedString(this, 50)

  override def userInvitationId: String = UserInvitationId.get
  override def firstName: String = FirstName.get
  override def lastName: String = LastName.get
  override def email: String = Email.get
  override def company: String = Company.get
  override def country: String = Country.get
  override def status: String = Status.get
}

object UserInvitation extends UserInvitation with LongKeyedMetaMapper[UserInvitation] {
  override def dbIndexes: List[BaseIndex[UserInvitation]] = UniqueIndex(UserInvitationId) :: super.dbIndexes
}

