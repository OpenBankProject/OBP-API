/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

 */
package code.api

import code.api.util.{CallContext, JwtUtil}
import code.model.User
import code.users.Users
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object OAuth2Handshake extends RestHelper with MdcLoggable {

  private def getValueOfOAuh2HeaderField(sc: CallContext) = {
    val valueOfAuthReqHeaderField = sc.authReqHeaderField.getOrElse("")
      .replaceAll("Authorization:", "")
      .replaceAll("Bearer", "")
      .trim()
    valueOfAuthReqHeaderField
  }
  /*
    Method for Old Style Endpoints
   */
  def getUserFromOAuth2Header(sc: CallContext): (Box[User], Option[CallContext]) = {

    val username = JwtUtil.getSubject(getValueOfOAuh2HeaderField(sc)).getOrElse("")

    (Users.users.vend.getUserByUserName(username), Some(sc))

  }
  /*
    Method for New Style Endpoints
   */
  def getUserFromOAuth2HeaderFuture(sc: CallContext): Future[(Box[User], Option[CallContext])] = {

    val username = JwtUtil.getSubject(getValueOfOAuh2HeaderField(sc)).getOrElse("")

    (Users.users.vend.getUserByUserName(username), Some(sc))

    for {
      user <- Users.users.vend.getUserByUserNameFuture(username)
    } yield {
      (user, Some(sc))
    }

  }


}