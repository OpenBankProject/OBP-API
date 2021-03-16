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

import code.api.util.APIUtil._
import code.api.util.ErrorMessages.InvalidJsonFormat
import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.v3_1_0.{APIMethods310, UserAuthContextUpdateJson}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.UserAuthContextUpdateStatus
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{PostRequest, RequestVar, S, SHtml}
import net.liftweb.json.Formats
import net.liftweb.json
import net.liftweb.util.Helpers._

import scala.util.Either

class UserOnBoarding extends MdcLoggable with RestHelper with APIMethods310 {
  protected implicit override def formats: Formats = CustomJsonFormats.formats


  //This key can be set from props, the default in obp code is CUSTOMER_NUMBER
  private object identifierKey extends RequestVar(APIUtil.getPropsValue("default_auth_context_update_request_key", "CUSTOMER_NUMBER"))
  private object identifierValue extends RequestVar("123456")
  private object otpValue extends RequestVar("123456")


  def addUserAuthContextUpdateRequest = {
    identifierKey.set(S.param("key").openOr(identifierKey.get))
    // CUSTOMER_NUMBER --> Customer Number
    val inputValue = identifierKey.get.split("_").map(_.toLowerCase.capitalize).mkString(" ")
    "#add-user-auth-context-update-request-form-title *" #> s"Please enter your ${inputValue}:" &
    "#identifier-key" #> SHtml.textElem(identifierKey) &
      "#identifier-value" #> SHtml.textElem(identifierValue)  &
      "type=submit" #> SHtml.onSubmitUnit(addUserAuthContextUpdateRequestProcess)
  }

  def confirmUserAuthContextUpdateRequest = {
    "#otp-value" #> SHtml.textElem(otpValue) &
      "type=submit" #> SHtml.onSubmitUnit(confirmUserAuthContextUpdateRequestProcess)
  }

  private def addUserAuthContextUpdateRequestProcess() ={

    callCreateUserAuthContextUpdateRequest match {
      case Left(error) => S.error("identifier-error",error._1)
      case Right(response) => {
        tryo {json.parse(response).extract[UserAuthContextUpdateJson]} match {
          case Full(userAuthContextUpdateJson) => S.redirectTo(
            s"/confirm-user-auth-context-update-request?BANK_ID=${S.param("BANK_ID")openOr("")}&AUTH_CONTEXT_UPDATE_ID=${userAuthContextUpdateJson.user_auth_context_update_id}"
          )
          case _ => S.error("identifier-error",s"$InvalidJsonFormat The Json body should be the $UserAuthContextUpdateJson. " +
            s"Please check `Create User Auth Context Update Request` endpoint separately! ")
        }
      }
    }
    
  }

  private def confirmUserAuthContextUpdateRequestProcess() ={
    callConfirmUserAuthContextUpdateRequest match {
      case Left(error) => S.error("otp-value-error",error._1)
      case Right(response) => {
        tryo {json.parse(response).extract[UserAuthContextUpdateJson]} match {
          case Full(userAuthContextUpdateJson) if (userAuthContextUpdateJson.status.equals(UserAuthContextUpdateStatus.ACCEPTED.toString)) =>
            S.redirectTo("/")
          case Full(userAuthContextUpdateJson) => 
            S.error("otp-value-error",s"Current SCA status is ${userAuthContextUpdateJson.status}. Please double check OTP value.")
          case _ => S.error("otp-value-error",s"$InvalidJsonFormat The Json body should be the $UserAuthContextUpdateJson. " +
            s"Please check `Create User Auth Context Update Request` endpoint separately! ")
        }
      }
    }
  }

  private def callCreateUserAuthContextUpdateRequest: Either[(String, Int), String] = {

    val requestParam = List(
      S.param("BANK_ID"),
      S.param("SCA_METHOD")
    )

    if(requestParam.count(_.isDefined) < requestParam.size) {
      return Left(("There are one or many mandatory request parameter not present, please check request parameter: BANK_ID, SCA_METHOD", 500))
    }

    val pathOfEndpoint = List(
      "banks",
      S.param("BANK_ID")openOr(""),
      "users",
      "current",
      "auth-context-updates",
      S.param("SCA_METHOD")openOr("")
    )

    val requestBody = s"""{"key":"${identifierKey.get}","value":"${identifierValue.get}"}"""
    val authorisationsResult = callEndpoint(Implementations3_1_0.createUserAuthContextUpdateRequest, pathOfEndpoint, PostRequest, requestBody)

    authorisationsResult

  }

  private def callConfirmUserAuthContextUpdateRequest: Either[(String, Int), String] = {

    val requestParam = List(
      S.param("BANK_ID"),
      S.param("AUTH_CONTEXT_UPDATE_ID")
    )

    if(requestParam.count(_.isDefined) < requestParam.size) {
      return Left(("There are one or many mandatory request parameter not present, please check request parameter: BANK_ID, AUTH_CONTEXT_UPDATE_ID", 500))
    }

    val pathOfEndpoint = List(
      "banks",
      S.param("BANK_ID")openOr(""),
      "users",
      "current",
      "auth-context-updates",
      S.param("AUTH_CONTEXT_UPDATE_ID")openOr(""),
      "challenge"
    )

    val requestBody = s"""{"answer":"${otpValue.get}"}"""
    val authorisationsResult = callEndpoint(Implementations3_1_0.answerUserAuthContextUpdateChallenge, pathOfEndpoint, PostRequest, requestBody)

    authorisationsResult

  }

}
