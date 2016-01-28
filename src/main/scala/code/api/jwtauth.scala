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

import java.net.{URLDecoder, URLEncoder}
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import authentikat.jwt._
import code.model.{Consumer, Token, TokenType, User}
import net.liftweb.common.{Box, Empty, Full, Loggable, ParamFailure}
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.{tryo, _}
import net.liftweb.util.{Helpers, Props}

import scala.compat.Platform

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object JWTAuth extends RestHelper with Loggable {
  serve
  {
    //Handling get request for a "request token"
    case Req("my" :: "logins" :: "direct" :: Nil,_ , PostRequest) =>
    {
      //Extract the jwtauth parameters from the header and test if the request is valid
      var (httpCode, message, jwtAuthParameters) = validator("requestToken", "POST")
      //Test if the request is valid
      if(httpCode==200)
      {
        //Generate the token and secret
        val secret = Helpers.randomString(40)
        val header = JwtHeader("HS256")
        val claimsSet = JwtClaimsSet(Map("app" -> "key"))
        val token: String = JsonWebToken(header, claimsSet, secret)

        //Save the token that we have generated
        if(saveRequestToken(jwtAuthParameters,token, secret))
          message="token="+token
      }
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
        //return an HTTP response
      Full(InMemoryResponse(message.getBytes,headers,Nil,httpCode))
    }

    case Req("my" :: "logins" :: "authorize" :: Nil,_, PostRequest) => authorize()
    case Req("my" :: "logins" :: "authorize" :: Nil,_, GetRequest) => authorize()

  }

  def authorize() =
  {
    //Extract the jwtauth parameters from the header and test if the request is valid
    var (httpCode, message, jwtAuthParameters) = validator("authorizationToken", "POST")
    //Test if the request is valid
    if(httpCode==200)
    {
      //Generate the token and secret/token
      val (token,secret) = generateTokenAndSecret()
      //Save the token that we have generated
      if(saveAuthorizationToken(jwtAuthParameters,token, secret))
      //remove the request token so the application could not exchange it
      //again to get an other access token
        Token.find(By(Token.key,jwtAuthParameters.get("token").get)) match {
          case Full(requestToken) => requestToken.delete_!
          case _ => None
        }

      message="token="+token+"&token_secret="+secret
    }
    val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
    //return an HTTP response
    Full(InMemoryResponse(message.getBytes,headers,Nil,httpCode))
  }

  //Check if the request (access token or request token) is valid and return a tuple
  def validator(requestType : String, httpMethod : String) : (Int, String, Map[String,String]) = {
    //return a Map containing the jwtauth parameters : prameter -> value
    def getAllParameters : Map[String,String]= {

      def toMapFromBasicAuth(paramsEncoded : String) = {
        val params = new String(base64Decode(paramsEncoded.replaceAll("Basic ", "")))
        params.toString.split(":") match {
          case Array(str1, str2) => Map("username" -> str1, "password" -> str2)
          case _ => Map("error" -> "error")
        }
      }

      //Convert the list of jwtauth parameters to a Map
      def toMap(parametersList : Map[String, List[String]]) = {
        println("parameters=" + parametersList)

          val jwtauthPossibleParameters =
            List(
              "username",
              "password",
              "token"
            )
          for ( p <- parametersList if jwtauthPossibleParameters.contains(p._1))
            yield (p._1 -> p._2.head)
      }

      S.request match {
          case Full(a) =>  a.header("Authorization") match {
            case Full(parameters) => toMapFromBasicAuth(parameters)
            case _ => toMap(a.params)
          }
          case _ => Map(("",""))
      }
    }

    def supportedjwtauthVersion(jwtauthVersion : Option[String]) : Boolean = {
      //auth_version is OPTIONAL.  If present, MUST be set to "1.0".
      jwtauthVersion match
      {
        case Some(a) =>  a=="1" || a=="1.0"
        case _ => true
      }
    }

    def wrongTimestamp(requestTimestamp : Option[String]) : Option[String] = {
      requestTimestamp match {
        case Some(timestamp) => {
          tryo{
            timestamp.toLong
          } match {
            case Full(l) =>
              tryo{
                new Date(l)
              } match {
                case Full(d) => {
                  val currentTime = Platform.currentTime / 1000
                  val timeRange : Long = Helpers.minutes(3)
                  //check if the timestamp is positive and in the time range
                  if(d.getTime < 0 || d.before(new Date(currentTime - timeRange)) ||  d.after(new Date(currentTime + timeRange)))
                    Some("timestamp value: " + timestamp +" is in and invalid time range")
                  else
                    None
                }
                case _ => Some("timestamp value: " + timestamp +" is an invalid date")
              }
            case _ => Some("timestamp value: " + timestamp +" is invalid")
          }

        }
        case _ => Some("the following parameter is missing: timestamp")
      }
    }

    def registeredApplication(consumerKey : String ) : Boolean = {
      Consumer.find(By(Consumer.key,consumerKey)) match {
        case Full(application) => application.isActive
        case _ => false
      }
    }

    def correctSignature(jwtauthparameters : Map[String, String], httpMethod : String) = {
      //Normalize an encode the request parameters as explained in Section 3.4.1.3.2
      //of jwtauth 1.0 specification (http://tools.ietf.org/html/rfc5849)
      def generatejwtauthParametersString(jwtauthparameters : Map[String, String]) : String = {
        def sortParam( keyAndValue1 : (String, String), keyAndValue2 : (String, String))= keyAndValue1._1.compareTo(keyAndValue2._1) < 0
        var parameters =""

        //sort the parameters by name
        jwtauthparameters.toList.sortWith(sortParam _).foreach(
          t =>
            if(t._1 != "signature")
              parameters += URLEncoder.encode(t._1,"UTF-8")+"%3D"+ URLEncoder.encode(t._2,"UTF-8")+"%26"

        )
        parameters = parameters.dropRight(3) //remove the "&" encoded sign
        parameters
      }

      //prepare the base string
      var baseString = httpMethod+"&"+URLEncoder.encode(Props.get("hostname").openOr(S.hostAndPath)  + S.uri,"UTF-8")+"&"
      baseString+= generatejwtauthParametersString(jwtauthparameters)

      val encodeBaseString = URLEncoder.encode(baseString,"UTF-8")
      //get the key to sign
      val comsumer = Consumer.find(
          By(Consumer.key,jwtauthparameters.get("consumer_key").get)
        ).get
      var secret= comsumer.secret.toString

      jwtauthparameters.get("token") match {
        case Some(tokenKey) => Token.find(By(Token.key,tokenKey)) match {
            case Full(token) => secret+= "&" +token.secret.toString()
            case _ => secret+= "&"
          }
        case _ => secret+= "&"
      }
      logger.info("base string: " + baseString)
      //signing process
      val signingAlgorithm : String = if(jwtauthparameters.get("signature_method").get.toLowerCase == "hmac-sha256")
        "HmacSHA256"
      else
        "HmacSHA1"

      logger.info("signing method: " + signingAlgorithm)
      logger.info("signing key: " + secret)
      logger.info("signing key in bytes: " + secret.getBytes("UTF-8"))

      var m = Mac.getInstance(signingAlgorithm);
      m.init(new SecretKeySpec(secret.getBytes("UTF-8"),signingAlgorithm))
      val calculatedSignature = Helpers.base64Encode(m.doFinal(baseString.getBytes))

      logger.info("calculatedSignature: " + calculatedSignature)
      //logger.info("received signature:" + jwtauthparameters.get("signature").get)
      logger.info("received signature after decoding: " + URLDecoder.decode(jwtauthparameters.get("signature").get))

      calculatedSignature== URLDecoder.decode(jwtauthparameters.get("signature").get,"UTF-8")
    }

    //check if the token exists and is still valid
    def validToken(tokenKey : String) ={
      Token.find(By(Token.key, tokenKey),By(Token.tokenType,TokenType.Request)) match {
        case Full(token) => token.isValid
        case _ => false
      }
    }

    def validToken2(tokenKey : String) = {
      Token.find(By(Token.key, tokenKey),By(Token.tokenType,TokenType.Access)) match {
        case Full(token) => token.isValid
          case _ => false
      }
    }

    //@return the missing parameters depending of the request type
    def missingjwtauthParameters(parameters : Map[String, String], requestType : String) : Set[String] = {
      val parametersBase =
        List()
      //    "username",
      //    "password",
      //    "token"
      //  )
      if(requestType == "requestToken")
        ("username" :: "password" :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="authorizationToken")
        ("token" :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="protectedResource")
        ("token" :: parametersBase).toSet diff parameters.keySet
      else
        parameters.keySet
    }

    def supportedSignatureMethod(jwtauthSignatureMethod : String ) : Boolean =
    {
      jwtauthSignatureMethod.toLowerCase == "hmac-sha256" ||
      jwtauthSignatureMethod.toLowerCase == "hs256"
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

    //supported signature method
    //else if (!supportedSignatureMethod(parameters.get("signature_method").get))
    //{
    //  message = "Unsupported signature method, please use hmac-sha1 or hmac-sha256"
    //  httpCode = 400
    //}
    //check if the application is registered and active
    //else if(! registeredApplication(parameters.get("consumer_key").get))
    //{
    //  logger.error("application: " + parameters.get("consumer_key").get + " not found")
    //  message = "Invalid consumer credentials"
    //  httpCode = 401
    //}
    //valid timestamp
    //else if(! wrongTimestamp(parameters.get("timestamp")).isEmpty)
    //{
    //  message = wrongTimestamp(parameters.get("timestamp")).get
    //  httpCode = 400
    //}

    //In the case jwtauth authorization token request, check if the token is still valid and the verifier is correct
    else if(requestType=="authorizationToken" && !validToken(parameters.get("token").get))
    {
      message = "Invalid or expired request token: " + parameters.get("token").get
      httpCode = 401
    }
    //In the case protected resource access request, check if the token is still valid
    else if (
        requestType=="protectedResource" &&
      ! validToken2(parameters.get("token").get)
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

  private def generateTokenAndSecret() =
  {
    // generate some random strings
    val token_message = Helpers.randomString(40)
    val secret_message = Helpers.randomString(40)

    (token_message, secret_message)
  }

  private def saveRequestToken(jwtauthParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.{Token, TokenType}

    //val nonce = Nonce.create
    //nonce.consumerkey(jwtauthParameters.get("consumer_key").get)
    //nonce.timestamp(new Date(jwtauthParameters.get("timestamp").get.toLong))
    //nonce.value(jwtauthParameters.get("nonce").get)
    //val nonceSaved = nonce.save()

    val token = Token.create
    token.tokenType(TokenType.Request)
    //if(! jwtauthParameters.get("consumer_key").get.isEmpty)
    //  Consumer.find(By(Consumer.key,jwtauthParameters.get("consumer_key").get)) match {
    //    case Full(consumer) => token.consumerId(consumer.id)
    //    case _ => None
    //}
    token.key(tokenKey)
    token.secret(tokenSecret)
    //if(! jwtauthParameters.get("callback").get.isEmpty)
    //  token.callbackURL(URLDecoder.decode(jwtauthParameters.get("callback").get,"UTF-8"))
    //else
      token.callbackURL("oob")
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.minutes(30)
    token.duration(tokenDuration)
    token.expirationDate(new Date(currentTime+tokenDuration))
    token.insertDate(new Date(currentTime))
    val tokenSaved = token.save()

    // nonceSaved && tokenSaved
    true && tokenSaved
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
    Token.find(By(Token.key, jwtauthParameters.get("token").get)) match {
      case Full(requestToken) => token.userForeignKey(requestToken.userForeignKey)
      case _ => None
    }
    token.key(tokenKey)
    token.secret(tokenSecret)
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.weeks(4)
    token.duration(tokenDuration)
    token.expirationDate(new Date(currentTime+tokenDuration))
    token.insertDate(new Date(currentTime))
    val tokenSaved = token.save()

    //nonceSaved &&
    tokenSaved
  }

  def getUser : Box[User] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, jwtauthParameters) = validator("protectedResource", httpMethod)

    if(httpCode== 200) getUser(httpCode, jwtauthParameters.get("token"))
    else ParamFailure(message, Empty, Empty, APIFailure(message, httpCode))
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