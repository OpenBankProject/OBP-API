package code.api.util

import java.util.Date

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util.APIUtil.{ResourceDoc, useISO20022Spelling, useOBPSpelling}
import code.model.{Consumer, User}
import net.liftweb.common.{Box, Empty}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers

case class CallContext(gatewayLoginRequestPayload: Option[PayloadOfJwtJSON] = None,
                       gatewayLoginResponseHeader: Option[String] = None,
                       spelling: Option[String] = None,
                       user: Box[User] = Empty,
                       consumer: Box[Consumer] = Empty,
                       resourceDocument: Option[ResourceDoc] = None,
                       startTime: Option[Date] = Some(Helpers.now),
                       endTime: Option[Date] = None,
                       correlationId: String = "",
                       url: String = "",
                       verb: String = "",
                       implementedInVersion: String = "",
                       authReqHeaderField: Box[String] = Empty,
                       directLoginParams: Map[String, String] = Map(),
                       oAuthParams: Map[String, String] = Map(),
                       httpCode: Option[Int] = None,
                       requestHeaders: List[HTTPParam] = Nil
                      ) {
  def toLight: CallContextLight = {
    CallContextLight(
      gatewayLoginRequestPayload = this.gatewayLoginRequestPayload,
      gatewayLoginResponseHeader = this.gatewayLoginResponseHeader,
      user = this.user.toOption,
      spelling = this.spelling,
      startTime = this.startTime,
      endTime = this.endTime,
      correlationId = this.correlationId,
      url = this.url,
      verb = this.verb,
      implementedInVersion = this.implementedInVersion,
      httpCode = this.httpCode,
      authReqHeaderField = this.authReqHeaderField.toOption,
      partialFunctionName = this.resourceDocument.map(_.partialFunctionName).getOrElse(""),
      directLoginToken = this.directLoginParams.get("token").getOrElse(""),
      oAuthToken = this.oAuthParams.get("oauth_token").getOrElse("")
    )
  }
}

case class CallContextLight(gatewayLoginRequestPayload: Option[PayloadOfJwtJSON] = None,
                            gatewayLoginResponseHeader: Option[String] = None,
                            user: Option[User] = None,
                            spelling: Option[String] = None,
                            startTime: Option[Date] = Some(Helpers.now),
                            endTime: Option[Date] = None,
                            correlationId: String = "",
                            url: String = "",
                            verb: String = "",
                            implementedInVersion: String = "",
                            httpCode: Option[Int] = None,
                            authReqHeaderField: Option[String] = None,
                            partialFunctionName: String,
                            directLoginToken: String,
                            oAuthToken: String
                           )

trait GatewayLoginParam
case class GatewayLoginRequestPayload(jwtPayload: Option[PayloadOfJwtJSON]) extends GatewayLoginParam
case class GatewayLoginResponseHeader(jwt: Option[String]) extends GatewayLoginParam

case class Spelling(spelling: Box[String])

object ApiSession {

  val emptyPayloadOfJwt = PayloadOfJwtJSON(login_user_name = "", is_first = true, app_id = "", app_name = "", cbs_id = "", time_stamp = "", cbs_token = None)

  def updateCallContext(s: Spelling, cnt: Option[CallContext]): Option[CallContext] = {
    cnt match {
      case None =>
        Some(CallContext(gatewayLoginRequestPayload = None, gatewayLoginResponseHeader = None, spelling = s.spelling))
      case Some(v) =>
        Some(v.copy(spelling = s.spelling))
    }
  }

  def updateCallContext(jwt: GatewayLoginParam, cnt: Option[CallContext]): Option[CallContext] = {
    jwt match {
      case GatewayLoginRequestPayload(None) =>
        cnt
      case GatewayLoginResponseHeader(None) =>
        cnt
      case GatewayLoginRequestPayload(Some(jwtPayload)) =>
        cnt match {
          case Some(v) =>
            Some(v.copy(Some(jwtPayload)))
          case None =>
            Some(CallContext(gatewayLoginRequestPayload = Some(jwtPayload), gatewayLoginResponseHeader = None, spelling = None))
        }
      case GatewayLoginResponseHeader(Some(j)) =>
        cnt match {
          case Some(v) =>
            Some(v.copy(gatewayLoginResponseHeader = Some(j)))
          case None =>
            Some(CallContext(gatewayLoginRequestPayload = None, gatewayLoginResponseHeader = Some(j), spelling = None))
        }
    }
  }

  def getGatawayLoginRequestInfo(cnt: Option[CallContext]): PayloadOfJwtJSON = {
    cnt match {
      case Some(v) =>
        v.gatewayLoginRequestPayload match {
          case Some(jwtPayload) =>
            jwtPayload
          case None =>
            emptyPayloadOfJwt
        }
      case None =>
        emptyPayloadOfJwt
    }
  }

  def processJson(j: JValue, cnt: Option[CallContext]): JValue = {
    cnt match {
      case Some(v) =>
        v.spelling match {
          case Some(s) if s == "ISO20022" =>
            useISO20022Spelling(j)
          case Some(s) if s == "OBP" =>
            useOBPSpelling(j)
          case _ =>
            j
        }
      case None =>
        j
    }
  }

}
