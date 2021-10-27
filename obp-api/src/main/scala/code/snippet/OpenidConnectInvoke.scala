package code.snippet

import java.net.URI

import code.api.OpenIdConnectConfig
import code.api.util.APIUtil
import com.nimbusds.jwt.JWT
import com.nimbusds.oauth2.sdk.id.{ClientID, State}
import com.nimbusds.oauth2.sdk.pkce.{CodeChallenge, CodeChallengeMethod}
import com.nimbusds.oauth2.sdk.{ResponseMode, ResponseType, Scope}
import com.nimbusds.openid.connect.sdk.{AuthenticationRequest, Display, Nonce, OIDCClaimsRequest, OIDCScopeValue, Prompt}
import net.liftweb.common.{Box, Empty, Full, Loggable}
import net.liftweb.http.js.{JsCmd, JsCmds}
import net.liftweb.http.{S, SHtml, SessionVar}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

object OpenIDConnectSessionState extends SessionVar[Box[State]](Empty)

object OpenidConnectInvoke extends Loggable {
  
  private def callbackFirstProvider(): JsCmd = {
    callbackCommonCode(1)
  }  
  private def callbackSecondProvider(): JsCmd = {
    callbackCommonCode(2)
  }

  private def callbackCommonCode(identityProvider: Int) = {
    // The client id
    val clientID: ClientID = new ClientID(OpenIdConnectConfig.get(identityProvider).client_id)

    // The client callback URL
    val callback = new URI(OpenIdConnectConfig.get(identityProvider).callback_url)

    // Generate random state string for pairing the response to the request
    val state = new State()

    // Generate nonce
    val nonce = new Nonce()

    val responseMode = APIUtil.getPropsValue("openid_connect.response_mode", "form_post") match {
      case "query" => ResponseMode.QUERY
      case "fragment" => ResponseMode.FRAGMENT
      case "form_post" => ResponseMode.FORM_POST
      case "query.jwt" => ResponseMode.QUERY_JWT
      case "fragment.jwt" => ResponseMode.FRAGMENT_JWT
      case "form_post.jwt" => ResponseMode.FORM_POST_JWT
      case "jwt" => ResponseMode.JWT
      case _ =>  ResponseMode.FORM_POST
    }
    val responseType = APIUtil.getPropsValue("openid_connect.response_type", "code") match {
      case "code" => new ResponseType("code")
      case "id_token" => new ResponseType("id_token")
      case "code id_token" => new ResponseType("code", "id_token")
      case _ =>  new ResponseType("code")
    }
    val scope = APIUtil.getPropsValue("openid_connect.scope", "openid email profile") match {
      case "openid email profile" =>
        val scope: Scope  = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        scope.add(OIDCScopeValue.PROFILE);
        scope
      case "openid email" =>
        val scope: Scope  = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        scope
      case "openid" =>
        val scope: Scope  = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope
      case _ =>
        val scope: Scope  = new Scope();
        scope.add(OIDCScopeValue.OPENID);
        scope.add(OIDCScopeValue.EMAIL);
        scope.add(OIDCScopeValue.PROFILE);
        scope
    }

    // Compose the request (in code flow)
    val req = new AuthenticationRequest(
      new URI(OpenIdConnectConfig.get(identityProvider).authorization_endpoint),
      responseType,
      responseMode,
      scope,
      clientID,
      callback,
      state,
      nonce,
      null, null, -1, null, null,
      null, null, null, null.asInstanceOf[OIDCClaimsRequest], null,
      null, null,
      null, null,
      null, false, null)
    OpenIDConnectSessionState.set(Full(state))
    val accessType = if (OpenIdConnectConfig.get(identityProvider).access_type_offline) "&access_type=offline" else ""
    val redirectTo = req.toHTTPRequest.getURL() + "?" + req.toHTTPRequest.getQuery() + accessType
    S.redirectTo(redirectTo)
  }
  
  private def identityProviderIsNotSet(identityProvider: Int) = {
    OpenIdConnectConfig.get(identityProvider).client_id.isEmpty ||
    OpenIdConnectConfig.get(identityProvider).client_secret.isEmpty ||
    OpenIdConnectConfig.get(identityProvider).authorization_endpoint.isEmpty ||
    OpenIdConnectConfig.get(identityProvider).token_endpoint.isEmpty ||
    OpenIdConnectConfig.get(identityProvider).callback_url.isEmpty ||
    OpenIdConnectConfig.get(identityProvider).jwks_uri.isEmpty
  }
  
  private def openIDConnectDisabled() = JsCmds.Alert("OpenID Connect is not enabled at this instance.")
  private def openIDConnect1IsNotSet() = JsCmds.Alert(s"OpenID Connect 1 is not set up at this instance.")
  private def openIDConnect2IsNotSet() = JsCmds.Alert(s"OpenID Connect 2 is not set up at this instance.")
  
  def linkButtonFirstProvider: CssSel = {
    if (OpenIdConnectConfig.openIDConnectEnabled)
      if(identityProviderIsNotSet(1))
        "a [onclick]" #> SHtml.ajaxInvoke(openIDConnect1IsNotSet)
      else
        "a [onclick]" #> SHtml.ajaxInvoke(callbackFirstProvider)
    else
      "a [onclick]" #> SHtml.ajaxInvoke(openIDConnectDisabled)
  }  
  def linkButtonSecondProvider: CssSel = {
    if (OpenIdConnectConfig.openIDConnectEnabled)
      if(identityProviderIsNotSet(2))
        "a [onclick]" #> SHtml.ajaxInvoke(openIDConnect2IsNotSet)
      else
        "a [onclick]" #> SHtml.ajaxInvoke(callbackSecondProvider)
    else
      "a [onclick]" #> SHtml.ajaxInvoke(openIDConnectDisabled)
  }
    
    
}
