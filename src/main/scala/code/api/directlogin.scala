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

object DirectLogin extends RestHelper with Loggable {
  serve
  {
    //Handling get request for an "authorization token"
    case Req("my" :: "logins" :: "direct" :: Nil,_ , PostRequest) =>
    {
      //Extract the directlogin parameters from the header and test if the request is valid
      var (httpCode, message, directLoginParameters) = validator("authorizationToken", "POST")
      //Test if the request is valid
      if(httpCode==200)
      {
        val claims = Map("" -> "")
        val (token,secret) = generateTokenAndSecret(claims)

        //Save the token that we have generated
        if(saveAuthorizationToken(directLoginParameters,token, secret))
          message="token="+token
      }
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
        //return an HTTP response
      Full(InMemoryResponse(message.getBytes,headers,Nil,httpCode))
    }
  }

  //Check if the request (access token or request token) is valid and return a tuple
  def validator(requestType : String, httpMethod : String) : (Int, String, Map[String,String]) = {
    //return a Map containing the directlogin parameters : prameter -> value
    def getAllParameters : Map[String,String]= {

      def toMapFromBasicAuth(paramsEncoded : String) = {
        //println("basic=" + paramsEncoded )
        val params = new String(base64Decode(paramsEncoded.replaceAll("Basic ", "")))
        params.toString.split(":") match {
          case Array(str1, str2) => Map("dl_username" -> str1, "dl_password" -> str2)
          case _ => Map("error" -> "error")
        }
      }

      def toMap(parametersList : String) = {
        //transform the string "directlogin_prameter="value""
        //to a tuple (directlogin_parameter,Decoded(value))
        def dynamicListExtract(input: String)  = {
          val directloginPossibleParameters =
            List(
              "dl_consumer_key",
              "dl_token",
              "dl_username",
              "dl_password"
            )

          if (input contains "=") {
            val split = input.split("=",2)
            val parameterValue = split(1).replace("\"","")
            //add only OAuth parameters and not empty
            if(directloginPossibleParameters.contains(split(0)) && ! parameterValue.isEmpty)
              Some(split(0),parameterValue)  // return key , value
            else
              None
          }
          else
            None
        }
        //we delete the "Oauth" prefix and all the white spaces that may exist in the string
        val cleanedParameterList = parametersList.stripPrefix("DirectLogin").replaceAll("\\s","")
        val params = Map(cleanedParameterList.split(",").flatMap(dynamicListExtract _): _*)
        params
      }
      
      //Convert the list of directlogin parameters to a Map
      def toMapFromReq(parametersList : Req ) = {
        val directloginPossibleParameters =
          List(
            "dl_username",
            "dl_password",
            "dl_consumer_key",
            "dl_token"
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
          case Full(header) => {
            if (header.contains("DirectLogin"))
              toMap(header)
            else
              toMapFromBasicAuth(header)
          }
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

    def validAccessToken(tokenKey : String) = {
      Token.find(By(Token.key, tokenKey),By(Token.tokenType,TokenType.Access)) match {
        case Full(token) => token.isValid
          case _ => false
      }
    }

    //@return the missing parameters depending of the request type
    def missingdirectloginParameters(parameters : Map[String, String], requestType : String) : Set[String] = {
      //println(parameters.toString)
      if(requestType == "requestToken")
        ("dl_username" :: "dl_password" :: List()).toSet diff parameters.keySet
      else if(requestType=="authorizationToken")
        ("dl_username" :: "dl_password" :: "dl_consumer_key" :: List()).toSet diff parameters.keySet
      else if(requestType=="protectedResource")
        ("dl_token" :: List()).toSet diff parameters.keySet
      else
        parameters.keySet
    }

    var message = ""
    var httpCode : Int = 500

    var parameters = getAllParameters

    //are all the necessary directlogin parameters present?
    val missingParams = missingdirectloginParameters(parameters,requestType)
    if( missingParams.size != 0 )
    {
      message = "the following parameters are missing : " + missingParams.mkString(", ")
      httpCode = 400
    }
    else if (
        requestType=="protectedResource" &&
      ! validAccessToken(parameters.get("dl_token").get)
    )
    {
      message = "Invalid or expired access token: " + parameters.get("dl_token").get
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

  private def saveAuthorizationToken(directloginParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.{Token, TokenType}

    val token = Token.create
    token.tokenType(TokenType.Access)
    Consumer.find(By(Consumer.key,directloginParameters.get("dl_consumer_key").get)) match {
      case Full(consumer) => token.consumerId(consumer.id)
      case _ => None
    }
    val userId = OBPUser.getUserId(directloginParameters.get("dl_username").get, directloginParameters.get("dl_password").get)
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
    val (httpCode, message, directloginParameters) = validator("protectedResource", httpMethod)

    val user = getUser(200, directloginParameters.get("dl_token"))
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
      logger.info("directlogin header correct ")
      Token.find(By(Token.key, tokenID.getOrElse(""))) match {
        case Full(token) => {
          logger.info("access token: "+ token + " found")
          val user = token.user
          //just a log
          user match {
            case Full(u) => logger.info("user " + u.emailAddress + " was found from the directlogin token")
            case _ => logger.info("no user was found for the directlogin token")
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