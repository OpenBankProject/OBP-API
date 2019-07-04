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
import net.liftweb.common.{Box, Full}
import net.liftweb.http.{BodyOrInputStream, JsonResponse, ParamCalcInfo, PutRequest, Req, RequestVar, S, SHtml}
import net.liftweb.util.Helpers._
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

import scala.xml.NodeSeq

class PaymentOTP(pathVariables: (String, String, String, String)) extends MdcLoggable {

  private object otpVar extends RequestVar("")
  private object submitButtonDefenseFlag extends RequestVar("")




  // Can be used to show link to an online form to collect more information about the App / Startup
  val registrationMoreInfoUrl = getWebUiPropsValue("webui_post_consumer_registration_more_info_url", "")

  val registrationMoreInfoText : String = registrationMoreInfoUrl match {
    case "" => ""
    case _  =>  getWebUiPropsValue("webui_post_consumer_registration_more_info_text", "Please tell us more your Application and / or Startup using this link.")
  }

  
  def validateOTP(in: NodeSeq): NodeSeq = {
    val (paymentService, paymentProduct, paymentId, authorisationId) = pathVariables

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
        val req: Req = S.request.openOrThrowException("no request object can be extract.")
        val pathOrigin = req.path
        val forwardPath = pathOrigin.copy(partPath = paymentService :: paymentProduct :: paymentId:: "authorisations" :: authorisationId :: Nil)
        val json = s"""{"scaAuthenticationData":"${otpVar.get}"}"""
        val inputStream = IOUtils.toInputStream(json)
        val paramCalcInfo = ParamCalcInfo(Nil, Map.empty, Nil, Full(BodyOrInputStream(inputStream)))
        val newRequest = new Req(forwardPath, req.contextPath, PutRequest, Full("application/json"), req.request, req.nanoStart, req.nanoEnd, false, () => paramCalcInfo , Map.empty)

        val endpoint = APIMethods_PaymentInitiationServicePISApi.updatePaymentPsuData
        val user  = AuthUser.getCurrentUser
        val result: Box[Box[JsonResponse]] = tryo{ endpoint(newRequest)(CallContext(user = user)) }
        result match {
          case Full(Full(JsonResponse(_, _, _, 200))) => {
            "#form_otp" #> "" &
              "#otp-validate-success p *" #> "OTP validate success." &
              "#otp-validate-errors" #> ""
          }
          case _ => {
            form &
              "#otp-validate-success" #> "" &
              "#otp-validate-errors .errorContent *" #> "Otp validate fail!"
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

}
