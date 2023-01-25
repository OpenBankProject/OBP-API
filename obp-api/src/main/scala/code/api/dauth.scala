/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

  */
package code.api

import code.api.JSONFactoryDAuth.PayloadOfJwtJSON
import code.api.util._
import code.consumer.Consumers
import code.model.{Consumer, UserX}
import code.users.Users
import code.util.Helper.MdcLoggable
import com.nimbusds.jwt.JWTClaimsSet
import com.openbankproject.commons.model.User
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.http.provider.HTTPParam

import scala.collection.immutable.List
import scala.concurrent.Future

/**
  * This object provides the API calls necessary to
  * authenticate users using JSON Web Tokens (http://jwt.io).
  */


object JSONFactoryDAuth {
  //Never update these values inside the case class
  case class PayloadOfJwtJSON(
    smart_contract_address: String,
    network_name: String,
    consumer_key: String,  
    timestamp: Option[String],
    msg_sender: Option[String],
    request_id: Option[String]
  )

}

object DAuth extends RestHelper with MdcLoggable {


  def createJwt(payloadAsJsonString: String) : String = {
    val smartContractAddress = getFieldFromPayloadJson(payloadAsJsonString, "smart_contract_address")
    val networkName = getFieldFromPayloadJson(payloadAsJsonString, "network_name")
    val msgSender = getFieldFromPayloadJson(payloadAsJsonString, "msg_sender")
    val consumerKey = getFieldFromPayloadJson(payloadAsJsonString, "consumer_key")
    val timeStamp = getFieldFromPayloadJson(payloadAsJsonString, "timestamp")
    val requestId = getFieldFromPayloadJson(payloadAsJsonString, "request_id")

    val json = JSONFactoryDAuth.PayloadOfJwtJSON(
      smart_contract_address = smartContractAddress,
      network_name = networkName,
      consumer_key = consumerKey,
      msg_sender = Some(msgSender),
      timestamp = Some(timeStamp),
      request_id = Some(requestId)
    )
    val jwtPayloadAsJson = compactRender(Extraction.decompose(json))
    val jwtClaims: JWTClaimsSet = JWTClaimsSet.parse(jwtPayloadAsJson)

    APIUtil.getPropsAsBoolValue("jwt.use.ssl", false) match {
      case true =>
        CertificateUtil.encryptJwtWithRsa(jwtClaims)
      case false =>
        CertificateUtil.jwtWithHmacProtection(jwtClaims)
    }
  }

  def parseJwt(jwt:String): Box[String] = {
    logger.debug("parseJwt says jwt.toString is: " + jwt)
    logger.debug("parseJwt says: validateJwtToken(jwt) is:" +  validateJwtToken(jwt))
    validateJwtToken(jwt) match {
      case Full(jwtPayload) =>
        logger.debug("parseJwt says: Full: " + jwtPayload.toString)
        Full(compactRender(Extraction.decompose(jwtPayload)))
      case _  =>
        logger.debug("parseJwt says: Not Full(jwtPayload)")
        Failure(ErrorMessages.DAuthJwtTokenIsNotValid)
    }
  }

  def validateJwtToken(token: String): Box[PayloadOfJwtJSON] = {
    APIUtil.getPropsAsBoolValue("jwt.use.ssl", false) match {
      case true =>
        logger.debug("validateJwtToken says: verifying jwt token with RSA: " + token)
        val claim = CertificateUtil.decryptJwtWithRsa(token)
        Box(parse(claim.toString).extractOpt[PayloadOfJwtJSON])
      case false =>
        logger.debug("validateJwtToken says: verifying jwt token with HmacProtection: " + token)
        logger.debug(JwtUtil.validateJwtWithRsaKey(token).toString)
        JwtUtil.validateJwtWithRsaKey(token) match {
          case true =>
            logger.debug("validateJwtToken says: jwt is verified: " + token)
            val claim = CertificateUtil.parseJwtWithHmacProtection(token)
            logger.debug("validateJwtToken says: this is claim of verified jwt: " + claim.toString())
            Box(parse(claim.toString).extractOpt[PayloadOfJwtJSON])
          case _ =>
            logger.debug("validateJwtToken says: could not verify jwt")
            Failure(ErrorMessages.DAuthJwtTokenIsNotValid)
        }
    }
  }

  // Check if the request (access token or request token) is valid and return a tuple
  def getDAuthToken(requestHeaders: List[HTTPParam]) : Option[List[String]]  = {
    requestHeaders.find(_.name==APIUtil.DAuthHeaderKey).map(_.values)
  }

  def getOrCreateResourceUser(jwtPayload: String, callContext: Option[CallContext]) : Box[(User, Option[CallContext])] = {
    val userName = getFieldFromPayloadJson(jwtPayload, "smart_contract_address")
    val provider = "dauth."+getFieldFromPayloadJson(jwtPayload, "network_name")
    logger.debug("login_user_name: " + userName)
    for {
      tuple <-
        UserX.getOrCreateDauthResourceUser(provider, userName) match {
            case Full(u) =>
              Full((u,callContext)) // Return user
            case Empty =>
              Failure(ErrorMessages.DAuthCannotGetOrCreateUser)
            case Failure(msg, t, c) =>
              Failure(msg, t, c)
            case _ =>
              Failure(ErrorMessages.DAuthUnknownError)
          }
    } yield {
      tuple
    }
  }
  def getOrCreateResourceUserFuture(jwtPayload: String, callContext: Option[CallContext]) : Future[Box[(User, Option[CallContext])]] = {
    val username = getFieldFromPayloadJson(jwtPayload, "smart_contract_address")
    val provider = "dauth."+ getFieldFromPayloadJson(jwtPayload, "network_name")
    logger.debug("login_user_name: " + username)
    
    for {
      tuple <- Future { UserX.getOrCreateDauthResourceUser(provider, username)} map {
          case (Full(u)) =>
            Full(u, callContext) // Return user
          case (Empty) =>
            Failure(ErrorMessages.DAuthCannotGetOrCreateUser)
          case (Failure(msg, t, c)) =>
            Failure(msg, t, c)
          case _ =>
            Failure(ErrorMessages.DAuthUnknownError)
        }
    } yield {
      tuple
    }
  }

  def getConsumerByConsumerKey(jwtPayload: String) : Box[Consumer] = {
    val consumeyKey = getFieldFromPayloadJson(jwtPayload, "consumer_key")
    Consumers.consumers.vend.getConsumerByConsumerKey(consumeyKey)
  }

  private def getFieldFromPayloadJson(payloadAsJsonString: String, fieldName: String) = {
    val jwtJson = parse(payloadAsJsonString) // Transform Json string to JsonAST
    val v = jwtJson.\(fieldName)
    v match {
      case JNothing =>
        ""
      case _ =>
        compactRender(v).replace("\"", "")
    }
  }
  // Try to find errorCode in Json string received from South side and extract to list
  // Return list of error codes values
  def getErrors(message: String) : List[String] = {
    val json = parse(message) removeField {
      case JField("backendMessages", _) => true
      case _          => false
    }
    val listOfValues = for {
      JArray(objects) <- json
      JObject(obj) <- objects
      JField("errorCode", JString(fieldName)) <- obj
    } yield fieldName
    listOfValues
  }

  def getUser : Box[User] = {
    val token = S.getRequestHeader(APIUtil.DAuthHeaderKey)
    val payload = token.map(DAuth.parseJwt).flatten
    payload match {
      case Full(payload) =>
        val username = getFieldFromPayloadJson(payload, "smart_contract_address")
        val provider = getFieldFromPayloadJson(payload, "network_name")
        val providerHardCodePrefixDauth = "dauth."+provider
        logger.debug("username: " + username)
        Users.users.vend.getUserByProviderId(provider = providerHardCodePrefixDauth, idGivenByProvider = username)
      case _ =>
        None
    }
  }
}
