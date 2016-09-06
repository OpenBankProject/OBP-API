/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE / Music Pictures Ltd

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

import java.util.Date

import authentikat.jwt.{JsonWebToken, JwtClaimsSet, JwtHeader}
import code.api.util.APIUtil._
import code.model.dataAccess.OBPUser
import code.model.{Consumer, Token, TokenType, User}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.mapper.By
import net.liftweb.util.{Helpers, Props}
import net.liftweb.util.Helpers._

import scala.compat.Platform
import code.api.util.{APIUtil, ErrorMessages}

/**
* This object provides the API calls necessary to
* authenticate users using JSON Web Tokens (http://jwt.io).
*/

object JSONFactory {
  case class TokenJSON( token : String )

  def stringOrNull(text: String) =
    if (text == null || text.isEmpty)
      null
    else
      text

  def stringOptionOrNull(text: Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }

  def createTokenJSON(token: String): TokenJSON = {
    new TokenJSON(
      stringOrNull(token)
    )
  }
}

object DirectLogin extends RestHelper with Loggable {

  // Our version of serve
  def dlServe(handler : PartialFunction[Req, JsonResponse]) : Unit = {
    val obpHandler : PartialFunction[Req, () => Box[LiftResponse]] = {
      new PartialFunction[Req, () => Box[LiftResponse]] {
        def apply(r : Req) = {
          handler(r)
        }
        def isDefinedAt(r : Req) = handler.isDefinedAt(r)
      }
    }
    super.serve(obpHandler)
  }

  dlServe
  {
    //Handling get request for a token
    case Req("my" :: "logins" :: "direct" :: Nil,_ , PostRequest|GetRequest) => {

      //Extract the directLogin parameters from the header and test if the request is valid
      var (httpCode, message, directLoginParameters) = validator("authorizationToken", getHttpMethod)

      if (httpCode == 200) {
        val userId:Long = (for {id <- getUserId(directLoginParameters)} yield id).getOrElse(0)

        if (userId == 0) {
          message = ErrorMessages.InvalidLoginCredentials
          httpCode = 401
        } else {
          val claims = Map("" -> "")
          val (token:String, secret:String) = generateTokenAndSecret(claims)

          //Save the token that we have generated
          if (saveAuthorizationToken(directLoginParameters, token, secret, userId)) {
            message = token
          } else {
            httpCode = 500
            message = "invalid"
          }
        }
      }

      if (httpCode == 200)
        successJsonResponse(Extraction.decompose(JSONFactory.createTokenJSON(message)))
      else
        errorJsonResponse(message, httpCode)
    }
  }

  def getHttpMethod = S.request match {
    case Full(s) => s.post_? match {
      case true => "POST"
      case _    => "GET"
    }
    case _ => "ERROR"
  }


  //Check if the request (access token or request token) is valid and return a tuple
  def validator(requestType : String, httpMethod : String) : (Int, String, Map[String,String]) = {
    //return a Map containing the directLogin parameters : prameter -> value
    def getAllParameters: Map[String, String] = {
      def toMap(parametersList: String) = {
        //transform the string "directLogin_prameter="value""
        //to a tuple (directLogin_parameter,Decoded(value))
        def dynamicListExtract(input: String) = {
          val directLoginPossibleParameters =
            List(
              "consumer_key",
              "token",
              "username",
              "password"
            )
          if (input contains "=") {
            val split = input.split("=", 2)
            val parameterValue = split(1).replace("\"", "")
            //add only OAuth parameters and not empty
            if (directLoginPossibleParameters.contains(split(0)) && !parameterValue.isEmpty)
              Some(split(0), parameterValue) // return key , value
            else
              None
          }
          else
            None
        }
        //we delete the "DirectLogin" prefix and all the white spaces that may exist in the string
        val cleanedParameterList = parametersList.stripPrefix("DirectLogin").replaceAll("\\s", "")
        val params = Map(cleanedParameterList.split(",").flatMap(dynamicListExtract _): _*)
        params
      }

      S.request match {
        case Full(a) => a.header("Authorization") match {
          case Full(header) => {
            if (header.contains("DirectLogin"))
              toMap(header)
            else
              Map("error" -> "header incorrect")
          }
          case _ => Map("error" -> "missing header")
        }
        case _ => Map("error" -> "request incorrect")
      }
    }

    def validAccessToken(tokenKey: String) = {
      Token.find(By(Token.key, tokenKey), By(Token.tokenType, TokenType.Access)) match {
        case Full(token) => token.isValid
        case _ => false
      }
    }

    //@return the missing parameters depending of the request type
    def missingDirectLoginParameters(parameters: Map[String, String], requestType: String): Set[String] = {
      requestType match {
        case "authorizationToken" =>
          ("username" :: "password" :: "consumer_key" :: List()).toSet diff parameters.keySet
        case "protectedResource" =>
          ("token" :: List()).toSet diff parameters.keySet
        case _ =>
          parameters.keySet
      }
    }

    var message = ""
    var httpCode: Int = 500

    val parameters = getAllParameters

    //are all the necessary directLogin parameters present?
    val missingParams = missingDirectLoginParameters(parameters, requestType)
    if (missingParams.nonEmpty) {
      message = ErrorMessages.DirectLoginMissingParameters + missingParams.mkString(", ")
      httpCode = 400
    }
    else if (
      requestType == "protectedResource" &&
        ! validAccessToken(parameters.getOrElse("token", ""))
    ) {
      message = ErrorMessages.DirectLoginInvalidToken + parameters.getOrElse("token", "")
      httpCode = 401
    }
    //check if the application is registered and active
    else if (
      requestType == "authorizationToken" &&
        Props.getBool("direct_login_consumer_key_mandatory", true) &&
        ! APIUtil.registeredApplication(parameters.getOrElse("consumer_key", ""))) {

      logger.error("application: " + parameters.getOrElse("consumer_key", "") + " not found")
      message = ErrorMessages.InvalidConsumerKey
      httpCode = 401
    }
    else
      httpCode = 200
    if(message.nonEmpty)
      logger.error("error message : " + message)
    (httpCode, message, parameters)
  }

  private def generateTokenAndSecret(claims: Map[String,String]) =
  {
    // generate random string
    val secret_message = Helpers.randomString(40)
    // jwt header
    val header = JwtHeader("HS256")
    // generate jwt token
    val token_message = JsonWebToken(header, JwtClaimsSet(claims), secret_message)
    (token_message, secret_message)
  }

  private def saveAuthorizationToken(directLoginParameters: Map[String, String], tokenKey: String, tokenSecret: String, userId: Long) =
  {
    import code.model.{Token, TokenType}
    val token = Token.create
    token.tokenType(TokenType.Access)
    Consumer.find(By(Consumer.key, directLoginParameters.getOrElse("consumer_key", ""))) match {
      case Full(consumer) => token.consumerId(consumer.id)
      case _ => None
    }
    token.userForeignKey(userId)
    token.key(tokenKey)
    token.secret(tokenSecret)
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.weeks(4)
    token.duration(tokenDuration)
    token.expirationDate(new Date(currentTime+tokenDuration))
    token.insertDate(new Date(currentTime))
    val tokenSaved = token.save()
    tokenSaved
  }

  def getUser : Box[User] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, directLoginParameters) = validator("protectedResource", httpMethod)

    val user = for {
      u <- getUserFromToken(if (directLoginParameters.isDefinedAt("token")) directLoginParameters.get("token") else Empty)
    } yield u

    if (user.isEmpty )
      ParamFailure(message, Empty, Empty, APIFailure(message, httpCode))
    else
      user
  }


  private def getUserId(directLoginParameters: Map[String, String]): Box[Long] = {
    val username = directLoginParameters.getOrElse("username", "")
    val password = directLoginParameters.getOrElse("password", "")

    var userId = for {id <- OBPUser.getUserId(username, password)} yield id

    if (userId.isEmpty) {
      OBPUser.externalUserHelper(username, password)
      userId = for {id <- OBPUser.getUserId(username, password)} yield id
    }

    userId
  }


  def getUserFromToken(tokenID : Box[String]) : Box[User] = {
    logger.info("DirectLogin header correct ")
    Token.find(By(Token.key, tokenID.getOrElse(""))) match {
      case Full(token) => {
        logger.info("access token: " + token + " found")
        val user = token.user
        //just a log
        user match {
          case Full(u) => logger.info("user " + u.name + " was found from the DirectLogin token")
          case _ => logger.info("no user was found for the DirectLogin token")
        }
        user
      }
      case _ => {
        logger.warn("no token " + tokenID.getOrElse("") + " found")
        Empty
      }
    }
  }


}
