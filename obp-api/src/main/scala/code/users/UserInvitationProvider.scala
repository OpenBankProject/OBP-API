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

import com.openbankproject.commons.model.BankId
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object UserInvitationProvider extends SimpleInjector {

  val userInvitationProvider = new Inject(() => buildOne) {}

  def buildOne: UserInvitationProvider = MappedUserInvitationProvider

}

trait UserInvitationProvider {
  def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String, company: String, country: String, purpose: String): Box[UserInvitation]
  def getUserInvitationBySecretLink(secretLink: Long): Box[UserInvitation]
  def scrambleUserInvitation(userInvitationId: String): Box[Boolean]
  def updateStatusOfUserInvitation(userInvitationId: String, status: String): Box[Boolean]
  def getUserInvitation(bankId: BankId, secretLink: Long): Box[UserInvitation]
  def getUserInvitations(bankId: BankId): Box[List[UserInvitation]]
}

trait UserInvitationTrait {
  def userInvitationId: String
  def bankId: String
  def firstName: String
  def lastName: String
  def email: String
  def company: String
  def country: String
  def status: String
  def purpose: String
  def secretKey: Long
}