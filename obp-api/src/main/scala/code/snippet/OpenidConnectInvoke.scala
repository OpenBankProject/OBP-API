package code.snippet

import java.net.URI

import code.api.OpenIdConnectConfig
import com.nimbusds.oauth2.sdk.id.{ClientID, State}
import com.nimbusds.oauth2.sdk.{ResponseType, Scope}
import com.nimbusds.openid.connect.sdk.{AuthenticationRequest, Nonce}
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

    // Compose the request (in code flow)
    val req =
      new AuthenticationRequest(
        new URI(OpenIdConnectConfig.get(identityProvider).authorization_endpoint),
        new ResponseType("code"),
        Scope.parse("openid email profile"),
        clientID,
        callback,
        state,
        nonce
      )
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
