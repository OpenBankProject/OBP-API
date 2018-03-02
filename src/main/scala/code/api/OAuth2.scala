/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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

import code.api.util.{APIUtil, CallContext, ErrorMessages, JwtUtil}
import code.model.User
import code.users.Users
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

  private def verifyJwt(jwt: String) = {
    APIUtil.getPropsAsBoolValue("oauth2.jwt.use.ssl", false) match {
      case true =>
        JwtUtil.verifyRsaSignedJwt(jwt)
      case false =>
        JwtUtil.verifyHmacSignedJwt(jwt)
    }
  }

  private def validateAccessToken(accessToken: String) = {
    APIUtil.getPropsValue("oauth2.jwk_set.url") match {
      case Full(url) =>
        JwtUtil.validateAccessToken(accessToken, url)
      case ParamFailure(a, b, c, apiFailure : APIFailure) =>
        ParamFailure(a, b, c, apiFailure : APIFailure)
      case Failure(msg, t, c) =>
        Failure(msg, t, c)
      case _ =>
        Failure(ErrorMessages.Oauth2ThereIsNoUrlOfJwkSet)
    }
  }

  /*
    Method for Old Style Endpoints
   */
  def getUserFromOAuth2Header(sc: CallContext): (Box[User], Option[CallContext]) = {
    APIUtil.getPropsAsBoolValue("allow_oauth2_login", true) match {
      case true =>
        val value = getValueOfOAuh2HeaderField(sc)
        validateAccessToken(value) match {
          case Full(_) =>
            val username = JwtUtil.getSubject(value).getOrElse("")
            (Users.users.vend.getUserByUserName(username), Some(sc))
          case ParamFailure(a, b, c, apiFailure : APIFailure) =>
            (ParamFailure(a, b, c, apiFailure : APIFailure), Some(sc))
          case Failure(msg, t, c) =>
            (Failure(msg, t, c), Some(sc))
          case _ =>
            (Failure(ErrorMessages.Oauth2IJwtCannotBeVerified), Some(sc))
        }
      case false =>
        (Failure(ErrorMessages.Oauth2IsNotAllowed), Some(sc))
    }
  }
  /*
    Method for New Style Endpoints
   */
  def getUserFromOAuth2HeaderFuture(sc: CallContext): Future[(Box[User], Option[CallContext])] = {
    APIUtil.getPropsAsBoolValue("allow_oauth2_login", true) match {
      case true =>
        val value = getValueOfOAuh2HeaderField(sc)
        validateAccessToken(value) match {
          case Full(_) =>
            val username = JwtUtil.getSubject(value).getOrElse("")
            (Users.users.vend.getUserByUserName(username), Some(sc))
            for {
              user <- Users.users.vend.getUserByUserNameFuture(username)
            } yield {
              (user, Some(sc))
            }
          case ParamFailure(a, b, c, apiFailure : APIFailure) =>
            Future((ParamFailure(a, b, c, apiFailure : APIFailure), Some(sc)))
          case Failure(msg, t, c) =>
            Future((Failure(msg, t, c), Some(sc)))
          case _ =>
            Future((Failure(ErrorMessages.Oauth2IJwtCannotBeVerified), Some(sc)))
        }
      case false =>
        Future((Failure(ErrorMessages.Oauth2IsNotAllowed), Some(sc)))
    }
  }


}