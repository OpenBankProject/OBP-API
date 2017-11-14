package code.api.util

import code.api.JSONFactoryGateway.PayloadOfJwtJSON

case class SessionContext(
                           gatewayLoginRequestPayload: Option[PayloadOfJwtJSON],
                           gatewayLoginResponseHeader: Option[String]
                         )
trait GatewayLoginParam
case class GatewayLoginRequestPayload(jwtPayload: Option[PayloadOfJwtJSON]) extends GatewayLoginParam
case class GatewayLoginResponseHeader(jwt: Option[String]) extends GatewayLoginParam

object ApiSession {

  val emptyPayloadOfJwt = PayloadOfJwtJSON(login_user_name = "", is_first = true, app_id = "", app_name = "", temenos_id = "", time_stamp = "", cbs_token = None)

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
            Some(SessionContext(gatewayLoginRequestPayload = Some(jwtPayload), gatewayLoginResponseHeader = None))
        }
      case GatewayLoginResponseHeader(Some(j)) =>
        cnt match {
          case Some(v) =>
            Some(v.copy(gatewayLoginResponseHeader = Some(j)))
          case None =>
            Some(SessionContext(gatewayLoginRequestPayload = None, gatewayLoginResponseHeader = Some(j)))
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

}
