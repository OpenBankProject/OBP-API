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

package code.sandbox

import code.api.util.APIUtil.fullPasswordValidation
import code.api.util.ErrorMessages
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.Users
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.By

trait CreateAuthUsers {

  self : OBPDataImport =>

  override protected def createSaveableUser(u : SandboxUserImport) : Box[Saveable[ResourceUser]] = {

    def asSaveable(u : AuthUser) = new Saveable[ResourceUser] {
      val value = u.createUnsavedResourceUser()
      def save() = {
        val usr = Users.users.vend.saveResourceUser(value)
        for (uu <- usr) {
          u.user(uu).save
        }
      }
    }

    val existingAuthUser = AuthUser.find(By(AuthUser.username, u.user_name))

    if(existingAuthUser.isDefined) {
      logger.warn(s"Existing AuthUser with email ${u.email} detected in data import where no ResourceUser was found")
      Failure(s"User with email ${u.email} already exist (and may be different (e.g. different display_name)")
    } else {
      val authUser = AuthUser.create
        .email(u.email)
        .firstName(u.user_name)
        .lastName(u.user_name)
        .username(u.user_name)
        .password(u.password)
        .validated(true)

      val validationErrors = authUser.validate
      if (!fullPasswordValidation(u.password)) Failure(ErrorMessages.InvalidStrongPasswordFormat)
      else if(!validationErrors.isEmpty) Failure(s"Errors: ${validationErrors.map(_.msg)}")
      else Full(asSaveable(authUser))
    }
  }

}
