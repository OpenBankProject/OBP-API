/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api

import code.Token.Tokens
import code.model.{Token, TokenType}
import net.liftweb.common.{Box, Full, Loggable}
import net.liftweb.http.{JsonResponse, S}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

case class ErrorMessage(
  error : String
)

case class SuccessMessage(
  success : String
)

case class TokenValidity(
  isValid : Boolean
)

case class ApplicationInformation(
  name : String,
  callbackURL : String
)

case class Verifier(
  value : String
)

case class UserData(
  id : Long
)

/**
* this object provides the API call required for the Bank mock,
* They are required during the User authentication / Application Authorization steps
* that the Bank Mock need to handle as part of the OAuth 1.0 protocol authentication.
*/
object BankMockAPI extends RestHelper with Loggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  implicit def successToJson(success: SuccessMessage): JValue = Extraction.decompose(success)
  implicit def TokenValidityToJson(msg: TokenValidity): JValue = Extraction.decompose(msg)
  implicit def applicationInfoToJson(info: ApplicationInformation): JValue = Extraction.decompose(info)
  implicit def verifierToJson(verifier: Verifier): JValue = Extraction.decompose(verifier)

  //extract and compare the sent key with the local one (shared secret)
  def isValidKey : Boolean = {
    val sentKey : Box[String] =
      for{
        req <- S.request
        sentKey <- req.header("BankMockKey")
      } yield sentKey
    val localKey : Box[String] = Props.get("BankMockKey")
    localKey == sentKey
  }

  def requestToken(token : String) : Box[Token] =
    Tokens.tokens.vend.getTokenByKeyAndType(token, TokenType.Request)

  serve("obp" / "v1.0" prefix {
    case "request-token-verification" :: Nil JsonGet _ => {
      if(isValidKey)
        S.param("oauth_token") match {
          case Full(token) => {
            requestToken(token) match {
              case Full(tkn) => JsonResponse(TokenValidity(tkn.isValid), Nil, Nil, 200)
              case _ => JsonResponse(ErrorMessage("invalid or expired request token"), Nil, Nil, 401)
            }
          }
          case _ => JsonResponse(ErrorMessage("No request token"), Nil, Nil, 401)
        }
      else
        JsonResponse(ErrorMessage("No key found or wrong key"), Nil, Nil, 401)
    }

    case "application-information" :: Nil JsonGet _ => {
      if(isValidKey)
        S.param("oauth_token") match {
          case Full(token) => {
            requestToken(token) match {
              case Full(tkn) =>
                if(tkn.isValid)
                  tkn.consumerId.foreign match {
                    case Full(app) => {
                      val applicationInfo = ApplicationInformation(
                        name  = app.name,
                        callbackURL = tkn.callbackURL.get
                      )
                      JsonResponse(applicationInfo, Nil, Nil, 200)
                    }
                    case _ => JsonResponse(ErrorMessage("Application not found"), Nil, Nil, 400)
                  }
                else
                  JsonResponse(ErrorMessage("Expired request token"), Nil, Nil, 401)
              case _ => JsonResponse(ErrorMessage("Request token not found"), Nil, Nil, 401)
            }
          }
          case _ => JsonResponse(ErrorMessage("No request token"), Nil, Nil, 401)
        }
      else
        JsonResponse(ErrorMessage("No key found or wrong key"), Nil, Nil, 401)
    }
    case "verifiers" :: Nil JsonPost json -> _ => {
      if(isValidKey)
        S.param("oauth_token") match {
          case Full(token) => {
            tryo{
              json.extract[UserData]
            } match {
              case Full(userData) => {
                requestToken(token) match {
                  case Full(tkn) =>{
                    //associate the token with the user
                    tkn.userForeignKey(userData.id)
                    val verifier = tkn.gernerateVerifier
                    tkn.save
                    JsonResponse(Verifier(verifier), Nil, Nil, 200)
                  }
                  case _ => JsonResponse(ErrorMessage("Request token not found"), Nil, Nil, 401)
                }
              }
              case _ => JsonResponse(ErrorMessage("Wrong JSON format"), Nil, Nil, 401)
            }
          }
          case _ => JsonResponse(ErrorMessage("No OAuth token"), Nil, Nil, 401)
        }
      else
        JsonResponse(ErrorMessage("No key found or wrong key"), Nil, Nil, 401)
    }
  })
}