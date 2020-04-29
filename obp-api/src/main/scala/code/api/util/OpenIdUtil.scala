package code.api.util

import java.net.{URI, URL}

import com.nimbusds.oauth2.sdk.Response
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.openid.connect.sdk.Nonce

object OpenIdUtil {
  def main(args: Array[String]): Unit = {
    import com.nimbusds.oauth2.sdk.AuthorizationCode
    import com.nimbusds.oauth2.sdk.ResponseType
    import com.nimbusds.oauth2.sdk.Scope
    import com.nimbusds.oauth2.sdk.id.ClientID
    import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse
    import com.nimbusds.openid.connect.sdk.AuthenticationRequest
    import com.nimbusds.openid.connect.sdk.AuthenticationResponse
    import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser
    import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse
    // The client identifier provisioned by the server// The client identifier provisioned by the server

    val clientID: ClientID = new ClientID("883773244832-s4hi72j0rble0iiivq1gn09k7vvptdci.apps.googleusercontent.com")

    // The client callback URL
    val callback = new URI("http://localhost:8082")

    // Generate random state string for pairing the response to the request
    val state = new State()

    // Generate nonce
    val nonce = new Nonce()

    // Compose the request (in code flow)
    val req = new AuthenticationRequest(
      new URI("https://accounts.google.com/o/oauth2/v2/auth"),
      new ResponseType("code"),
      Scope.parse("openid email profile"),
      clientID,
      callback,
      state,
      nonce)
    org.scalameta.logger.elem(req.toHTTPRequest.getURL())
    org.scalameta.logger.elem(req.toHTTPRequest.getQuery())
    val httpResponse = req.toHTTPRequest.send()
    org.scalameta.logger.elem(httpResponse.getStatusCode())
    //org.scalameta.logger.elem(httpResponse.getStatusMessage())
    org.scalameta.logger.elem(httpResponse.getContentType())
    //println(httpResponse.getContent())


    //val autorizationURT = Response.set(buildAuthorizationCodeRequest(state, nonce, clientID, redirectUri)).build
    
    val response = AuthenticationResponseParser.parse(httpResponse)

    if (response.isInstanceOf[AuthenticationErrorResponse]) {
      // process error
    }

    val successResponse = response.asInstanceOf[AuthenticationSuccessResponse]
    
    org.scalameta.logger.elem(successResponse)

    // Retrieve the authorisation code
    val code = successResponse.getAuthorizationCode

    // Don't forget to check the state
    assert(successResponse.getState.equals(state))
  }
}
