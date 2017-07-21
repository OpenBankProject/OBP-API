/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

  */
package code.api

import authentikat.jwt.{JsonWebToken, JwtClaimsSet, JwtHeader}
import code.api.util.APIUtil.setGatewayResponseHeader
import code.api.util.ErrorMessages
import code.consumer.Consumers
import code.model.{Consumer, User}
import code.users.Users
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._
import net.liftweb.util.Helpers._
import net.liftweb.util.{Helpers, Props}

/**
* This object provides the API calls necessary to
* authenticate users using JSON Web Tokens (http://jwt.io).
*/


object JSONFactoryGateway {
  case class TokenJSON(
                        username: String,
                        is_first: Option[Boolean],
                        CBS_auth_token: Option[String],
                        timestamp: String,
                        consumer_id: String,
                        consumer_name: String
                      )
}

object GatewayLogin extends RestHelper with MdcLoggable {

  val gateway = "Gateway" // This value is used for ResourceUser.provider and Consumer.description

  def createJwt(payloadAsJsonString: String, cbsAuthToken: Option[String]) : String = {
    val username = getFieldFromPayloadJson(payloadAsJsonString, "username")
    val consumerId = getFieldFromPayloadJson(payloadAsJsonString, "consumer_id")
    val consumerName = getFieldFromPayloadJson(payloadAsJsonString, "consumer_name")
    val isFirst = getFieldFromPayloadJson(payloadAsJsonString, "is_first")
    val timestamp = getFieldFromPayloadJson(payloadAsJsonString, "timestamp")
    val cbsToken = cbsAuthToken match {
      case Some(v) => v
      case None => getFieldFromPayloadJson(payloadAsJsonString, "CBS_auth_token")
    }
    val json = JSONFactoryGateway.TokenJSON(
      username = username,
      is_first = None,
      CBS_auth_token = Some(cbsToken),
      timestamp = timestamp,
      consumer_id = consumerId,
      consumer_name = consumerName
    )
    val header = JwtHeader("HS256")
    val claimsSet = JwtClaimsSet(compact(render(Extraction.decompose(json))))
    val secretKey = Props.get("gateway.token_secret", "Cannot get the secret")
    val jwt: String = JsonWebToken(header, claimsSet, secretKey)
    jwt
  }

  def parseJwt(parameters: Map[String, String]): String = {
    val jwt = getToken(parameters)
    validateJwtToken(jwt) match {
      case true => {
        jwt match {
          case JsonWebToken(header, payload, signature) =>
            logger.debug("payload" + payload)
            payload.asJsonString
          case _ => "Cannot extract token!"
        }
      }
      case false  => {
        "JWT token is not valid!"
      }
    }
  }

  def validateJwtToken(jwt: String): Boolean = {
    val secretKey = Props.get("gateway.token_secret", "Cannot get the secret")
    val isValid = JsonWebToken.validate(jwt, secretKey)
    isValid
  }

  // Check if the request (access token or request token) is valid and return a tuple
  def validator(request: Box[Req]) : (Int, String, Map[String,String]) = {
    // First we try to extract all parameters from a Request
    val parameters: Map[String, String] = getAllParameters(request)
    val emptyMap = Map[String, String]()

    parameters.get("error") match {
      case Some(m) => {
        logger.error("GatewayLogin error message : " + m)
        (400, m, emptyMap)
      }
      case _ => {
        // Are all the necessary GatewayLogin parameters present?
        val missingParams: Set[String] = missingGatewayLoginParameters(parameters)
        missingParams.nonEmpty match {
          case true => {
            val message = ErrorMessages.GatewayLoginMissingParameters + missingParams.mkString(", ")
            logger.error("GatewayLogin error message : " + message)
            (400, message, emptyMap)
          }
          case false => {
            logger.debug("GatewayLogin parameters : " + parameters)
            (200, "", parameters)
          }
        }
      }
    }
  }

  def communicateWithCbs(jwtPayload: String) : Box[String] = {
    val isFirst = getFieldFromPayloadJson(jwtPayload, "is_first")
    val cbsAuthToken = getFieldFromPayloadJson(jwtPayload, "CBS_auth_token")
    val username = getFieldFromPayloadJson(jwtPayload, "username")
    logger.debug("isFirst : " + isFirst)
    logger.debug("cbsAuthToken : " + cbsAuthToken)
    if(isFirst.equalsIgnoreCase("true") || cbsAuthToken.equalsIgnoreCase("")){
      // Call CBS
      // Connector.connector.vend.getBankAccounts(username)
      Empty
    } else {
      // Do not call CBS
      Full("There is no need to call CBS")
    }
  }

  private def createConsumerAndSetResponseHeader(payload: String, u: Box[User], cbsAuthToken: Option[String]) = {
    u match {
      case Full(user) =>
        GatewayLogin.getOrCreateConsumer(payload, user)
        val jwtResponse = GatewayLogin.createJwt(payload, cbsAuthToken)
        setGatewayResponseHeader(jwtResponse)

      case _ =>
        // Do nothing
    }
  }

  def getOrCreateResourceUser(jwtPayload: String) : Box[User] = {
    val username = getFieldFromPayloadJson(jwtPayload, "username")
    logger.debug("username: " + username)
    communicateWithCbs(jwtPayload) match {
      case Full(s) if s.equalsIgnoreCase("There is no need to call CBS") => // Payload data do not require call to CBS
        logger.debug("There is no need to call CBS")
        Users.users.vend.getUserByProviderId(provider = gateway, idGivenByProvider = username) match {
          case Full(u) => // Only valid case because we expect to find a user
            createConsumerAndSetResponseHeader(jwtPayload, Full(u), None)
            Full(u)
          case Empty =>
            Failure("User cannot be found. Please initiate CBS communication in order to crete it.")
          case Failure(msg, _, _) =>
            Failure(msg)
          case _ =>
            Failure(ErrorMessages.GatewayLoginUnknownError)
        }
      case Full(s) if getErrors(s).length == 0 => // CBS returned response without any error
        logger.debug("CBS returned proper response")
        val u = Users.users.vend.getUserByProviderId(provider = gateway, idGivenByProvider = username).or { // Find a user
          Users.users.vend.createResourceUser( // Otherwise create a new one
            provider = gateway,
            providerId = Some(username),
            name = Some(username),
            email = None,
            userId = None
          )
        }
        val cbsAuthToken: Seq[String] = getCbsToken(jwtPayload)
        createConsumerAndSetResponseHeader(jwtPayload, u, Some(cbsAuthToken.head))
        // Update user accoun views
//        for {
//          user <- u
//          ru <- Users.users.vend.getResourceUserByResourceUserId(user.resourceUserId.value)
//        } {
//          Connector.connector.vend.updateUserAccountViewsOld(ru)
//        }
        u // Return user
      case Full(s) if getErrors(s).length > 0 =>
        logger.debug("CBS returned some errors")
        Failure(getErrors(s).mkString(", "))
      case Empty =>
        logger.debug("Call of CBS is not implemented")
        Failure("Call of CBS is not implemented")
      case Failure(msg, _, _) =>
        Failure(msg)
    }
  }

  def getOrCreateConsumer(jwtPayload: String, u: User) : Box[Consumer] = {
    val consumerId = getFieldFromPayloadJson(jwtPayload, "consumer_id")
    val consumerName = getFieldFromPayloadJson(jwtPayload, "consumer_name")
    logger.debug("consumer_id: " + consumerId)
    logger.debug("consumerName: " + consumerName)
    Consumers.consumers.vend.getOrCreateConsumer(
      consumerId=Some(consumerId),
      Some(Helpers.randomString(40).toLowerCase),
      Some(Helpers.randomString(40).toLowerCase),
      Some(true),
      name = Some(consumerName),
      appType = None,
      description = Some(gateway),
      developerEmail = None,
      redirectURL = None,
      createdByUserId = Some(u.userId)
    )
  }

  // Return a Map containing the GatewayLogin parameter : token -> value
  def getAllParameters(request: Box[Req]): Map[String, String] = {
    def toMap(parametersList: String) = {
      //transform the string "GatewayLogin token="value""
      //to a tuple (GatewayLogin_parameter,Decoded(value))
      def dynamicListExtract(input: String) = {
        val gatewayLoginPossibleParameters =
          List(
            "token"
          )
        if (input contains "=") {
          val split = input.split("=", 2)
          val parameterValue = split(1).replace("\"", "")
          //add only OAuth parameters and not empty
          if (gatewayLoginPossibleParameters.contains(split(0)) && !parameterValue.isEmpty)
            Some(split(0), parameterValue) // return key , value
          else
            None
        }
        else
          None
      }
      // We delete the "GatewayLogin" prefix and all the white spaces that may exist in the string
      val cleanedParameterList = parametersList.stripPrefix("GatewayLogin").replaceAll("\\s", "")
      val params = Map(cleanedParameterList.split(",").flatMap(dynamicListExtract _): _*)
      params
    }

    request match {
      case Full(a) => a.header("Authorization") match {
        case Full(header) => {
          if (header.contains("GatewayLogin"))
            toMap(header)
          else
            Map("error" -> "Missing GatewayLogin in header!")
        }
        case _ => Map("error" -> "Missing Authorization header!")
      }
      case _ => Map("error" -> "Request is incorrect!")
    }
  }

  // Returns the missing parameters
  def missingGatewayLoginParameters(parameters: Map[String, String]): Set[String] = {
    ("token" :: List()).toSet diff parameters.keySet
  }

  private def getToken(params: Map[String, String]): String = {
    val token = params.getOrElse("token", "")
    token
  }

  private def getFieldFromPayloadJson(payloadAsJsonString: String, fieldName: String) = {
    val jwtJson = parse(payloadAsJsonString) // Transform Json string to JsonAST
    compact(render(jwtJson.\\(fieldName))).replace("\"", "")
  }

  private def getErrors(message: String) : List[String] = {
    for {
      JArray(errorCodes) <- parse(message) \\ "errorCode"
      JField("errorCode", JString(error)) <- errorCodes
    } yield error
  }

  private def getCbsToken(message: String) : List[String] = {
    for {
      JArray(cbsToken) <- parse(message) \\ "cbsToken"
      JField("errorCode", JString(cbsToken)) <- cbsToken
    } yield cbsToken
  }


}
