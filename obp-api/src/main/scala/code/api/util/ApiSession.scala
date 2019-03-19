package code.api.util

import java.util.{Date, UUID}

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.RequestHeader
import code.api.oauth1a.OauthParams._
import code.api.util.APIUtil.{ResourceDoc, useISO20022Spelling, useOBPSpelling}
import code.model.Consumer
import com.openbankproject.commons.dto.CallContextAkka
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers

case class CallContext(
                       gatewayLoginRequestPayload: Option[PayloadOfJwtJSON] = None, //Never update these values inside the case class !!!  
                       gatewayLoginResponseHeader: Option[String] = None,
                       spelling: Option[String] = None,
                       user: Box[User] = Empty,
                       consumer: Box[Consumer] = Empty,
                       ipAddress: String = "",
                       resourceDocument: Option[ResourceDoc] = None,
                       startTime: Option[Date] = Some(Helpers.now),
                       endTime: Option[Date] = None,
                       correlationId: String = "",
                       sessionId: Option[String] = None, //Only this value must be used for cache key !!!   
                       url: String = "",
                       verb: String = "",
                       implementedInVersion: String = "",
                       authReqHeaderField: Box[String] = Empty,
                       directLoginParams: Map[String, String] = Map(),
                       oAuthParams: Map[String, String] = Map(),
                       httpCode: Option[Int] = None,
                       requestHeaders: List[HTTPParam] = Nil,
                       `X-Rate-Limit-Limit` : Long = -1,
                       `X-Rate-Limit-Remaining` : Long = -1,
                       `X-Rate-Limit-Reset` : Long = -1
                      ) {

  /**
    * Purpose of this helper function is to get rid of unnecessary and heavy data before serialization.
    * For instance before we send it from the North Side to the Adapter(the South side)
    * @return CallContext without ResourceDoc type
    */
  def removeResourceDocument: CallContext = this.copy(resourceDocument = None)
  /**
    * Purpose of this helper function is to transform data for Akka's connector serialization.
    * @return object which type is CallContextAkka
    */
  def toCallContextAkka: CallContextAkka = 
    CallContextAkka(
      userId = this.user.map(_.userId).toOption,
      consumerId = this.consumer.map(_.consumerId.get).toOption,
      correlationId = this.correlationId,
      sessionId = this.sessionId
    )
  
  def toLight: CallContextLight = {
    CallContextLight(
      gatewayLoginRequestPayload = this.gatewayLoginRequestPayload,
      gatewayLoginResponseHeader = this.gatewayLoginResponseHeader,
      userId = this.user.map(_.userId).toOption,
      userName = this.user.map(_.name).toOption,
      consumerId = this.consumer.map(_.id.get).toOption,
      appName = this.consumer.map(_.name.get).toOption,
      developerEmail = this.consumer.map(_.developerEmail.get).toOption,
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
      oAuthToken = this.oAuthParams.get(TokenName).getOrElse(""),
      `X-Rate-Limit-Limit` = this.`X-Rate-Limit-Limit`,
      `X-Rate-Limit-Remaining` = this.`X-Rate-Limit-Remaining`,
      `X-Rate-Limit-Reset` = this.`X-Rate-Limit-Reset`
    )
  }
  /**
    * Purpose of this helper function is to get the Consent-Id value from a Request Headers.
    * @return the Consent-Id value from a Request Header as a String
    */
  def getConsentId(): String = {
    this.requestHeaders.toSet.filter(_.name == RequestHeader.`Consent-Id`).toList match {
      case x :: Nil => x.values.mkString(", ")
      case _ => ""
    }
  }
  
}

case class CallContextLight(gatewayLoginRequestPayload: Option[PayloadOfJwtJSON] = None,
                            gatewayLoginResponseHeader: Option[String] = None,
                            userId: Option[String] = None,
                            userName: Option[String] = None,
                            consumerId: Option[Long] = None,
                            appName: Option[String] = None,
                            developerEmail: Option[String] = None,
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
                            oAuthToken: String,
                            `X-Rate-Limit-Limit` : Long = -1,
                            `X-Rate-Limit-Remaining` : Long = -1,
                            `X-Rate-Limit-Reset` : Long = -1
                           )

trait GatewayLoginParam
case class GatewayLoginRequestPayload(jwtPayload: Option[PayloadOfJwtJSON]) extends GatewayLoginParam
case class GatewayLoginResponseHeader(jwt: Option[String]) extends GatewayLoginParam

case class Spelling(spelling: Box[String])

object ApiSession {

  val emptyPayloadOfJwt = PayloadOfJwtJSON(login_user_name = "", is_first = true, app_id = "", app_name = "", cbs_id = "", time_stamp = "", cbs_token = None, session_id = None)

  /**
    * This method accept a callContext, and return the new CallContext with the new callContesxt.sessionId
    */
  def createSessionId(callContext: Option[CallContext]): Option[CallContext] = {
    val sessionId = Some(UUID.randomUUID().toString)
    callContext.map(_.copy(sessionId = sessionId ))
  }
  
  /**
    * Will update the callContext.sessionId using the gatewayLoginRequestPayload.session_id.
    * This is used for GatewayLogin for now. Only when is_first = false will call this method.
    */
  def updateSessionId(callContext: Option[CallContext]): Option[CallContext] = {
    val gatewayLoginRequestSessionId = callContext.map(_.gatewayLoginRequestPayload.map(_.session_id)).flatten.flatten
    callContext.map(_.copy(sessionId = gatewayLoginRequestSessionId))
  }

  /**
    * Used for update the callContext.sessionId by the parameter . 
    */
  def updateSessionId(callContext: Option[CallContext], newSessionId: String): Option[CallContext] = {
    callContext.map(_.copy(sessionId = Some(newSessionId)))
  }
  
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
