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
package code.snippet

import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi
import code.api.util.CallContext
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.actor.LAFuture
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.http.{BodyOrInputStream, JsonResponse, LiftResponse, ParamCalcInfo, PutRequest, Req, RequestVar, S, SHtml}
import net.liftweb.util.Helpers._
import code.api.util.ErrorMessages.FutureTimeoutException
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

import scala.util.{Either}
import scala.xml.NodeSeq

class PaymentOTP extends MdcLoggable {

  private object otpVar extends RequestVar("")
  private object submitButtonDefenseFlag extends RequestVar("")




  // Can be used to show link to an online form to collect more information about the App / Startup
  val registrationMoreInfoUrl = getWebUiPropsValue("webui_post_consumer_registration_more_info_url", "")

  val registrationMoreInfoText : String = registrationMoreInfoUrl match {
    case "" => ""
    case _  =>  getWebUiPropsValue("webui_post_consumer_registration_more_info_text", "Please tell us more your Application and / or Startup using this link.")
  }

  
  def validateOTP(in: NodeSeq): NodeSeq = {

    def submitButtonDefense: Unit = {
      submitButtonDefenseFlag("true")
    }

    val form = "form" #> {
      "#otp_input" #> SHtml.textElem(otpVar) &
        "type=submit" #> SHtml.submit("Send OTP", () => submitButtonDefense)
    }
    val page = if(S.post_?) {

      if(StringUtils.isBlank(otpVar.get)) {
        form &
          "#otp-validate-success" #> "" &
          "#otp-validate-errors .errorContent *" #> "please input OTP value"
      } else {

        val result = S.param("flow") match {
          case Full("payment") => processPaymentOTP
          case Full(notSupportFlow) =>  Left((s"flow $notSupportFlow is not correct.", 500))
          case _ =>  Left(("request parameter [flow] is mandatory, please add this parameter in url.", 500))
        }

        result match {
          case Right(_)=> {
            "#form_otp" #> "" &
              "#otp-validate-success p *" #> "OTP validate success." &
              "#otp-validate-errors" #> ""
          }
          case Left((msg, _)) => {
            val extractMsg = msg.replaceFirst(""".*(OBP-\d+: .+\.).*""", "$1")
            form &
              "#otp-validate-success" #> "" &
              "#otp-validate-errors .errorContent *" #> s"Otp validate fail! $extractMsg"
          }
        }
        
      }
    }
    else {
      form &
        "#otp-validate-errors" #> "" &
        "#otp-validate-success" #> ""
    }
    page(in)
  }


  private def processPaymentOTP: Either[(String, Int), String] = {
    val requestParam = List(S.param("paymentService"), S.param("paymentProduct"), S.param("paymentId"), Full("authorisations"), S.param("authorisationId"))

    if(requestParam.count(_.isDefined) < requestParam.size) {
      return Left(("There are one or many mandatory request parameter not present, please check request parameter: paymentService, paymentProduct, paymentId, authorisationId", 500))
    }

    val newUrl = requestParam.map(_.openOr(""))

    val req: Req = S.request.openOrThrowException("no request object can be extract.")
    val pathOrigin = req.path
    val forwardPath = pathOrigin.copy(partPath = newUrl)
    val json = s"""{"scaAuthenticationData":"${otpVar.get}"}"""
    val inputStream = IOUtils.toInputStream(json)
    val paramCalcInfo = ParamCalcInfo(Nil, Map.empty, Nil, Full(BodyOrInputStream(inputStream)))
    val newRequest = new Req(forwardPath, req.contextPath, PutRequest, Full("application/json"), req.request, req.nanoStart, req.nanoEnd, false, () => paramCalcInfo , Map.empty)

    val endpoint = APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData
    val user  = AuthUser.getCurrentUser
    val result = tryo{ endpoint(newRequest)(CallContext(user = user)) }

    val func: ((=> LiftResponse) => Unit) => Unit = result match {
      case Failure("Continuation", Full(continueException), _) => ReflectUtils.getCallByNameValue(continueException, "f").asInstanceOf[((=> LiftResponse) => Unit) => Unit]
      case _ => null
    }
    val future = new LAFuture[LiftResponse]
    val fb: (=> LiftResponse) => Unit = liftResponse => future.satisfy(liftResponse)

    func(fb)
    val timeoutOfEndpointMethod = 10 * 1000L // endpoint is async, but html template must not async, So here need wait for endpoint value.

    future.get(timeoutOfEndpointMethod) match {
      case Full(JsonResponse(jsExp, _, _, code)) if(code.toString.startsWith("20")) => Right(jsExp.toJsCmd)
      case Full(JsonResponse(jsExp, _, _, code)) => Left((jsExp.toJsCmd, code))
      case Empty => Left((FutureTimeoutException, 500))
    }
  }

}
