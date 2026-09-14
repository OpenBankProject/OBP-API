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

package code.users

import java.util.UUID.randomUUID

import code.api.util.SecureRandomUtil
import code.util.UUIDString
import com.openbankproject.commons.model.BankId
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers
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
  override def getUserInvitationBySecretLink(secretLink: Long): Box[UserInvitation] = {
    UserInvitation.find(
      By(UserInvitation.SecretKey, secretLink)
    )
  }
  override def updateStatusOfUserInvitation(userInvitationId: String, status: String): Box[Boolean] = tryo {
    UserInvitation.find(
      By(UserInvitation.UserInvitationId, userInvitationId)
    ) match {
      case Full(userInvitation) => userInvitation.Status(status).save
      case _ => false
    }
  }
  override def scrambleUserInvitation(userInvitationId: String): Box[Boolean] = tryo {
    UserInvitation.find(
      By(UserInvitation.UserInvitationId, userInvitationId)
    ) match {
      case Full(userInvitation) =>
        userInvitation
          .Email(Helpers.randomString(10) + "@example.com")
          .FirstName(Helpers.randomString(userInvitation.firstName.length))
          .LastName(Helpers.randomString(userInvitation.lastName.length))
          .Company(Helpers.randomString(userInvitation.company.length))
          .Country(Helpers.randomString(userInvitation.country.length))
          .Purpose(Helpers.randomString(userInvitation.purpose.length))
          .Status("DELETED")
          .save
      case _ => false
    }
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
  override def secretKey: Long = SecretKey.get
}

object UserInvitation extends UserInvitation with LongKeyedMetaMapper[UserInvitation] {
  override def dbIndexes: List[BaseIndex[UserInvitation]] = UniqueIndex(UserInvitationId) :: super.dbIndexes
}

