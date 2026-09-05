package code.api.util

import org.json4s._
import code.api.JSONFactoryDAuth
import java.util.{Date, UUID}
import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util.APIUtil._
import code.api.util.AuthenticationType.{Anonymous, DirectLogin, GatewayLogin, DAuth, OAuth2_OIDC, OAuth2_OIDC_FAPI}
import code.api.util.ErrorMessages.{BankAccountNotFound, AuthenticatedUserIsRequired}
import code.api.util.RateLimitingJson.CallLimit
import code.context.UserAuthContextProvider
import code.customer.CustomerX
import code.model._
import code.util.Helper.MdcLoggable
import code.util.SecureLogging
import code.views.Views
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{EnumValue, OBPEnumerationWithType, ReflectUtils}
import net.liftweb.common.{Box, Empty}
import org.json4s.JsonAST.JValue
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

case class CallContext(
                        gatewayLoginRequestPayload: Option[PayloadOfJwtJSON] = None, //Never update these values inside the case class !!!
                        gatewayLoginResponseHeader: Option[String] = None,
                        dauthRequestPayload: Option[JSONFactoryDAuth.PayloadOfJwtJSON] = None, //Never update these values inside the case class !!!
                        dauthResponseHeader: Option[String] = None,
                        spelling: Option[String] = None,
                        // The AUTHENTICATED principal. Not always a person: under a consent this is the
                        // consent's own shadow user. Stored data (metric rows, created_by_user_id columns)
                        // always records this id — the human is resolved at read time via the consent table.
                        user: Box[User] = Empty,
                        // The human who CREATED the consent this request runs under. Populated only by the
                        // OBP-native consent path (applyConsentRulesCommon) from the consent JWT's
                        // createdByUserId claim, resolved against the users table. For OBP-native consents
                        // the creator is the granting human (they create their own consent in the Portal).
                        // Not set by Berlin Group / UK flows, where the consent may be created by a TPP flow
                        // with no human logged in — see `consenter` for those.
                        // Read via humanUser / accountableUserId, where it takes precedence over consenter.
                        onBehalfOfUser: Box[User] = Empty,
                        // The human (PSU) who AUTHORISED the consent this request runs under — the owner of
                        // record, from the consent table's userId (bound by updateConsentUser during the
                        // authorise ceremony). Populated by the Berlin Group and UK consent paths, whose
                        // consents are created by TPP flows and only gain their human at authorisation.
                        // The UK ownership check (checkUKConsent) compares the consent's userId against this.
                        // In practice onBehalfOfUser and consenter are never both set: each consent standard
                        // populates the one whose source is authoritative for it.
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
                        httpCode: Option[Int] = None,
                        httpBody: Option[String] = None,
                        requestHeaders: List[HTTPParam] = Nil,
                        rateLimiting: Option[CallLimit] = None,
                        xRateLimitLimit : Long = -1,
                        xRateLimitRemaining : Long = -1,
                        xRateLimitReset : Long = -1,
                        paginationOffset : Option[String] = None,
                        paginationLimit : Option[String] = None,
                        // Validated entities from ResourceDoc middleware (http4s)
                        bank: Option[Bank] = None,
                        bankAccount: Option[BankAccount] = None,
                        view: Option[View] = None,
                        counterparty: Option[CounterpartyTrait] = None,
                        // Set when the request is authenticated via a consent. Persisted on metric rows for search/audit.
                        consentReferenceId: Option[String] = None,
                        // Set when a UK Open Banking consent authenticated this request via the Consent-Id /
                        // Consent-JWT header rather than a Bearer token. checkUKConsent short-circuits on this:
                        // the consent has already been fully validated (standard, status, expiry, consumer,
                        // signature) and the PSU resolved from MappedConsent.mUserId.
                        ukConsentId: Option[String] = None,
                        // Set when a Bearer token's consent_id claim named a UK Open Banking consent that could
                        // not be resolved to its shadow user. The principal is then still the PSU, which is wider
                        // than the consent -- so this is what checkUKConsent refuses on.
                        //
                        // Deliberately a flag rather than a failed authentication. The swap runs for every
                        // request, but only the data-read endpoints run on the principal; the consent-management
                        // endpoints (GET/DELETE account-access-consents) do not, and they are how a TPP inspects
                        // or revokes the very consent that cannot be resolved. Failing at authentication would
                        // lock that door too -- and, since those endpoints are UserOrApplication, would report it
                        // as ApplicationNotIdentified rather than as anything to do with the consent.
                        ukConsentUnresolved: Option[String] = None,
                        // How the caller's certificate was established (PeerTrust.Resolution.mode):
                        // "direct", "forwarded" or "none". Persisted on metric rows as certificate_trust.
                        certificateTrust: Option[String] = None,
                        // The specifics behind certificateTrust (PeerTrust.Resolution.detail): the forwarding
                        // proxy's canonical subject DN, or the reason no caller was identified; None for
                        // "direct". Persisted on metric rows as certificate_trust_detail.
                        certificateTrustDetail: Option[String] = None
                      ) extends MdcLoggable {
  override def toString: String = SecureLogging.maskSensitive(
    s"${this.getClass.getSimpleName}(${this.productIterator.mkString(", ")})"
  )
  /**
   * The human being this request is on behalf of, where one is known.
   *
   * `user` is not always a person: a consent resolves to a shadow user that exists only for that
   * consent (Berlin Group, OBP-native, and -- since UK consents moved to the same model -- UK too).
   * Anything that must name a human rather than a principal reads this instead: the CBS adapter,
   * which tells the core banking system who is asking, and the consent ownership checks.
   * Stored data (metric rows included) always carries the authenticated principal; the human is
   * resolved at read time via the consent table (see accountableUserId).
   */
  def humanUser: Box[User] = onBehalfOfUser.or(consenter).or(user)

  //This is only used to connect the back adapter. not useful for sandbox mode.
  def toOutboundAdapterCallContext: OutboundAdapterCallContext= {
    for{
      user <- this.user //If there is no user, then will go to `.openOr` method, to return anonymousAccess box.
      // The adapter is told which human is asking. A shadow user has no name and no customer links,
      // so sending it would make every consent-borne request look like a different, unknown caller.
      psu <- this.humanUser
      username <- tryo(Some(psu.name))
      currentResourceUserId <- tryo(Some(psu.userId))
      consumerId = this.consumer.map(_.consumerId).openOr("") // if none, just return ""
      permission <- Views.views.vend.getPermissionForUser(user)
      views <- tryo(permission.views)
      linkedCustomers <- tryo(CustomerX.customerProvider.vend.getCustomersByUserId(psu.userId))
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
          if (authViews.isEmpty) None else Some(authViews))),
        outboundAdapterConsenterInfo = 
          if (this.consenter.isDefined){
            Some(OutboundAdapterAuthInfo(
              username = this.consenter.toOption.map(_.name)))//TODO, here we may added more field to the consenter, at the moment only username is useful
          }else{
            None
          }
      )
    }}.openOr(OutboundAdapterCallContext( //For anonymousAccess endpoints, there are no user info
      this.correlationId,
      this.sessionId))

  def toLight: CallContextLight = {
    CallContextLight(
      gatewayLoginRequestPayload = this.gatewayLoginRequestPayload,
      gatewayLoginResponseHeader = this.gatewayLoginResponseHeader,
      // Like for like with CallContext: userId/userName are the AUTHENTICATED principal
      // (CallContext.user), never a resolved human. Under a consent that principal is the
      // consent's own shadow user (a per-consent UUID with an empty name) — the on-behalf-of
      // human is not stored here but resolved at read time via the consent table
      // (consentReferenceId below -> consent.userId), see CallContext.accountableUserId.
      userId = this.user.map(_.userId).toOption,
      userName = this.user.map(_.name).toOption,
      consumerId = this.consumer.map(_.consumerId).toOption,
      appName = this.consumer.map(_.name).toOption,
      developerEmail = this.consumer.map(_.developerEmail).toOption,
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
      xRateLimitLimit = this.xRateLimitLimit,
      xRateLimitRemaining = this.xRateLimitRemaining,
      xRateLimitReset = this.xRateLimitReset,
      paginationOffset = this.paginationOffset,
      paginationLimit = this.paginationLimit,
      consentReferenceId = this.consentReferenceId,
      certificateTrust = this.certificateTrust,
      certificateTrustDetail = this.certificateTrustDetail
    )
  }

  // for endpoint body convenient get userId
  def userId: String  = user.map(_.userId).openOrThrowException(AuthenticatedUserIsRequired)

  /**
   * The ACCOUNTABLE identity this request is really about — the user_id that durable
   * state (creator role grants, account holders, entitlement requests) and attribution
   * (metrics families, "my" queries) bind to. "Accountable" deliberately hints at a
   * legal person: today resolution always ends at the human who granted the consent,
   * but the contract is accountability, not species — if durable, sponsored agent
   * identities are ever admitted as principals in their own right, resolution may stop
   * at such an agent without this name becoming a lie (unlike the previous name,
   * effectiveHumanUserId).
   *
   * The authenticated `user` may be the accountable party themselves, or a consent user
   * minted by a Consent they granted (e.g. Opey / MCP acting under a consent) — consent
   * users are ephemeral and must never hold durable state (see addEntitlement's guard).
   * Resolution order:
   *  1. `onBehalfOfUser` or `consenter`, when a middleware populated them (free);
   *  2. otherwise resolve via the delegation registry: the caller's ResourceUser row's
   *     CreatedByConsentId names the Consent that minted it, and that Consent's userId
   *     names the granting human;
   *  3. otherwise the caller IS the accountable party.
   *
   * IMPORTANT: this reads only the authenticated user and server-written columns
   * (ResourceUser.CreatedByConsentId, MappedConsent.mUserId). It deliberately takes no
   * parameters so nothing caller-asserted (body/header/query values) can ever influence
   * the resolution — identity-sensitive queries (e.g. /my/banks) depend on that.
   */
  def accountableUserId: String = {
    val delegatedHumanUserId = onBehalfOfUser.or(consenter).map(_.userId).filter(_.nonEmpty)
    delegatedHumanUserId.openOr {
      val authenticatedUserId = user.map(_.userId).openOr("")
      val grantingHumanUserId = for {
        callerResourceUser <- code.model.dataAccess.ResourceUser.findByUserId(authenticatedUserId)
        consentId <- net.liftweb.common.Box(callerResourceUser.createdByConsentId)
        consent <- code.consent.Consents.consentProvider.vend.getConsentByConsentId(consentId)
      } yield consent.userId
      grantingHumanUserId.filter(_.nonEmpty).openOr(authenticatedUserId)
    }
  }
  def userPrimaryKey: UserPrimaryKey = user.map(_.userPrimaryKey).openOrThrowException(AuthenticatedUserIsRequired)
  def loggedInUser: User = user.openOrThrowException(AuthenticatedUserIsRequired)
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
object AuthenticationType extends OBPEnumerationWithType[AuthenticationType](ReflectUtils.forType("code.api.util.AuthenticationType")){
  object DirectLogin extends AuthenticationType
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
                            consumerId: Option[String] = None,
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
                            partialFunctionName: String = "",
                            directLoginToken: String = "",
                            xRateLimitLimit : Long = -1,
                            xRateLimitRemaining : Long = -1,
                            xRateLimitReset : Long = -1,
                            paginationOffset : Option[String] = None,
                            paginationLimit : Option[String] = None,
                            consentReferenceId: Option[String] = None,
                            certificateTrust: Option[String] = None,
                            certificateTrustDetail: Option[String] = None
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