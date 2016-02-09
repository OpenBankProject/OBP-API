/**
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

 Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
    by
    Simon Redfern : simon AT tesobe DOT com
    Everett Sochowski: everett AT tesobe DOT com
    Ayoub Benali : ayoub AT tesobe Dot com
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
import net.liftweb.util.{Props, Helpers}
import net.liftweb.util.Helpers._

import scala.compat.Platform

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
      //Test if the request is valid
      if(httpCode==200)
      {
        val claims = Map("" -> "")
        val (token,secret) = generateTokenAndSecret(claims)

        //Save the token that we have generated
        if(saveAuthorizationToken(directLoginParameters,token, secret))
          message = token
        else
          message = "invalid"
      }
      val tokenJSON = JSONFactory.createTokenJSON(message)
      successJsonResponse(Extraction.decompose(tokenJSON))
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

    def registeredApplication(consumerKey: String): Boolean = {
      Consumer.find(By(Consumer.key, consumerKey)) match {
        case Full(application) => application.isActive
        case _ => false
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
      message = "the following parameters are missing : " + missingParams.mkString(", ")
      httpCode = 400
    }
    else if (
      requestType == "protectedResource" &&
        !validAccessToken(parameters.get("token").get)
    ) {
      message = "Invalid or expired access token: " + parameters.get("token").get
      httpCode = 401
    }
    //check if the application is registered and active
    else if (
      requestType == "authorizationToken" &&
        Props.getBool("direct_login_consumer_key_mandatory", true) &&
        !registeredApplication(parameters.get("consumer_key").get)) {

      logger.error("application: " + parameters.get("consumer_key").get + " not found")
      message = "Invalid consumer credentials"
      httpCode = 401
    }
    //checking if the signature is correct
    //else if(! correctSignature(parameters, httpMethod))
    //{
    //  message = "Invalid signature"
    //  httpCode = 401
    //}
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

  private def saveAuthorizationToken(directLoginParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.{Token, TokenType}
    val token = Token.create
    token.tokenType(TokenType.Access)
    Consumer.find(By(Consumer.key, directLoginParameters.get("consumer_key").get)) match {
      case Full(consumer) => token.consumerId(consumer.id)
      case _ => None
    }
    val username = directLoginParameters.get("username").get.toString
    val password = directLoginParameters.get("password").get match {
      case p: String => p
      case _ => "error"
    }
    val userId = OBPUser.getUserId(username, password)
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
    val user = getUser(200, directLoginParameters.get("token"))
    if (user != Empty ) {
      val res = Full(user.get)
      res
    } else {
      ParamFailure(message, Empty, Empty, APIFailure(message, httpCode))
    }
  }

  def getUser(httpCode : Int, tokenID : Box[String]) : Box[User] =
    if(httpCode==200)
    {
      import code.model.Token
      logger.info("directLogin header correct ")
      Token.find(By(Token.key, tokenID.getOrElse(""))) match {
        case Full(token) => {
          logger.info("access token: "+ token + " found")
          val user = token.user
          //just a log
          user match {
            case Full(u) => logger.info("user " + u.emailAddress + " was found from the directLogin token")
            case _ => logger.info("no user was found for the directLogin token")
          }
          user
        }
        case _ =>{
          logger.warn("no token " + tokenID.get + " found")
          Empty
        }
      }
    }
    else
      Empty
}