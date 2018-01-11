package code.api.util

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util.APIUtil.{useISO20022Spelling, useOBPSpelling}
import net.liftweb.common.Box
import net.liftweb.json.JsonAST.JValue

case class SessionContext(
                           gatewayLoginRequestPayload: Option[PayloadOfJwtJSON],
                           gatewayLoginResponseHeader: Option[String],
                           spelling: Option[String]
                         )
trait GatewayLoginParam
case class GatewayLoginRequestPayload(jwtPayload: Option[PayloadOfJwtJSON]) extends GatewayLoginParam
case class GatewayLoginResponseHeader(jwt: Option[String]) extends GatewayLoginParam

case class Spelling(spelling: Box[String])

object ApiSession {

  val emptyPayloadOfJwt = PayloadOfJwtJSON(login_user_name = "", is_first = true, app_id = "", app_name = "", cbs_id = "", time_stamp = "", cbs_token = None)

  def updateSessionContext(s: Spelling, cnt: Option[SessionContext]): Option[SessionContext] = {
    cnt match {
      case None =>
        Some(SessionContext(gatewayLoginRequestPayload = None, gatewayLoginResponseHeader = None, spelling = s.spelling))
      case Some(v) =>
        Some(v.copy(spelling = s.spelling))
    }
  }

  def updateSessionContext(jwt: GatewayLoginParam, cnt: Option[SessionContext]): Option[SessionContext] = {
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
            Some(SessionContext(gatewayLoginRequestPayload = Some(jwtPayload), gatewayLoginResponseHeader = None, spelling = None))
        }
      case GatewayLoginResponseHeader(Some(j)) =>
        cnt match {
          case Some(v) =>
            Some(v.copy(gatewayLoginResponseHeader = Some(j)))
          case None =>
            Some(SessionContext(gatewayLoginRequestPayload = None, gatewayLoginResponseHeader = Some(j), spelling = None))
        }
    }
  }

  def getGatawayLoginRequestInfo(cnt: Option[SessionContext]): PayloadOfJwtJSON = {
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

  def processJson(j: JValue, cnt: Option[SessionContext]): JValue = {
    cnt match {
      case Some(v) =>
        v.spelling match {
          case Some(s) if s == "ISO20022" =>
            useISO20022Spelling(j)
          case Some(s) if s == "OBP" =>
            useOBPSpelling(j)
          case None =>
            j
        }
      case None =>
        j
    }
  }

}
