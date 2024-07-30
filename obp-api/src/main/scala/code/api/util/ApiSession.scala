package code.api.util

import code.api.JSONFactoryDAuth
import java.util.{Date, UUID}
import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.oauth1a.OauthParams._
import code.api.util.APIUtil._
import code.api.util.AuthenticationType.{Anonymous, DirectLogin, GatewayLogin, DAuth, OAuth2_OIDC, OAuth2_OIDC_FAPI}
import code.api.util.ErrorMessages.{BankAccountNotFound, UserNotLoggedIn}
import code.api.util.RateLimitingJson.CallLimit
import code.context.UserAuthContextProvider
import code.customer.CustomerX
import code.model.{Consumer, _}
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{EnumValue, OBPEnumeration}
import net.liftweb.common.{Box, Empty}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

case class CallContext(
                        gatewayLoginRequestPayload: Option[PayloadOfJwtJSON] = None, //Never update these values inside the case class !!!
                        gatewayLoginResponseHeader: Option[String] = None,
                        dauthRequestPayload: Option[JSONFactoryDAuth.PayloadOfJwtJSON] = None, //Never update these values inside the case class !!!
                        dauthResponseHeader: Option[String] = None,
                        spelling: Option[String] = None,
                        user: Box[User] = Empty,
                        consenter: Box[User] = Empty,
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
                        operationId: Option[String] = None, // Dynamic Endpoint Unique Identifier. Important for Rate Limiting.
                        authReqHeaderField: Box[String] = Empty,
                        directLoginParams: Map[String, String] = Map(),
                        oAuthParams: Map[String, String] = Map(),
                        httpCode: Option[Int] = None,
                        httpBody: Option[String] = None,
                        requestHeaders: List[HTTPParam] = Nil,
                        rateLimiting: Option[CallLimit] = None,
                        xRateLimitLimit : Long = -1,
                        xRateLimitRemaining : Long = -1,
                        xRateLimitReset : Long = -1,
                        paginationOffset : Option[String] = None,
                        paginationLimit : Option[String] = None
                      ) extends MdcLoggable {
  
  //This is only used to connect the back adapter. not useful for sandbox mode.
  def toOutboundAdapterCallContext: OutboundAdapterCallContext= {
    for{
      user <- this.user //If there is no user, then will go to `.openOr` method, to return anonymousAccess box.
      username <- tryo(Some(user.name))
      currentResourceUserId <- tryo(Some(user.userId))
      consumerId = this.consumer.map(_.consumerId.get).openOr("") // if none, just return ""
      permission <- Views.views.vend.getPermissionForUser(user)
      views <- tryo(permission.views)
      linkedCustomers <- tryo(CustomerX.customerProvider.vend.getCustomersByUserId(user.userId))
      likedCustomersBasic = if (linkedCustomers.isEmpty) None else Some(createInternalLinkedBasicCustomersJson(linkedCustomers))
      userAuthContexts<- UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(user.userId)
      basicUserAuthContextsFromDatabase = if (userAuthContexts.isEmpty) None else Some(createBasicUserAuthContextJson(userAuthContexts))
      generalContextFromPassThroughHeaders = createBasicUserAuthContextJsonFromCallContext(this)
      basicUserAuthContexts = Some(basicUserAuthContextsFromDatabase.getOrElse(List.empty[BasicUserAuthContext]))
      authViews<- tryo(
        for{
          view <- views
          (account, callContext )<- code.bankconnectors.LocalMappedConnector.getBankAccountLegacy(view.bankId, view.accountId, Some(this)) ?~! {BankAccountNotFound}
          internalCustomers = createAuthInfoCustomersJson(account.customerOwners.toList)
          internalUsers = createAuthInfoUsersJson(account.userOwners.toList)
          viewBasic = ViewBasic(view.viewId.value, view.name, view.description)
          accountBasic =  AccountBasic(
            account.accountId.value,
            account.accountRoutings,
            internalCustomers.customers,
            internalUsers.users)
        }yield
          AuthView(viewBasic, accountBasic)
      )
    } yield{
      OutboundAdapterCallContext(
        correlationId = this.correlationId,
        sessionId = this.sessionId,
        consumerId = Some(consumerId),
        generalContext = Some(generalContextFromPassThroughHeaders),
        outboundAdapterAuthInfo = Some(OutboundAdapterAuthInfo(
          userId = currentResourceUserId,
          username = username,
          linkedCustomers = likedCustomersBasic,
          userAuthContext = basicUserAuthContexts,
          if (authViews.isEmpty) None else Some(authViews)))
      )
    }}.openOr(OutboundAdapterCallContext( //For anonymousAccess endpoints, there are no user info
      this.correlationId,
      this.sessionId))

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
      operationId = this.operationId,
      httpCode = this.httpCode,
      httpBody = this.httpBody,
      authReqHeaderField = this.authReqHeaderField.toOption,
      requestHeaders = this.requestHeaders,
      partialFunctionName = this.resourceDocument.map(_.partialFunctionName).getOrElse(""),
      directLoginToken = this.directLoginParams.get("token").getOrElse(""),
      oAuthToken = this.oAuthParams.get(TokenName).getOrElse(""),
      xRateLimitLimit = this.xRateLimitLimit,
      xRateLimitRemaining = this.xRateLimitRemaining,
      xRateLimitReset = this.xRateLimitReset,
      paginationOffset = this.paginationOffset,
      paginationLimit = this.paginationLimit
    )
  }

  // for endpoint body convenient get userId
  def userId: String  = user.map(_.userId).openOrThrowException(UserNotLoggedIn)
  def userPrimaryKey: UserPrimaryKey = user.map(_.userPrimaryKey).openOrThrowException(UserNotLoggedIn)
  def loggedInUser: User = user.openOrThrowException(UserNotLoggedIn)
  // for endpoint body convenient get cc.callContext
  def callContext: Option[CallContext] = Option(this)

  def authType: AuthenticationType = {
    if(hasGatewayHeader(authReqHeaderField)) {
      GatewayLogin
    } else if(requestHeaders.exists(_.name==DAuthHeaderKey)) { // DAuth Login
      DAuth
    } else if(has2021DirectLoginHeader(requestHeaders)) { // Direct Login
      DirectLogin
    }  else if(hasDirectLoginHeader(authReqHeaderField)) { // Direct Login Deprecated
      DirectLogin
    } else if(hasAnOAuthHeader(authReqHeaderField)) {
      AuthenticationType.`OAuth1.0a`
    //↓ have no client certificate, the request should contains Google or Yahoo id token OIDC way
    } else if(hasAnOAuth2Header(authReqHeaderField) && APIUtil.`getPSD2-CERT`(requestHeaders).isEmpty) {
      OAuth2_OIDC
    } else if(hasAnOAuth2Header(authReqHeaderField)) {
      OAuth2_OIDC_FAPI
    } else {
      Anonymous
    }
  }
}

sealed trait AuthenticationType extends EnumValue
object AuthenticationType extends OBPEnumeration[AuthenticationType]{
  object DirectLogin extends AuthenticationType
  object `OAuth1.0a` extends AuthenticationType {
    override def toString: String = "OAuth1.0a"
  }
  object GatewayLogin extends AuthenticationType
  object DAuth extends AuthenticationType
  object OAuth2_OIDC extends AuthenticationType
  object OAuth2_OIDC_FAPI extends AuthenticationType
  object Anonymous extends AuthenticationType
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
                            operationId: Option[String] = None,
                            httpCode: Option[Int] = None,
                            httpBody: Option[String] = None,
                            authReqHeaderField: Option[String] = None,
                            requestHeaders: List[HTTPParam] = Nil,
                            partialFunctionName: String,
                            directLoginToken: String,
                            oAuthToken: String,
                            xRateLimitLimit : Long = -1,
                            xRateLimitRemaining : Long = -1,
                            xRateLimitReset : Long = -1,
                            paginationOffset : Option[String] = None,
                            paginationLimit : Option[String] = None
                           )

trait LoginParam
case class GatewayLoginRequestPayload(jwtPayload: Option[PayloadOfJwtJSON]) extends LoginParam
case class GatewayLoginResponseHeader(jwt: Option[String]) extends LoginParam
case class DAuthRequestPayload(jwtPayload: Option[JSONFactoryDAuth.PayloadOfJwtJSON]) extends LoginParam
case class DAuthResponseHeader(jwt: Option[String]) extends LoginParam

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
        Some(CallContext(spelling = s.spelling)) //Some fields default value is NONE.
      case Some(v) =>
        Some(v.copy(spelling = s.spelling))
    }
  }

  def updateCallContext(jwt: LoginParam, cnt: Option[CallContext]): Option[CallContext] = {
    jwt match {
      case GatewayLoginRequestPayload(None) | DAuthRequestPayload(None) =>
        cnt
      case GatewayLoginResponseHeader(None) | DAuthResponseHeader(None) =>
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
      case DAuthRequestPayload(Some(jwtPayload)) =>
        cnt match {
          case Some(v) =>
            Some(v.copy(dauthRequestPayload = Some(jwtPayload)))
          case None =>
            Some(CallContext(dauthRequestPayload = Some(jwtPayload), dauthResponseHeader = None, spelling = None))
        }
      case DAuthResponseHeader(Some(j)) =>
        cnt match {
          case Some(v) =>
            Some(v.copy(dauthResponseHeader = Some(j)))
          case None =>
            Some(CallContext(dauthRequestPayload = None, dauthResponseHeader = Some(j), spelling = None))
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