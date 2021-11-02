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
import com.openbankproject.commons.model.{User}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._
import net.liftweb.util.Helpers

import com.openbankproject.commons.ExecutionContext.Implicits.global
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
    msg_sender: String,
    consumer_id: String,
    time_stamp: String,
    caller_request_id: String
  )

}

object DAuth extends RestHelper with MdcLoggable {

  val DAuthValue = "DAuth" // This value is used for ResourceUser.provider and Consumer.description

  def createJwt(payloadAsJsonString: String) : String = {
    val smartContractAddress = getFieldFromPayloadJson(payloadAsJsonString, "smart_contract_address")
    val networkName = getFieldFromPayloadJson(payloadAsJsonString, "network_name")
    val msgSender = getFieldFromPayloadJson(payloadAsJsonString, "msg_sender")
    val consumerId = getFieldFromPayloadJson(payloadAsJsonString, "consumer_id")
    val timeStamp = getFieldFromPayloadJson(payloadAsJsonString, "time_stamp")
    val callerRequestId = getFieldFromPayloadJson(payloadAsJsonString, "caller_request_id")

    val json = JSONFactoryDAuth.PayloadOfJwtJSON(
      smart_contract_address = smartContractAddress,
      network_name = networkName,
      msg_sender = msgSender,
      consumer_id = consumerId,
      time_stamp = timeStamp,
      caller_request_id = callerRequestId
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

  def parseJwt(parameters: Map[String, String]): Box[String] = {
    val jwt = getToken(parameters)
    logger.debug("parseJwt says jwt.toString is: " + jwt.toString)
    logger.debug("parseJwt says: validateJwtToken(jwt) is:" +  validateJwtToken(jwt))
    validateJwtToken(jwt) match {
      case Full(jwtPayload) =>
        logger.debug("parseJwt says: Full: " + jwtPayload.toString)
        Full(compactRender(Extraction.decompose(jwtPayload)))
      case _  =>
        logger.debug("parseJwt says: Not Full(jwtPayload)")
        Failure(ErrorMessages.GatewayLoginJwtTokenIsNotValid)
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
        logger.debug(CertificateUtil.verifywtWithHmacProtection(token).toString)
        CertificateUtil.verifywtWithHmacProtection(token) match {
          case true =>
            logger.debug("validateJwtToken says: jwt is verified: " + token)
            val claim = CertificateUtil.parseJwtWithHmacProtection(token)
            logger.debug("validateJwtToken says: this is claim of verified jwt: " + claim.toString())
            Box(parse(claim.toString).extractOpt[PayloadOfJwtJSON])
          case _ =>
            logger.debug("validateJwtToken says: could not verify jwt")
            Failure(ErrorMessages.GatewayLoginJwtTokenIsNotValid)
        }
    }
  }

  // Check if the request (access token or request token) is valid and return a tuple
  def validator(request: Box[Req]) : (Int, String, Map[String,String]) = {
    // First we try to extract all parameters from a Request
    val parameters: Map[String, String] = getAllParameters(request)
    val emptyMap = Map[String, String]()

    parameters.get("error") match {
      case Some(m) => {
        logger.error("DAuth error message : " + m)
        (400, m, emptyMap)
      }
      case _ => {
        // Are all the necessary DAuth parameters present?
        val missingParams: Set[String] = missingDAuthParameters(parameters)
        missingParams.nonEmpty match {
          case true => {
            val message = ErrorMessages.DAuthMissingParameters + missingParams.mkString(", ")
            logger.error("DAuth error message : " + message)
            (400, message, emptyMap)
          }
          case false => {
            logger.debug("DAuth parameters : " + parameters)
            (200, "", parameters)
          }
        }
      }
    }
  }

  def getOrCreateResourceUser(jwtPayload: String, callContext: Option[CallContext]) : Box[(User, Option[CallContext])] = {
    val username = getFieldFromPayloadJson(jwtPayload, "smart_contract_address")
    logger.debug("login_user_name: " + username)
    for {
      tuple <- 
          Users.users.vend.getUserByProviderId(provider = DAuthValue, idGivenByProvider = username).or { // Find a user
            Users.users.vend.createResourceUser( // Otherwise create a new one
              provider = DAuthValue,
              providerId = Some(username),
              None,
              name = Some(username),
              email = None,
              userId = None,
              createdByUserInvitationId = None,
              company = None,
              lastMarketingAgreementSignedDate = None
            )
          } match {
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
    logger.debug("login_user_name: " + username)
    for {
      tuple <- 
        Users.users.vend.getOrCreateUserByProviderIdFuture(provider = DAuthValue, idGivenByProvider = username, consentId = None, name = None, email = None) map {
          case (Full(u), _) =>
            Full(u, callContext) // Return user
          case (Empty, _) =>
            Failure(ErrorMessages.DAuthCannotGetOrCreateUser)
          case (Failure(msg, t, c), _) =>
            Failure(msg, t, c)
          case _ =>
            Failure(ErrorMessages.DAuthUnknownError)
        }
    } yield {
      tuple
    }
  }

  def getOrCreateConsumer(jwtPayload: String, u: User) : Box[Consumer] = {
    val consumerId = getFieldFromPayloadJson(jwtPayload, "consumer_id")
    val consumerName = getFieldFromPayloadJson(jwtPayload, "msg_sender")
    logger.debug("app_id: " + consumerId)
    logger.debug("app_name: " + consumerName)
    Consumers.consumers.vend.getOrCreateConsumer(
      consumerId=Some(consumerId),
      Some(Helpers.randomString(40).toLowerCase),
      Some(Helpers.randomString(40).toLowerCase),
      None,
      None,
      None,
      None,
      Some(true),
      name = Some(consumerName),
      appType = None,
      description = Some(DAuthValue),
      developerEmail = None,
      redirectURL = None,
      createdByUserId = Some(u.userId)
    )
  }

  // Return a Map containing the DAuth parameter : token -> value
  def getAllParameters(request: Box[Req]): Map[String, String] = {
    def toMap(parametersList: String) = {
      //transform the string "DAuth token="value""
      //to a tuple (DAuth_parameter,Decoded(value))
      def dynamicListExtract(input: String) = {
        val DAuthPossibleParameters =
          List(
            "token"
          )
        if (input contains "=") {
          val split = input.split("=", 2)
          val parameterValue = split(1).replace("\"", "")
          //add only OAuth parameters and not empty
          if (DAuthPossibleParameters.contains(split(0)) && !parameterValue.isEmpty)
            Some(split(0), parameterValue) // return key , value
          else
            None
        }
        else
          None
      }
      // We delete the "DAuth" prefix and all the white spaces that may exist in the string
      val cleanedParameterList = parametersList.stripPrefix("DAuth").replaceAll("\\s", "")
      val params = Map(cleanedParameterList.split(",").flatMap(dynamicListExtract _): _*)
      params
    }

    request match {
      case Full(a) => a.header("Authorization") match {
        case Full(header) => {
          if (header.contains("DAuth"))
            toMap(header)
          else
            Map("error" -> "Missing DAuth in header!")
        }
        case _ => Map("error" -> "Missing Authorization header!")
      }
      case _ => Map("error" -> "Request is incorrect!")
    }
  }

  // Returns the missing parameters
  def missingDAuthParameters(parameters: Map[String, String]): Set[String] = {
    ("token" :: List()).toSet diff parameters.keySet
  }

  private def getToken(params: Map[String, String]): String = {
    logger.debug("getToken params are: " + params.toString())
    val token = params.getOrElse("token", "")
    logger.debug("getToken wants to return token: " + token)
    token
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
    val (httpCode, message, parameters) = DAuth.validator(S.request)
    httpCode match {
      case 200 =>
        val payload = DAuth.parseJwt(parameters)
        payload match {
          case Full(payload) =>
            val username = getFieldFromPayloadJson(payload, "smart_contract_address")
            logger.debug("username: " + username)
            Users.users.vend.getUserByProviderId(provider = DAuthValue, idGivenByProvider = username)
          case _ =>
            None
        }
      case _  =>
        None
    }
  }
}
