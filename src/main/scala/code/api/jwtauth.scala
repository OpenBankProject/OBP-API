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
import code.model.dataAccess.OBPUser
import code.model.{Consumer, Token, TokenType, User}
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.mapper.By
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._

import scala.compat.Platform

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object JWTAuth extends RestHelper with Loggable {
  serve
  {
    //Handling get request for an "authorization token"
    case Req("my" :: "logins" :: "direct" :: Nil,_ , PostRequest) =>
    {
      //Extract the jwtauth parameters from the header and test if the request is valid
      var (httpCode, message, jwtAuthParameters) = validator("authorizationToken", "POST")
      //Test if the request is valid
      if(httpCode==200)
      {
        val claims = Map("app" -> "key")
        val (token,secret) = generateTokenAndSecret(claims)

        //Save the token that we have generated
        if(saveAuthorizationToken(jwtAuthParameters,token, secret))
          message="token="+token
      }
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
        //return an HTTP response
      Full(InMemoryResponse(message.getBytes,headers,Nil,httpCode))
    }
  }

  //Check if the request (access token or request token) is valid and return a tuple
  def validator(requestType : String, httpMethod : String) : (Int, String, Map[String,String]) = {
    //return a Map containing the jwtauth parameters : prameter -> value
    def getAllParameters : Map[String,String]= {

      def toMapFromBasicAuth(paramsEncoded : String) = {
        //println("basic=" + paramsEncoded )
        val params = new String(base64Decode(paramsEncoded.replaceAll("Basic ", "")))
        params.toString.split(":") match {
          case Array(str1, str2) => Map("username" -> str1, "password" -> str2)
          case _ => Map("error" -> "error")
        }
      }

      //Convert the list of jwtauth parameters to a Map
      def toMapFromReq(parametersList : Req ) = {
        val jwtauthPossibleParameters =
          List(
            "username",
            "password",
            "token"
          )

        if (parametersList.json_?) {
          val parameters = parametersList.json.map( _ .values ).getOrElse(Map[String,String] _)
          parameters.asInstanceOf[Map[String,String]]
        } else if (parametersList.xml_?) {
          val parameters = parametersList.xml.map( _ .text ).getOrElse(Map[String,String] _)
          parameters.asInstanceOf[Map[String,String]]
        } else {
          Map("error" -> "parameters incorrect")
        }
      }

      S.request match {
          case Full(a) =>  a.header("Authorization") match {
            case Full(parameters) => toMapFromBasicAuth(parameters)
            case _ => toMapFromReq(a)
          }
          case _ => Map("error" -> "request incorrect")
      }
    }

    def registeredApplication(consumerKey : String ) : Boolean = {
      Consumer.find(By(Consumer.key,consumerKey)) match {
        case Full(application) => application.isActive
        case _ => false
      }
    }

    //check if the token exists and is still valid
    def validRequestToken(tokenKey : String) ={
      Token.find(By(Token.key, tokenKey),By(Token.tokenType,TokenType.Request)) match {
        case Full(token) => token.isValid
        case _ => false
      }
    }

    def validAccessToken(tokenKey : String) = {
      Token.find(By(Token.key, tokenKey),By(Token.tokenType,TokenType.Access)) match {
        case Full(token) => token.isValid
          case _ => false
      }
    }

    //@return the missing parameters depending of the request type
    def missingjwtauthParameters(parameters : Map[String, String], requestType : String) : Set[String] = {
      //println(parameters.toString)
      if(requestType == "requestToken")
        ("username" :: "password" :: List()).toSet diff parameters.keySet
      else if(requestType=="authorizationToken")
        ("username" :: "password" :: List()).toSet diff parameters.keySet
      else if(requestType=="protectedResource")
        ("token" :: List()).toSet diff parameters.keySet
      else
        parameters.keySet
    }

    var message = ""
    var httpCode : Int = 500

    var parameters = getAllParameters

    //are all the necessary jwtauth parameters present?
    val missingParams = missingjwtauthParameters(parameters,requestType)
    if( missingParams.size != 0 )
    {
      message = "the following parameters are missing : " + missingParams.mkString(", ")
      httpCode = 400
    }

    //In the case jwtauth authorization token request, check if the token is still valid and the verifier is correct
    //else if(requestType=="authorizationToken" && !validRequestToken(parameters.get("token").get))
    //{
    //  message = "Invalid or expired request token: " + parameters.get("token").get
    //  httpCode = 401
    //}
    //In the case protected resource access request, check if the token is still valid
    else if (
        requestType=="protectedResource" &&
      ! validAccessToken(parameters.get("token").get)
    )
    {
      message = "Invalid or expired access token: " + parameters.get("token").get
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

  private def saveAuthorizationToken(jwtauthParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.{Token, TokenType}

    //val nonce = Nonce.create
    //nonce.consumerkey(jwtauthParameters.get("consumer_key").get)
    //nonce.timestamp(new Date(jwtauthParameters.get("timestamp").get.toLong))
    //nonce.tokenKey(jwtauthParameters.get("token").get)
    //nonce.value(jwtauthParameters.get("nonce").get)
    //val nonceSaved = nonce.save()

    val token = Token.create
    token.tokenType(TokenType.Access)
    //Consumer.find(By(Consumer.key,jwtauthParameters.get("consumer_key").get)) match {
    //  case Full(consumer) => token.consumerId(consumer.id)
    //  case _ => None
    //}
    //Token.find(By(Token.key, jwtauthParameters.get("token").get)) match {
    val userId = OBPUser.getUserId(jwtauthParameters.get("username").get, jwtauthParameters.get("password").get)
    //Token.find(By(Token.key, tokenKey)) match {
    //  case Full(requestToken) => token.userForeignKey(requestToken.userForeignKey)
    //  case _ => None
    //}
    token.verifier("verifier")
    token.consumerId(1) //TESTING ONLY
    token.userForeignKey(userId)
    token.key(tokenKey)
    token.secret(tokenSecret)
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.weeks(4)
    token.duration(tokenDuration)
    token.expirationDate(new Date(currentTime+tokenDuration))
    token.insertDate(new Date(currentTime))
    val tokenSaved = token.save()

    //nonceSaved &&
    true && tokenSaved
  }

  def getUser : Box[User] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, jwtauthParameters) = validator("protectedResource", httpMethod)

    val user = getUser(200, jwtauthParameters.get("token"))
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
      logger.info("jwtauth header correct ")
      Token.find(By(Token.key, tokenID.get)) match {
        case Full(token) => {
          logger.info("access token: "+ token + " found")
          val user = token.user
          //just a log
          user match {
            case Full(u) => logger.info("user " + u.emailAddress + " was found from the jwtauth token")
            case _ => logger.info("no user was found for the jwtauth token")
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