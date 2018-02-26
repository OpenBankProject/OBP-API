package code.api.util

import com.auth0.jwt.JWT

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


  def main(args: Array[String]): Unit = {
    val jwtToken = "eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhZG1pbiIsImF6cCI6ImNsaWVudCIsImlzcyI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDgwXC9vcGVuaWQtY29ubmVjdC1zZXJ2ZXItd2ViYXBwXC8iLCJleHAiOjE1MTk1MDMxODAsImlhdCI6MTUxOTQ5OTU4MCwianRpIjoiMmFmZjNhNGMtZjY5Zi00ZWM1LWE2MzEtYWUzMGYyYzQ4MjZiIn0.NwlK2EJKutaybB4YyEhuwb231ZNkD-BEwhScadcWWn8PFftjVyjqjD5_BwSiWHHa_QaESNPdZugAnF4I2DxtXmpir_x2fB2ch888AzXw6CgTT482I16m1jpL-2iSlQk1D-ZW6fJ2Qemdi3x2V13Xgt9PBvk5CsUukJ8SSqTPbSNNER9Nq2dlS-qQfg61TzhPkuuXDlmCQ3b8QHgUf6UnCfee1jRaohHQoCvJJJubmUI3dY0Df1ynTodTTZm4J1TV6Wp6ZhsPkQVmdBAUsE5kIFqADaE179lldh86-97bVHGU5a4aTYRRKoTPDltt1NvY5XJrjLCgZH8AEW7mOHz9mw"
    println("Header: " + getHeader(jwtToken))
    println("Payload: " + getPayload(jwtToken))
    println("Subject: " + getSubject(jwtToken))
    println("Signature :" + getSignature(jwtToken))
  }

}


