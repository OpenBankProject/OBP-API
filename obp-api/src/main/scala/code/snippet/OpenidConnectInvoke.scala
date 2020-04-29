package code.snippet

import java.net.URI

import code.api.OpenIdConnectConfig
import com.nimbusds.oauth2.sdk.{ResponseType, Scope}
import com.nimbusds.oauth2.sdk.id.{ClientID, State}
import com.nimbusds.openid.connect.sdk.{AuthenticationRequest, Nonce}
import net.liftweb.util.Helpers._
import net.liftweb.common.Loggable
import net.liftweb.http.{S, SHtml}
import net.liftweb.http.js.{JsCmd, JsCmds}
import net.liftweb.http.provider.HTTPCookie
import net.liftweb.util.CssSel

object OpenidConnectInvoke extends Loggable {
  def callback(): JsCmd = {
    logger.info("The button was pressed")

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
    val jSessionId1: List[HTTPCookie] = S.request.map(_.cookies.filter(_.name == "JSESSIONID")).openOrThrowException("There is no JSESSIONID cookie")
    org.scalameta.logger.elem(jSessionId1)
    val redirectTo = req.toHTTPRequest.getURL() + "?" + req.toHTTPRequest.getQuery()
    S.redirectTo(redirectTo)
  }

  def linkButton: CssSel = "a [onclick]" #> SHtml.ajaxInvoke(callback)
}
