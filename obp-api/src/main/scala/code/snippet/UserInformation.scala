/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.snippet

import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.http.{RequestVar, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

import scala.xml.NodeSeq

class UserInformation extends MdcLoggable {
  
  private object idTokenVar extends RequestVar("")
  private object accessTokenVar extends RequestVar("")
  private object providerVar extends RequestVar("")
  private object devEmailVar extends RequestVar("")
  private object usernameVar extends RequestVar("")

  def show: CssSel = {
    if(!AuthUser.loggedIn_?) {
      "*" #> NodeSeq.Empty
    } else if (AuthUser.getCurrentUser.isEmpty) {
      "*" #> NodeSeq.Empty
    } else {
      val user: User = AuthUser.getCurrentUser.openOrThrowException(attemptedToOpenAnEmptyBox)
      usernameVar.set(user.name)
      devEmailVar.set(user.emailAddress)
      providerVar.set(user.provider)
      idTokenVar.set(AuthUser.getIDTokenOfCurrentUser)
      accessTokenVar.set(AuthUser.getAccessTokenOfCurrentUser)
      "form" #> {
        "#user-info-username" #> SHtml.text(usernameVar, usernameVar(_)) &
        "#user-info-provider" #> SHtml.text(providerVar.is, providerVar(_)) &
        "#user-info-email" #> SHtml.text(devEmailVar, devEmailVar(_)) &
        "#user-info-id-token" #> SHtml.text(idTokenVar, idTokenVar(_)) &
        "#user-info-access-token" #> SHtml.text(accessTokenVar, accessTokenVar(_))
      } & "#register-consumer-success" #> ""
    }
  }
  
}
