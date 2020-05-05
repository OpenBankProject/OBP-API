package code.snippet

import java.net.URI

import code.api.OpenIdConnectConfig
import com.nimbusds.oauth2.sdk.id.{ClientID, State}
import com.nimbusds.oauth2.sdk.{ResponseType, Scope}
import com.nimbusds.openid.connect.sdk.{AuthenticationRequest, Nonce}
import net.liftweb.common.{Box, Empty, Full, Loggable}
import net.liftweb.http.js.JsCmd
import net.liftweb.http.{S, SHtml, SessionVar}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

object OpenIDConnectSessionState extends SessionVar[Box[State]](Empty)

object OpenidConnectInvoke extends Loggable {
  
  def callback(): JsCmd = {
    // The client id
    val clientID: ClientID = new ClientID(OpenIdConnectConfig.get().client_id)

    // The client callback URL
    val callback = new URI(OpenIdConnectConfig.get().callback_url)

    // Generate random state string for pairing the response to the request
    val state = new State()

    // Generate nonce
    val nonce = new Nonce()

    // Compose the request (in code flow)
    val req =
      new AuthenticationRequest(
        new URI(OpenIdConnectConfig.get().authorization_endpoint),
        new ResponseType("code"),
        Scope.parse("openid email profile"),
        clientID,
        callback,
        state,
        nonce
      )
    OpenIDConnectSessionState.set(Full(state))
    val accessType = if (OpenIdConnectConfig.get().access_type_offline) "&access_type=offline" else ""
    val redirectTo = req.toHTTPRequest.getURL() + "?" + req.toHTTPRequest.getQuery() + accessType
    S.redirectTo(redirectTo)
  }

  def linkButton: CssSel = "a [onclick]" #> SHtml.ajaxInvoke(callback)
}
