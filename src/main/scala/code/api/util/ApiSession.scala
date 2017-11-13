package code.api.util

import code.api.GatewayLogin
import code.api.JSONFactoryGateway.PayloadOfJwtJSON

case class SessionContext(gatewayLogin: Option[PayloadOfJwtJSON])

object ApiSession {

  val emptyPayloadOfJwt = PayloadOfJwtJSON(login_user_name = "", is_first = true, app_id = "", app_name = "", temenos_id = "", time_stamp = "", cbs_token = None)

  def updateSessionContext(jwt: Option[String], cnt: Option[SessionContext]): Option[SessionContext] = {
    jwt match {
      case None =>
        cnt
      case Some(j) =>
        val payload = GatewayLogin.getPayloadFromJwt(j)
        cnt match {
          case Some(v) =>
            Some(v.copy(Some(payload)))
          case None =>
            val payload = GatewayLogin.getPayloadFromJwt(j)
            Some(SessionContext(Some(payload)))
        }
    }
  }

  def getGatawayLoginInfo(cnt: Option[SessionContext]): PayloadOfJwtJSON = {
    cnt match {
      case Some(v) =>
        v.gatewayLogin match {
          case Some(gl) =>
            gl
          case None =>
            emptyPayloadOfJwt
        }
      case None =>
        emptyPayloadOfJwt
    }
  }

}
