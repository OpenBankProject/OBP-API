/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd.

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
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.Req
import net.liftweb.http.PostRequest
import net.liftweb.common.Box
import net.liftweb.http.InMemoryResponse
import net.liftweb.common.{Empty, Full, Loggable}
import net.liftweb.http.S
import code.model.{Consumer, Nonce, Token}
import net.liftweb.mapper.By
import java.util.Date
import java.net.{URLDecoder, URLEncoder}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

import net.liftweb.util.Helpers

import scala.compat.Platform
import Helpers._
import code.api.util.APIUtil
import net.liftweb.util.Props
import code.model.TokenType
import code.model.User
import net.liftweb.util.Helpers.tryo
import net.liftweb.common.ParamFailure

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object OAuthHandshake extends RestHelper with Loggable {
  serve
  {
    //Handling get request for a "request token"
    case Req("oauth" :: "initiate" :: Nil,_ , PostRequest) =>
    {
      //Extract the OAuth parameters from the header and test if the request is valid
      var (httpCode, message, oAuthParameters) = validator("requestToken", "POST")
      //Test if the request is valid
      if(httpCode==200)
      {
        //Generate the token and secret
        val (token,secret) = generateTokenAndSecret()
        //Save the token that we have generated
        if(saveRequestToken(oAuthParameters,token, secret))
          message="oauth_token="+token+"&oauth_token_secret="+
            secret+"&oauth_callback_confirmed=true"
      }
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
        //return an HTTP response
      Full(InMemoryResponse(message.getBytes,headers,Nil,httpCode))
    }

    case Req("oauth" :: "token" :: Nil,_, PostRequest) =>
    {
      //Extract the OAuth parameters from the header and test if the request is valid
      var (httpCode, message, oAuthParameters) = validator("authorizationToken", "POST")
      //Test if the request is valid
      if(httpCode==200)
      {
        //Generate the token and secret
        val (token,secret) = generateTokenAndSecret()
        //Save the token that we have generated
        if(saveAuthorizationToken(oAuthParameters,token, secret))
        //remove the request token so the application could not exchange it
        //again to get an other access token
        Token.find(By(Token.key,oAuthParameters.get("oauth_token").get)) match {
            case Full(requestToken) => requestToken.delete_!
            case _ => None
          }

        message="oauth_token="+token+"&oauth_token_secret="+secret
      }
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
      //return an HTTP response
      Full(InMemoryResponse(message.getBytes,headers,Nil,httpCode))
    }
  }

  //Check if the request (access token or request token) is valid and return a tuple
  def validator(requestType : String, httpMethod : String) : (Int, String, Map[String,String]) = {
    //return a Map containing the OAuth parameters : oauth_prameter -> value
    def getAllParameters : Map[String,String]= {

      //Convert the string containing the list of OAuth parameters to a Map
      def toMap(parametersList : String) = {
        //transform the string "oauth_prameter="value""
        //to a tuple (oauth_parameter,Decoded(value))
        def dynamicListExtract(input: String)  = {
          val oauthPossibleParameters =
            List(
              "oauth_consumer_key",
              "oauth_nonce",
              "oauth_signature_method",
              "oauth_timestamp",
              "oauth_version",
              "oauth_signature",
              "oauth_callback",
              "oauth_token",
              "oauth_verifier"
            )

          if (input contains "=") {
            val split = input.split("=",2)
            val parameterValue = split(1).replace("\"","")
            //add only OAuth parameters and not empty
            if(oauthPossibleParameters.contains(split(0)) && ! parameterValue.isEmpty)
              Some(split(0),parameterValue)  // return key , value
            else
              None
          }
          else
            None
        }
        //we delete the "Oauth" prefix and all the white spaces that may exist in the string
        val cleanedParameterList = parametersList.stripPrefix("OAuth").replaceAll("\\s","")
        Map(cleanedParameterList.split(",").flatMap(dynamicListExtract _): _*)
      }

      S.request match {
          case Full(a) =>  a.header("Authorization") match {
            case Full(parameters) => toMap(parameters)
            case _ => Map(("",""))
          }
          case _ => Map(("",""))
      }
    }
    //return true if the authorization header has a duplicated parameter
    def duplicatedParameters = {
      var output=false
      val authorizationParameters = S.request.get.header("Authorization").get.split(",")

      //count the iterations of a parameter in the authorization header
      def countPram(parameterName : String, parametersArray :Array[String] )={
        var i = 0
        parametersArray.foreach(t => {if (t.split("=")(0) == parameterName) i+=1})
        i
      }

      //return true if on of the Authorization header parameter is present more than one time
      authorizationParameters.foreach(
        t => {
          if(countPram(t.split("=")(0),authorizationParameters)>1 && !output)
            output=true
        }
      )
      output
    }

    def supportedOAuthVersion(OAuthVersion : Option[String]) : Boolean = {
      //auth_version is OPTIONAL.  If present, MUST be set to "1.0".
      OAuthVersion match
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
                    Some("oauth_timestamp value: " + timestamp +" is in and invalid time range")
                  else
                    None
                }
                case _ => Some("oauth_timestamp value: " + timestamp +" is an invalid date")
              }
            case _ => Some("oauth_timestamp value: " + timestamp +" is invalid")
          }

        }
        case _ => Some("the following parameter is missing: oauth_timestamp")
      }
    }

    def alreadyUsedNonce(parameters : Map[String, String]) : Boolean = {
      /*
      * The nonce value MUST be unique across all requests with the
      * same timestamp, client credentials, and token combinations.
      */
      val token = parameters.get("oauth_token") getOrElse ""

      Nonce.count(
        By(Nonce.value,parameters.get("oauth_nonce").get),
        By(Nonce.tokenKey, token),
        By(Nonce.consumerkey,parameters.get("oauth_consumer_key").get),
        By(Nonce.timestamp, new Date(parameters.get("oauth_timestamp").get.toLong))
       ) !=0
    }

    def correctSignature(OAuthparameters : Map[String, String], httpMethod : String) = {
      //Normalize an encode the request parameters as explained in Section 3.4.1.3.2
      //of OAuth 1.0 specification (http://tools.ietf.org/html/rfc5849)
      def generateOAuthParametersString(OAuthparameters : Map[String, String]) : String = {
        def sortParam( keyAndValue1 : (String, String), keyAndValue2 : (String, String))= keyAndValue1._1.compareTo(keyAndValue2._1) < 0
        var parameters =""

        //sort the parameters by name
        OAuthparameters.toList.sortWith(sortParam _).foreach(
          t =>
            if(t._1 != "oauth_signature")
              parameters += URLEncoder.encode(t._1,"UTF-8")+"%3D"+ URLEncoder.encode(t._2,"UTF-8")+"%26"

        )
        parameters = parameters.dropRight(3) //remove the "&" encoded sign
        parameters
      }

      //prepare the base string (should we really have openOr here?)
      var baseString = httpMethod+"&"+URLEncoder.encode(Props.get("hostname").openOr(S.hostAndPath)  + S.uri,"UTF-8")+"&"
      baseString+= generateOAuthParametersString(OAuthparameters)

      val encodeBaseString = URLEncoder.encode(baseString,"UTF-8")
      //get the key to sign
      val comsumer = Consumer.find(
          By(Consumer.key,OAuthparameters.get("oauth_consumer_key").get)
        ).get
      var secret= comsumer.secret.toString

      OAuthparameters.get("oauth_token") match {
        case Some(tokenKey) => Token.find(By(Token.key,tokenKey)) match {
            case Full(token) => secret+= "&" +token.secret.toString()
            case _ => secret+= "&"
          }
        case _ => secret+= "&"
      }
      logger.info("base string: " + baseString)
      //signing process
      val signingAlgorithm : String = if(OAuthparameters.get("oauth_signature_method").get.toLowerCase == "hmac-sha256")
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
      //logger.info("received signature:" + OAuthparameters.get("oauth_signature").get)
      logger.info("received signature after decoding: " + URLDecoder.decode(OAuthparameters.get("oauth_signature").get))

      calculatedSignature== URLDecoder.decode(OAuthparameters.get("oauth_signature").get,"UTF-8")
    }

    //check if the token exists and is still valid
    def validToken(tokenKey : String, verifier : String) ={
      Token.find(By(Token.key, tokenKey),By(Token.tokenType,TokenType.Request)) match {
        case Full(token) =>
          token.isValid && token.verifier == verifier
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
    def missingOAuthParameters(parameters : Map[String, String], requestType : String) : Set[String] = {
      val parametersBase =
        List(
          "oauth_consumer_key",
          "oauth_nonce",
          "oauth_signature_method",
          "oauth_timestamp",
          "oauth_signature"
        )
      if(requestType == "requestToken")
        ("oauth_callback" :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="authorizationToken")
        ("oauth_token" :: "oauth_verifier" :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="protectedResource")
        ("oauth_token" :: parametersBase).toSet diff parameters.keySet
      else
        parameters.keySet
    }

    def supportedSignatureMethod(oauthSignatureMethod : String ) : Boolean =
    {
      oauthSignatureMethod.toLowerCase == "hmac-sha256" ||
      oauthSignatureMethod.toLowerCase == "hmac-sha1"
    }

    var message = ""
    var httpCode : Int = 500

    var parameters = getAllParameters

    //are all the necessary OAuth parameters present?
    val missingParams = missingOAuthParameters(parameters,requestType)
    if( missingParams.size != 0 )
    {
      message = "the following parameters are missing : " + missingParams.mkString(", ")
      httpCode = 400
    }
    //no parameter exists more than one times
    else if (duplicatedParameters)
    {
      message = "Duplicated oauth protocol parameters"
      httpCode = 400
    }
    //valid OAuth
    else if(!supportedOAuthVersion(parameters.get("oauth_version")))
    {
      message = "OAuth version not supported"
      httpCode = 400
    }
    //supported signature method
    else if (!supportedSignatureMethod(parameters.get("oauth_signature_method").get))
    {
      message = "Unsupported signature method, please use hmac-sha1 or hmac-sha256"
      httpCode = 400
    }
    //check if the application is registered and active
    else if(! APIUtil.registeredApplication(parameters.get("oauth_consumer_key").get))
    {
      logger.error("application: " + parameters.get("oauth_consumer_key").get + " not found")
      message = "Invalid consumer credentials"
      httpCode = 401
    }
    //valid timestamp
    else if(! wrongTimestamp(parameters.get("oauth_timestamp")).isEmpty)
    {
      message = wrongTimestamp(parameters.get("oauth_timestamp")).get
      httpCode = 400
    }
    //unused nonce
    else if (alreadyUsedNonce(parameters))
    {
      message = "Nonce already used"
      httpCode = 401
    }
    //In the case OAuth authorization token request, check if the token is still valid and the verifier is correct
    else if(requestType=="authorizationToken" && !validToken(parameters.get("oauth_token").get, parameters.get("oauth_verifier").get))
    {
      message = "Invalid or expired request token: " + parameters.get("oauth_token").get
      httpCode = 401
    }
    //In the case protected resource access request, check if the token is still valid
    else if (
        requestType=="protectedResource" &&
      ! validToken2(parameters.get("oauth_token").get)
    )
    {
      message = "Invalid or expired access token: " + parameters.get("oauth_token").get
      httpCode = 401
    }
    //checking if the signature is correct
    else if(! correctSignature(parameters, httpMethod))
    {
      message = "Invalid signature"
      httpCode = 401
    }
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

  private def saveRequestToken(oAuthParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.{Nonce, Token, TokenType}

    val nonce = Nonce.create
    nonce.consumerkey(oAuthParameters.get("oauth_consumer_key").get)
    nonce.timestamp(new Date(oAuthParameters.get("oauth_timestamp").get.toLong))
    nonce.value(oAuthParameters.get("oauth_nonce").get)
    val nonceSaved = nonce.save()

    val token = Token.create
    token.tokenType(TokenType.Request)
    Consumer.find(By(Consumer.key,oAuthParameters.get("oauth_consumer_key").get)) match {
      case Full(consumer) => token.consumerId(consumer.id)
      case _ => None
    }
    token.key(tokenKey)
    token.secret(tokenSecret)
    if(! oAuthParameters.get("oauth_callback").get.isEmpty)
      token.callbackURL(URLDecoder.decode(oAuthParameters.get("oauth_callback").get,"UTF-8"))
    else
      token.callbackURL("oob")
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.minutes(30)
    token.duration(tokenDuration)
    token.expirationDate(new Date(currentTime+tokenDuration))
    token.insertDate(new Date(currentTime))
    val tokenSaved = token.save()

    nonceSaved && tokenSaved
  }

  private def saveAuthorizationToken(oAuthParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.{Nonce, Token, TokenType}

    val nonce = Nonce.create
    nonce.consumerkey(oAuthParameters.get("oauth_consumer_key").get)
    nonce.timestamp(new Date(oAuthParameters.get("oauth_timestamp").get.toLong))
    nonce.tokenKey(oAuthParameters.get("oauth_token").get)
    nonce.value(oAuthParameters.get("oauth_nonce").get)
    val nonceSaved = nonce.save()

    val token = Token.create
    token.tokenType(TokenType.Access)
    Consumer.find(By(Consumer.key,oAuthParameters.get("oauth_consumer_key").get)) match {
      case Full(consumer) => token.consumerId(consumer.id)
      case _ => None
    }
    Token.find(By(Token.key, oAuthParameters.get("oauth_token").get)) match {
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

    nonceSaved && tokenSaved
  }

  def getConsumer: List[Consumer] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
    import code.model.Token
    val consumer: Option[Consumer] = for {
      tokenId: String <- oAuthParameters.get("oauth_token")
      token: Token <- Token.find(By(Token.key, tokenId))
      consumer: Consumer <- token.consumerId.foreign
    } yield {
      consumer
    }
    consumer.toList
  }


  def getUser : Box[User] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)

    if(httpCode== 200) getUser(httpCode, oAuthParameters.get("oauth_token"))
    else ParamFailure(message, Empty, Empty, APIFailure(message, httpCode))
  }

  def getUser(httpCode : Int, tokenID : Box[String]) : Box[User] =
    if(httpCode==200)
    {
      import code.model.Token
      logger.info("OAuth header correct ")
      Token.find(By(Token.key, tokenID.get)) match {
        case Full(token) => {
          logger.info("access token: "+ token + " found")
          val user = token.user
          //just a log
          user match {
            case Full(u) => logger.info("user " + u.name + " was found from the oauth token")
            case _ => logger.info("no user was found for the oauth token")
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