package code.api.util

import java.net.URL

import com.auth0.jwt.JWT
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.{MACVerifier, RSASSAVerifier}
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTProcessor}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Props
import java.text.ParseException

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
    * @return the Subject value or None.
    */
  def getSubject(jwtToken: String): Option[String] = {
    val jwtDecoded = JWT.decode(jwtToken)
    jwtDecoded.getSubject() match {
      case null => None
      case value => Some(value)
    }
  }

  /**
    * Helper function which validating bearer JWT access tokens
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




  def main(args: Array[String]): Unit = {
    // val jwtToken = "eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhZG1pbiIsImF6cCI6ImNsaWVudCIsImlzcyI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDgwXC9vcGVuaWQtY29ubmVjdC1zZXJ2ZXItd2ViYXBwXC8iLCJleHAiOjE1MTk1MDMxODAsImlhdCI6MTUxOTQ5OTU4MCwianRpIjoiMmFmZjNhNGMtZjY5Zi00ZWM1LWE2MzEtYWUzMGYyYzQ4MjZiIn0.NwlK2EJKutaybB4YyEhuwb231ZNkD-BEwhScadcWWn8PFftjVyjqjD5_BwSiWHHa_QaESNPdZugAnF4I2DxtXmpir_x2fB2ch888AzXw6CgTT482I16m1jpL-2iSlQk1D-ZW6fJ2Qemdi3x2V13Xgt9PBvk5CsUukJ8SSqTPbSNNER9Nq2dlS-qQfg61TzhPkuuXDlmCQ3b8QHgUf6UnCfee1jRaohHQoCvJJJubmUI3dY0Df1ynTodTTZm4J1TV6Wp6ZhsPkQVmdBAUsE5kIFqADaE179lldh86-97bVHGU5a4aTYRRKoTPDltt1NvY5XJrjLCgZH8AEW7mOHz9mw"
    val jwtToken = "eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJtYXJrby5taWxpYyIsImF6cCI6ImNsaWVudCIsImlzcyI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDgwXC9vcGVuaWQtY29ubmVjdC1zZXJ2ZXItd2ViYXBwXC8iLCJleHAiOjE1MTk3MTc2MDUsImlhdCI6MTUxOTcxNDAwNSwianRpIjoiY2RiNThmNTctZTI2OC00MzZhLWIzMDQtNWE0MWFiYTg0NDFhIn0.XiZKY8A_mXZz6zjCgtXaj0bHI5klmQGnEQcX_b9lBlhfh6IruUwiHuYW0DHXDpKHdKA3Uuqcubj68aT8r5FGyrEGRy4AmzHbzCcwly-MYIElAK4trjSwUJh9VmGwDdr1OFtWC5HrTfsTGfiLQrhNjBGePCy2bGy0pG7pjBNQ3TVOkiAFUVYnCJOiFGLdWcHvEHnPYoYOdvRBLa072qDFbNFiWXqfKcdXdYGXZD5SGMMlA6J6l3NKKiy4t53yE3LjHs5pIclG5OdSV3uB8wGTTACN44CMVUFpWaL6_7_Zlzr-swq_jXYuxHesWGoCWaZKzlbtHsOqpvolgQJlTgdAgA"
    println("Header: " + getHeader(jwtToken))
    println("Payload: " + getPayload(jwtToken))
    println("Subject: " + getSubject(jwtToken))
    println("Signature :" + getSignature(jwtToken))
    println("Verify JWT :" + verifyRsaSignedJwt(jwtToken))
  }


}


