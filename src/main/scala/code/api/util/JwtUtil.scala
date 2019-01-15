package code.api.util

import java.net.URL
import java.text.ParseException

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Claim
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.{MACVerifier, RSASSAVerifier}
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTProcessor}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
import net.liftweb.common.{Box, Empty, Failure, Full}

object JwtUtil {

  /**
    * Getter for the Header contained in the JWT as a Base64 encoded String.
    * This represents the first part of the token.
    *
    * @return the Header of the JWT.
    */
  def getHeader(jwtToken: String) = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getHeader()
  }

  /**
    * Getter for the Payload contained in the JWT as a Base64 encoded String.
    * This represents the second part of the token.
    *
    * @return the Payload of the JWT.
    */
  def getPayload(jwtToken: String) = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getPayload()
  }

  /**
    * Getter for the Signature contained in the JWT as a Base64 encoded String.
    * This represents the third part of the token.
    *
    * @return the Signature of the JWT.
    */
  def getSignature(jwtToken: String) = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getSignature()
  }

  /**
    * Helper function which verify JSON Web Token (JWT) with RSA signature
    *
    * @return True or False
    */
  def verifyRsaSignedJwt(jwtToken: String) = {
    val signedJWT = SignedJWT.parse(jwtToken)
    val verifier = new RSASSAVerifier(CertificateUtil.publicKey)
    signedJWT.verify(verifier)
  }

  /**
    * Helper function which verify JSON Web Token (JWT) with HMAC protection
    *
    * @return True or False
    */
  def verifyHmacSignedJwt(jwtToken: String): Boolean = {
    val signedJWT = SignedJWT.parse(jwtToken)
    val sharedSecret = APIUtil.getPropsValue("oauth2.token_secret", "")
    val verifier = new MACVerifier(sharedSecret)
    signedJWT.verify(verifier)
  }

  /**
    * Get the value of the "sub" claim, or None if it's not available.
    *
    * @return the Subject's value or None.
    */
  def getSubject(jwtToken: String): Option[String] = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getSubject() match {
      case null => None
      case value => Some(value)
    }
  }
  /**
    * The Issuer Identifier for the Issuer of the response. 
    * Get the value of the "iss" claim, or None if it's not available.
    *
    * @return the Issuer's value or None.
    */
  def getIssuer(jwtToken: String): Option[String] = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getIssuer() match {
      case null => None
      case value => Some(value)
    }
  }
  /**
    * The Audience Identifier for the Issuer of the response. 
    * Get the value of the "aud" claim.
    *
    * @return the Issuer's value. In case if it's not available the value is empty list.
    */
  def getAudience(jwtToken: String): List[String] = {
    import scala.collection.JavaConverters._
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getAudience() match {
      case null => Nil
      case value => value.asScala.toList
    }
  }

  /**
    * This fuction gets an arbitrary claim
    * @param name The name of the claim we want to get
    * @param jwtToken JSON Web Token (JWT) as a String value
    * @return The claim we requested
    */
  def getClaim(name: String, jwtToken: String): Claim = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getClaim(name)
  }

  /**
    * This function validates Access Token
    * @param accessToken The access token to validate, typically submitted with a HTTP header like
    *                    Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6InMxIn0.eyJzY3A...
    * @param remoteJWKSetUrl The URL of OAuth 2.0 server's JWK set, published at a well-known URL
    * @return The boxed token claims set or Failure
    */

  def validateAccessToken(accessToken: String, remoteJWKSetUrl: String): Box[JWTClaimsSet] = {
    // Set up a JWT processor to parse the tokens and then check their signature
    // and validity time window (bounded by the "iat", "nbf" and "exp" claims)
    val jwtProcessor = new DefaultJWTProcessor[SecurityContext]

    // The public RSA keys to validate the signatures will be sourced from the
    // OAuth 2.0 server's JWK set, published at a well-known URL. The RemoteJWKSet
    // object caches the retrieved keys to speed up subsequent look-ups and can
    // also gracefully handle key-rollover
    val keySource: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(remoteJWKSetUrl))

    // The expected JWS algorithm of the access tokens (agreed out-of-band)
    val expectedJWSAlg = JWSAlgorithm.RS256

    // Configure the JWT processor with a key selector to feed matching public
    // RSA keys sourced from the JWK set URL
    val keySelector = new JWSVerificationKeySelector[SecurityContext](expectedJWSAlg, keySource)
    jwtProcessor.setJWSKeySelector(keySelector)

    try {
      // Process the token
      val maybeCtx: Option[SecurityContext] = None
      val ctx: SecurityContext = maybeCtx.orNull // optional context parameter, not required here
      val claimsSet = jwtProcessor.process(accessToken, ctx)
      Full(claimsSet)
    } catch {
      case e: BadJWTException => Failure(ErrorMessages.Oauth2BadJWTException + e.getMessage, Full(e), Empty)
      case e: ParseException  => Failure(ErrorMessages.Oauth2ParseException + e.getMessage, Full(e), Empty)
      case e: Exception       => Failure(e.getMessage, Full(e), Empty)
    }
  }

  /**
    * This function validates ID Token
    * @param idToken The access token to validate, typically submitted with a HTTP header like
    *                Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6InMxIn0.eyJzY3A...
    * @param remoteJWKSetUrl The URL of OAuth 2.0 server's JWK set, published at a well-known URL
    * @return The boxed token claims set or Failure
    */
  def validateIdToken(idToken: String, remoteJWKSetUrl: String): Box[IDTokenClaimsSet] = {
    import java.net._

    import com.nimbusds.jose._
    import com.nimbusds.oauth2.sdk.id._
    import com.nimbusds.openid.connect.sdk.validators._
    
    val resourceRetriever = new DefaultResourceRetriever(1000, 1000, 50 * 1024)

    // The required parameters
    val iss: Issuer = new Issuer(getIssuer(idToken).getOrElse(""))
    val azp = getClaim("azp", idToken).asString()
    val clientID: ClientID = new ClientID(azp)
    val jwsAlg: JWSAlgorithm = JWSAlgorithm.RS256
    //val jwkSetURL: URL = new URL("https://www.googleapis.com/oauth2/v3/certs")
    val jwkSetURL: URL = new URL(remoteJWKSetUrl)

    // Create validator for signed ID tokens
    val validator: IDTokenValidator = new IDTokenValidator(iss, clientID, jwsAlg, jwkSetURL, resourceRetriever)

    import com.nimbusds.jose.JOSEException
    import com.nimbusds.jose.proc.BadJOSEException
    import com.nimbusds.jwt.{JWT, JWTParser}
    import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
    
    // Parse the ID token// Parse the ID token
    val idTokenAsJWT: JWT = JWTParser.parse(idToken)

    // Set the expected nonce, leave null if none
    val expectedNonce = null // new Nonce("xyz...") or null
    
    try {
      val claims: IDTokenClaimsSet = validator.validate(idTokenAsJWT, expectedNonce)
      Full(claims)
    } catch {
      case e: BadJOSEException =>
        // Invalid signature or claims (iss, aud, exp...)
        Failure(ErrorMessages.Oauth2BadJOSEException + e.getMessage, Full(e), Empty)
      case e: JOSEException =>
        // Internal processing exception
        Failure(ErrorMessages.Oauth2JOSEException + e.getMessage, Full(e), Empty)
    }
  }




  def main(args: Array[String]): Unit = {
    val jwtToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhhYWQ2NmJkZWZjMWI0M2Q4ZGIyN2U2NWUyZTJlZjMwMTg3OWQzZTgiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJhdF9oYXNoIjoiWGlpckZ1cnJ2X0ZxN3RHd25rLWt1QSIsIm5hbWUiOiJNYXJrbyBNaWxpxIciLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDUuZ29vZ2xldXNlcmNvbnRlbnQuY29tLy1YZDQ0aG5KNlREby9BQUFBQUFBQUFBSS9BQUFBQUFBQUFBQS9BS3hyd2NhZHd6aG00TjR0V2s1RThBdnhpLVpLNmtzNHFnL3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJNYXJrbyIsImZhbWlseV9uYW1lIjoiTWlsacSHIiwibG9jYWxlIjoiZW4iLCJpYXQiOjE1NDczMTE3NjAsImV4cCI6MTU0NzMxNTM2MH0.UyOmM0rsO0-G_ibDH3DFogS94GcsNd9GtYVw7j3vSMjO1rZdIraV-N2HUtQN3yHopwdf35A2FEJaag6X8dbvEkJC7_GAynyLIpodoaHNtaLbww6XQSYuQYyF27aPMpROoGZUYkMpB_82LF3PbD4ecDPC2IA5oSyDF4Eya4yn-MzxYmXS7usVWvanREg8iNQSxpu7zZqj4UwhvSIv7wH0vskr_M-PnefQzNTrdUx74i-v9lVqC4E_bF5jWeDGO8k5dqWqg55QuZdyJdSh89KNiIjJXGZDWUBzGfsbetWRnObIgX264fuOW4SpRglUc8fzv41Sc7SSqjqRAFm05t60kg"
    println("Header: " + getHeader(jwtToken))
    println("Payload: " + getPayload(jwtToken))
    println("Subject: " + getSubject(jwtToken))
    println("Signature: " + getSignature(jwtToken))
    println("Verify JWT: " + verifyRsaSignedJwt(jwtToken))

    val idToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhhYWQ2NmJkZWZjMWI0M2Q4ZGIyN2U2NWUyZTJlZjMwMTg3OWQzZTgiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJhdF9oYXNoIjoiWVVuME9EYlVsUU4xQ1VUOThSVmE3USIsIm5hbWUiOiJNYXJrbyBNaWxpxIciLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDUuZ29vZ2xldXNlcmNvbnRlbnQuY29tLy1YZDQ0aG5KNlREby9BQUFBQUFBQUFBSS9BQUFBQUFBQUFBQS9BS3hyd2NhZHd6aG00TjR0V2s1RThBdnhpLVpLNmtzNHFnL3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJNYXJrbyIsImZhbWlseV9uYW1lIjoiTWlsacSHIiwibG9jYWxlIjoiZW4iLCJpYXQiOjE1NDc0NTQ3MTIsImV4cCI6MTU0NzQ1ODMxMn0.GImeYoPuOgitxpS59XvEd93nxRYKbWl9vHvuMIXYJWFQ5bF_LcnX_PdXRA3w-cBrAZZ3FCAtY0nrE8f7pb6-oQnqpJXYl6PwCe_oZV5rUzMnWUyWauk752_Et-hSxCypAyf7zvW3xcunQUdeKLt_b5dIIs80d8vDpnSlR4SkXx9iduOQ84ktvHMgwIb7ymws6LenstJH864TMvmUNokFgVGOcVeJRKKiGmcoIhIYdh9j1z4J0_gCPs-UsJhJTdmVQgtNQFqMUt8KPEYvFd0gI3Cdvd9gQM5cq9OSUs3D9sI0DLEhBCoEHanBinUrII8B7JE2HkPTEMdM2ZN-2Ecq5A"
    println("validateIdToken: " + validateIdToken(idToken = idToken, remoteJWKSetUrl = "https://www.googleapis.com/oauth2/v3/certs").map("Logged in user: " + _.getSubject))
  }


}


