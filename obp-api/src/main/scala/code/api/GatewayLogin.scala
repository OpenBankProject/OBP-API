/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util._
import code.bankconnectors.Connector
import code.consumer.Consumers
import code.model.dataAccess.AuthUser
import code.model.{Consumer, User}
import code.users.Users
import code.util.Helper.MdcLoggable
import com.nimbusds.jwt.JWTClaimsSet
import com.openbankproject.commons.model.{InboundAccount, User}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._
import net.liftweb.util.Helpers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * This object provides the API calls necessary to
  * authenticate users using JSON Web Tokens (http://jwt.io).
  */


object JSONFactoryGateway {
  //Never update these values inside the case class
  case class PayloadOfJwtJSON(
                               login_user_name: String,
                               is_first: Boolean,
                               app_id: String,
                               app_name: String,
                               time_stamp: String,
                               cbs_token: Option[String],
                               cbs_id: String, 
                               session_id: Option[String]
                             )

}

object GatewayLogin extends RestHelper with MdcLoggable {

  val gateway = "Gateway" // This value is used for ResourceUser.provider and Consumer.description

  def createJwt(payloadAsJsonString: String, cbsAuthToken: Option[String]) : String = {
    val username = getFieldFromPayloadJson(payloadAsJsonString, "login_user_name")
    val consumerId = getFieldFromPayloadJson(payloadAsJsonString, "app_id")
    val consumerName = getFieldFromPayloadJson(payloadAsJsonString, "app_name")
    val timestamp = getFieldFromPayloadJson(payloadAsJsonString, "time_stamp")
    val cbsId = getFieldFromPayloadJson(payloadAsJsonString, "cbs_id")
    val sessionId = getFieldFromPayloadJson(payloadAsJsonString, "session_id")
    val cbsToken = cbsAuthToken match {
      case Some(v) => v
      case None => getFieldFromPayloadJson(payloadAsJsonString, "cbs_token")
    }

    val json = JSONFactoryGateway.PayloadOfJwtJSON(
      login_user_name = username,
      is_first = false,
      app_id = consumerId,
      app_name = consumerName,
      time_stamp = timestamp,
      cbs_token = Some(cbsToken),
      cbs_id = cbsId,
      session_id= Some(sessionId)
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
    validateJwtToken(jwt) match {
      case Full(jwtPayload) =>
        Full(compactRender(Extraction.decompose(jwtPayload)))
      case _  =>
        Failure(ErrorMessages.GatewayLoginJwtTokenIsNotValid)
    }
  }

  def validateJwtToken(token: String): Box[PayloadOfJwtJSON] = {
    APIUtil.getPropsAsBoolValue("jwt.use.ssl", false) match {
      case true =>
        val claim = CertificateUtil.decryptJwtWithRsa(token)
        Box(parse(claim.toString).extractOpt[PayloadOfJwtJSON])
      case false =>
        CertificateUtil.verifywtWithHmacProtection(token) match {
          case true =>
            val claim = CertificateUtil.parseJwtWithHmacProtection(token)
            Box(parse(claim.toString).extractOpt[PayloadOfJwtJSON])
          case _ =>
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

  def refreshBankAccounts(jwtPayload: String, callContext: Option[CallContext]) : Box[(String, List[InboundAccount], Option[CallContext])] = {
    val isFirst = getFieldFromPayloadJson(jwtPayload, "is_first")
    val cbsToken = getFieldFromPayloadJson(jwtPayload, "cbs_token")
    val username = getFieldFromPayloadJson(jwtPayload, "login_user_name")
    logger.debug("is_first : " + isFirst)
    logger.debug("cbs_token : " + cbsToken)
    
    //These payload should be set into GatewayLogin before the first CSB call
    val payloadJson = parse(jwtPayload).extract[PayloadOfJwtJSON]
    val callContextForRequest = ApiSession.updateCallContext(GatewayLoginRequestPayload(Some(payloadJson)), callContext)
    
    if(APIUtil.isFirst(isFirst)) // Case is_first="true"
    { 
      //if isFirst = true, we create new sessionId for CallContext
      val callContextNewSessionId = ApiSession.createSessionId(callContextForRequest)
      // Call CBS, Note, this is the first time to call Adapter in GatewayLogin process
      val res = Connector.connector.vend.getBankAccountsForUser(username,callContextNewSessionId) // Box[List[InboundAccountJune2017]]//
      res match {
        case Full((l, callContextReturn))=>
          Full(compactRender(Extraction.decompose(l)),l, callContextReturn) // case class --> JValue --> Json string
        case Empty =>
          Empty
        case Failure(msg, t, c) =>
          Failure(msg, t, c)
      }
    } else { // Do not call CBS
      //if isFirst = false, we set the sessionId from the GateWayLogin.gatewayLoginRequestPayload
      val callContextUpdatedSessionId = ApiSession.updateSessionId(callContextForRequest)
      Full(ErrorMessages.GatewayLoginNoNeedToCallCbs, Nil, callContextUpdatedSessionId)
    }
  }

  def refreshBankAccountsFuture(jwtPayload: String, callContext: Option[CallContext]) : Future[Box[(String, List[InboundAccount], Option[CallContext])]] = {
    val isFirst = getFieldFromPayloadJson(jwtPayload, "is_first")
    val cbsToken = getFieldFromPayloadJson(jwtPayload, "cbs_token")
    val username = getFieldFromPayloadJson(jwtPayload, "login_user_name")
    logger.debug("is_first : " + isFirst)
    logger.debug("cbs_token : " + cbsToken)
    
    //These payload should be set into GatewayLogin before the first CSB call
    val payloadJson = parse(jwtPayload).extract[PayloadOfJwtJSON]
    val callContextForRequest = ApiSession.updateCallContext(GatewayLoginRequestPayload(Some(payloadJson)), callContext)
    
    if(APIUtil.isFirst(isFirst)) // Case is_first="true"
    { 
      //We update the sessionId only once, and only when is_first = true. 
      val callContextUpdatedSessionId = ApiSession.createSessionId(callContextForRequest)

      // Call CBS
      val res = Connector.connector.vend.getBankAccountsForUserFuture(username,callContextUpdatedSessionId) // Box[List[InboundAccountJune2017]]//
      res map {
        case Full((l, callContextReturn)) =>
          Full(compactRender(Extraction.decompose(l)), l, callContextReturn) // case class --> JValue --> Json string
        case Empty =>
          Empty
        case Failure(msg, t, c) =>
          Failure(msg, t, c)
      }
    } else { // Do not call CBS
      Future {
        val callContextUpdatedSessionId = ApiSession.updateSessionId(callContextForRequest)
        Full((ErrorMessages.GatewayLoginNoNeedToCallCbs, Nil, callContextUpdatedSessionId))
      }
    }
  }

  def getOrCreateResourceUser(jwtPayload: String, callContext: Option[CallContext]) : Box[(User, Option[String], Option[CallContext])] = {
    val username = getFieldFromPayloadJson(jwtPayload, "login_user_name")
    logger.debug("login_user_name: " + username)
    val cbsAndCallContextBox = refreshBankAccounts(jwtPayload, callContext)
    for {
      tuple <- cbsAndCallContextBox match {
        case Full((s, _, callContextNew)) if s.equalsIgnoreCase(ErrorMessages.GatewayLoginNoNeedToCallCbs) => // Payload data do not require call to CBS
          logger.debug(ErrorMessages.GatewayLoginNoNeedToCallCbs)
          Users.users.vend.getUserByProviderId(provider = gateway, idGivenByProvider = username) match {
            case Full(u) => // Only valid case because we expect to find a user
              Full(u, None,callContextNew)
            case Empty =>
              Failure(ErrorMessages.GatewayLoginCannotFindUser)
            case Failure(msg, t, c) =>
              Failure(msg, t, c)
            case _ =>
              Failure(ErrorMessages.GatewayLoginUnknownError)
          }
        case Full((s, accounts, callContextNew )) if getErrors(s).forall(_.equalsIgnoreCase("")) => // CBS returned response without any error
          logger.debug("CBS returned proper response")
          Users.users.vend.getUserByProviderId(provider = gateway, idGivenByProvider = username).or { // Find a user
            Users.users.vend.createResourceUser( // Otherwise create a new one
              provider = gateway,
              providerId = Some(username),
              name = Some(username),
              email = None,
              userId = None
            )
          } match {
            case Full(u) =>
              val isFirst = getFieldFromPayloadJson(jwtPayload, "is_first")
              // Update user account views, only when is_first == true in the GatewayLogin token's payload .
              if(APIUtil.isFirst(isFirst)) {
                AuthUser.updateUserAccountViews(u, accounts)
              }
              Full((u, Some(getCbsTokens(s).head),callContextNew)) // Return user
            case Empty =>
              Failure(ErrorMessages.GatewayLoginCannotGetOrCreateUser)
            case Failure(msg, t, c) =>
              Failure(msg, t, c)
            case _ =>
              Failure(ErrorMessages.GatewayLoginUnknownError)
          }

        case Full((s, _,_)) if getErrors(s).exists(_.equalsIgnoreCase("") == false) => // CBS returned some errors"
          logger.debug("CBS returned some errors")
          Failure(getErrors(s).mkString(", "))
        case Empty =>
          logger.debug(ErrorMessages.GatewayLoginCannotGetCbsToken)
          Failure(ErrorMessages.GatewayLoginCannotGetCbsToken)
        case Failure(msg, t, c) =>
          Failure(msg, t, c)
        case _ =>
          Failure(ErrorMessages.GatewayLoginUnknownError)
      }
    } yield {
      tuple
    }
  }
  def getOrCreateResourceUserFuture(jwtPayload: String, callContext: Option[CallContext]) : Future[Box[(User, Option[String], Option[CallContext])]] = {
    val username = getFieldFromPayloadJson(jwtPayload, "login_user_name")
    logger.debug("login_user_name: " + username)
    val cbsAndCallContextF = refreshBankAccountsFuture(jwtPayload, callContext)
    for {
      cbs <- cbsAndCallContextF
      tuple <- cbs match {
        case Full((s, _, callContextNew)) if s.equalsIgnoreCase(ErrorMessages.GatewayLoginNoNeedToCallCbs) => // Payload data do not require call to CBS
          logger.debug(ErrorMessages.GatewayLoginNoNeedToCallCbs)
          Users.users.vend.getUserByProviderIdFuture(provider = gateway, idGivenByProvider = username) map {
            case Full(u) => // Only valid case because we expect to find a user
              Full(u, None, callContextNew)
            case Empty =>
              Failure(ErrorMessages.GatewayLoginCannotFindUser)
            case Failure(msg, t, c) =>
              Failure(msg, t, c)
            case _ =>
              Failure(ErrorMessages.GatewayLoginUnknownError)
          }
        case Full((s, accounts, callContextNew)) if getErrors(s).forall(_.equalsIgnoreCase("")) => // CBS returned response without any error
          logger.debug("CBS returned proper response")
          Users.users.vend.getOrCreateUserByProviderIdFuture(provider = gateway, idGivenByProvider = username, name = None, email = None) map {
            case Full(u) =>
              val isFirst = getFieldFromPayloadJson(jwtPayload, "is_first")
              // Update user account views, only when is_first == true in the GatewayLogin token's payload .
              if(APIUtil.isFirst(isFirst)) {
                AuthUser.updateUserAccountViews(u, accounts)
              }
              Full(u, Some(getCbsTokens(s).head), callContextNew) // Return user
            case Empty =>
              Failure(ErrorMessages.GatewayLoginCannotGetOrCreateUser)
            case Failure(msg, t, c) =>
              Failure(msg, t, c)
            case _ =>
              Failure(ErrorMessages.GatewayLoginUnknownError)
          }

        case Full((s, _, _)) if getErrors(s).exists(_.equalsIgnoreCase("")==false) => // CBS returned some errors"
          logger.debug("CBS returned some errors")
          Future {
            Failure(getErrors(s).mkString(", "))
          }
        case Empty =>
          logger.debug(ErrorMessages.GatewayLoginCannotGetCbsToken)
          Future {
            Failure(ErrorMessages.GatewayLoginCannotGetCbsToken)
          }
        case Failure(msg, t, c) =>
          Future {
            Failure(msg, t, c)
          }
        case _ =>
          Future {
            Failure(ErrorMessages.GatewayLoginUnknownError)
          }
      }
    } yield {
      tuple
    }
  }

  def getOrCreateConsumer(jwtPayload: String, u: User) : Box[Consumer] = {
    val consumerId = getFieldFromPayloadJson(jwtPayload, "app_id")
    val consumerName = getFieldFromPayloadJson(jwtPayload, "app_name")
    logger.debug("app_id: " + consumerId)
    logger.debug("app_name: " + consumerName)
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

  // Try to find CBS auth token in Json string received from South side and extract to list
  // Return list of same CBS auth token values
  def getCbsTokens(message: String) : List[String] = {
    val json = parse(message)
    val listOfValues = for {
      JArray(objects) <- json
      JObject(obj) <- objects
      JField("cbsToken", JString(fieldName)) <- obj
    } yield fieldName
    listOfValues
  }

  def getUser : Box[User] = {
    val (httpCode, message, parameters) = GatewayLogin.validator(S.request)
    httpCode match {
      case 200 =>
        val payload = GatewayLogin.parseJwt(parameters)
        payload match {
          case Full(payload) =>
            val username = getFieldFromPayloadJson(payload, "username")
            logger.debug("username: " + username)
            Users.users.vend.getUserByProviderId(provider = gateway, idGivenByProvider = username)
          case _ =>
            None
        }
      case _  =>
        None
    }
  }
}
