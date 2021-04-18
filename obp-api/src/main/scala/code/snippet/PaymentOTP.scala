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

import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.StartPaymentAuthorisationJson
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi.{startPaymentAuthorisation, updatePaymentPsuData}
import code.api.util.APIUtil._
import code.api.util.ErrorMessages.FutureTimeoutException
import code.api.util.{CallContext, CustomJsonFormats}
import code.api.v2_1_0.TransactionRequestWithChargeJSON210
import code.api.v4_0_0.APIMethods400
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.actor.LAFuture
import net.liftweb.common.{Empty, Failure, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{BodyOrInputStream, JsonResponse, LiftResponse, ParamCalcInfo, PostRequest, PutRequest, Req, RequestType, RequestVar, S, SHtml}
import net.liftweb.json
import net.liftweb.json.Formats
import net.liftweb.json.JsonAST.{JObject, JString}
import net.liftweb.util.Helpers._
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

import scala.util.Either
import scala.xml.NodeSeq

class PaymentOTP extends MdcLoggable with RestHelper with APIMethods400 {
  protected implicit override def formats: Formats = CustomJsonFormats.formats

  private object otpVar extends RequestVar("")
  private object submitButtonDefenseFlag extends RequestVar("")
  
  def validateOTP(in: NodeSeq): NodeSeq = {

    def submitButtonDefense: Unit = {
      submitButtonDefenseFlag("true")
    }

    val form = "form" #> {
      "#otp_input" #> SHtml.textElem(otpVar) &
        "type=submit" #> SHtml.submit("Send OTP", () => submitButtonDefense)
    }

    def PaymentOTP = {
      val result = S.param("flow") match {
        case Full("payment") => processPaymentOTP
        case Full(unSupportedFlow) => Left((s"flow $unSupportedFlow is not correct.", 500))
        case _ => Left(("request parameter [flow] is mandatory, please add this parameter in url.", 500))
      }

      result.map(json.parse(_).extract[StartPaymentAuthorisationJson]) match {
        case Right(v) if (v.scaStatus == "finalised") => {
          "#form_otp" #> "" &
            "#otp-validation-success p *" #> "OTP validation success." &
            "#otp-validation-errors" #> ""
        }
        case Right(v) => {
          form &
            "#otp-validation-success" #> "" &
            "#otp-validation-errors .errorContent *" #> s"Otp validation fail! ${v.psuMessage}"
        }
        case Left((msg, _)) => {
          form &
            "#otp-validation-success" #> "" &
            "#otp-validation-errors .errorContent *" #> s"Otp validation fail! $msg"
        }
      }
    }
    def transactionRequestOTP = {
      val result = S.param("flow") match {
        case Full("transaction_request") => processTransactionRequestOTP
        case Full(unSupportedFlow) => Left((s"flow $unSupportedFlow is not correct.", 500))
        case _ => Left(("request parameter [flow] is mandatory, please add this parameter in url.", 500))
      }

      result.map(json.parse(_).extract[TransactionRequestWithChargeJSON210]) match {
        case Right(v) => {
          "#form_otp" #> "" &
            "#otp-validation-success p *" #> "OTP validation success." &
            "#otp-validation-errors" #> ""
        }
        case Left((msg, _)) => {
          form &
            "#otp-validation-success" #> "" &
            "#otp-validation-errors .errorContent *" #> s"Otp validation fail! $msg"
        }
      }
    }

    val page = if(S.post_?) {
      if(StringUtils.isBlank(otpVar.get)) {
        form &
          "#otp-validation-success" #> "" &
          "#otp-validation-errors .errorContent *" #> "please input OTP value"
      } else {
        S.param("flow") match {
          case Full("payment") => PaymentOTP
          case Full("transaction_request") => transactionRequestOTP
          case _ => transactionRequestOTP
        }
      }
    }
    else {
      form &
        "#otp-validation-errors" #> "" &
        "#otp-validation-success" #> ""
    }
    page(in)
  }


  private def processPaymentOTP: Either[(String, Int), String] = {

    val requestParam = List(S.param("paymentService"), S.param("paymentProduct"), S.param("paymentId"))

    if(requestParam.count(_.isDefined) < requestParam.size) {
      return Left(("There are one or many mandatory request parameter not present, please check request parameter: paymentService, paymentProduct, paymentId", 500))
    }

    val pathOfEndpoint = requestParam.map(_.openOr("")) :+ "authorisations"

    val authorisationsResult = callEndpoint(startPaymentAuthorisation, pathOfEndpoint, PostRequest)

    authorisationsResult match {
      case left @Left((_, _)) => left

      case Right(v) => {
        val authorisationId = json.parse(v).extract[StartPaymentAuthorisationJson].authorisationId
        val requestBody = s"""{"scaAuthenticationData":"${otpVar.get}"}"""

        callEndpoint(updatePaymentPsuData, pathOfEndpoint :+ authorisationId, PutRequest, requestBody)
      }
    }



  }
  
  
  private def processTransactionRequestOTP: Either[(String, Int), String] = {

    val requestParam = List(
      S.param("id"),
      S.param("bankId"),
      S.param("accountId"),
      S.param("viewId"),
      S.param("transactionRequestType"),
      S.param("transactionRequestId")
    )

    if(requestParam.count(_.isDefined) < requestParam.size) {
      return Left(("There are one or many mandatory request parameter not present, please check request parameter: bankId, accountId, viewId, transactionRequestType, transactionRequestId", 500))
    }

    val pathOfEndpoint = List(
      "banks",
      S.param("bankId")openOr(""),
      "accounts",
      S.param("accountId")openOr(""),
      S.param("viewId")openOr(""),
      "transaction-request-types",
      S.param("transactionRequestType")openOr(""),
      "transaction-requests",
      S.param("transactionRequestId")openOr(""),
      "challenge"
    )

    val requestBody = s"""{"id":"${S.param("id").getOrElse("")}","answer":"${otpVar.get}"}"""

    val authorisationsResult = callEndpoint(Implementations4_0_0.answerTransactionRequestChallenge, pathOfEndpoint, PostRequest, requestBody)

    authorisationsResult

  }

}
