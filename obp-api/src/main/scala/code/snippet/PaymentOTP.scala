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

import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.StartPaymentAuthorisationJson
import code.api.builder.PaymentInitiationServicePISApi.APIMethods_PaymentInitiationServicePISApi.{startPaymentAuthorisation, updatePaymentPsuData}
import code.api.util.APIUtil.OBPEndpoint
import code.api.util.CallContext
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.actor.LAFuture
import net.liftweb.common.{Empty, Failure, Full}
import net.liftweb.http.{BodyOrInputStream, JsonResponse, LiftResponse, ParamCalcInfo, PostRequest, PutRequest, Req, RequestType, RequestVar, S, SHtml}
import net.liftweb.util.Helpers._
import code.api.util.ErrorMessages.FutureTimeoutException
import net.liftweb.json
import net.liftweb.json.JsonAST.{JObject, JString}
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

import scala.util.Either
import scala.xml.NodeSeq

class PaymentOTP extends MdcLoggable {
  private implicit val formats = code.api.util.CustomJsonFormats.formats

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

        result.map(json.parse(_).extract[StartPaymentAuthorisationJson]) match {
          case Right(v) if(v.scaStatus == "finalised")=> {
            "#form_otp" #> "" &
              "#otp-validate-success p *" #> "OTP validate success." &
              "#otp-validate-errors" #> ""
          }
          case Right(v) => {
            form &
              "#otp-validate-success" #> "" &
              "#otp-validate-errors .errorContent *" #> s"Otp validate fail! ${v.psuMessage}"
          }
          case Left((msg, _)) => {
            form &
              "#otp-validate-success" #> "" &
              "#otp-validate-errors .errorContent *" #> s"Otp validate fail! $msg"
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

  /**
    * call an endpoint method
    * @param endpoint endpoint method
    * @param endpointPartPath endpoint method url slices, it is for endpoint the first case expression
    * @param requestType http request method
    * @param requestBody http request body
    * @param addlParams append request parameters
    * @return result of call endpoint method
    */
  def callEndpoint(endpoint: OBPEndpoint, endpointPartPath: List[String], requestType: RequestType, requestBody: String = "", addlParams: Map[String, String] = Map.empty): Either[(String, Int), String] = {
    val req: Req = S.request.openOrThrowException("no request object can be extract.")
    val pathOrigin = req.path
    val forwardPath = pathOrigin.copy(partPath = endpointPartPath)

    val body = Full(BodyOrInputStream(IOUtils.toInputStream(requestBody)))

    val paramCalcInfo = ParamCalcInfo(req.paramNames, req._params, Nil, body)
    val newRequest = new Req(forwardPath, req.contextPath, requestType, Full("application/json"), req.request, req.nanoStart, req.nanoEnd, false, () => paramCalcInfo, addlParams)

    val user = AuthUser.getCurrentUser
    val result = tryo {
      endpoint(newRequest)(CallContext(user = user))
    }

    val func: ((=> LiftResponse) => Unit) => Unit = result match {
      case Failure("Continuation", Full(continueException), _) => ReflectUtils.getCallByNameValue(continueException, "f").asInstanceOf[((=> LiftResponse) => Unit) => Unit]
      case _ => null
    }

    val future = new LAFuture[LiftResponse]
    val satisfyFutureFunction: (=> LiftResponse) => Unit = liftResponse => future.satisfy(liftResponse)
    func(satisfyFutureFunction)

    val timeoutOfEndpointMethod = 10 * 1000L // endpoint is async, but html template must not async, So here need wait for endpoint value.

    future.get(timeoutOfEndpointMethod) match {
      case Full(JsonResponse(jsExp, _, _, code)) if (code.toString.startsWith("20")) => Right(jsExp.toJsCmd)
      case Full(JsonResponse(jsExp, _, _, code)) => {
        val message = json.parse(jsExp.toJsCmd)
          .asInstanceOf[JObject]
          .obj
          .find(_.name == "message")
          .map(_.value.asInstanceOf[JString].s)
          .getOrElse("")
        Left((message, code))
      }
      case Empty => Left((FutureTimeoutException, 500))
    }
  }
}
