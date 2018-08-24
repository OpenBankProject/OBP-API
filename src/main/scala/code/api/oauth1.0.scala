/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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

import java.net.{URLDecoder, URLEncoder}
import java.util.Date

import code.api.Constant._
import code.api.oauth1a.Arithmetics
import code.api.oauth1a.OauthParams._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext, ErrorMessages}
import code.consumer.Consumers
import code.model.{Consumer, TokenType, User}
import code.nonce.Nonces
import code.token.Tokens
import code.users.Users
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{InMemoryResponse, PostRequest, Req, S}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

import scala.compat.Platform
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object OAuthHandshake extends RestHelper with MdcLoggable {

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
         Tokens.tokens.vend.getTokenByKey(oAuthParameters.get(TokenName).get) match {
            case Full(requestToken) => Tokens.tokens.vend.deleteToken(requestToken.id.get) match {
              case true  =>
              case false => logger.warn("Request token: " + requestToken + " is not deleted. The application could exchange it again to get an other access token!")
            }
            case _ => logger.warn("Request token is not deleted due to absence in database!")
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
              NonceName,
              SignatureMethodName,
              TimestampName,
              VersionName,
              SignatureName,
              CallbackName,
              TokenName,
              VerifierName
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
      val authorizationParameters = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).header("Authorization").openOrThrowException(attemptedToOpenAnEmptyBox).split(",")

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
      val token = parameters.get(TokenName) getOrElse ""
      Nonces.nonces.vend.countNonces(consumerKey = parameters.get("oauth_consumer_key").get,
        tokenKey = token,
        timestamp = new Date(parameters.get(TimestampName).get.toLong),
        value = parameters.get(NonceName).get
      ) !=0
    }



    //check if the token exists and is still valid
    def validToken(tokenKey : String, verifier : String) ={
      Tokens.tokens.vend.getTokenByKeyAndType(tokenKey, TokenType.Request) match {
        case Full(token) =>
          token.isValid && token.verifier == verifier
        case _ => false
      }
    }

    def validToken2(tokenKey : String) = {
      Tokens.tokens.vend.getTokenByKeyAndType(tokenKey, TokenType.Access) match {
        case Full(token) => token.isValid
          case _ => false
      }
    }

    //@return the missing parameters depending of the request type
    def missingOAuthParameters(parameters : Map[String, String], requestType : String) : Set[String] = {
      val parametersBase =
        List(
          "oauth_consumer_key",
          NonceName,
          SignatureMethodName,
          TimestampName,
          SignatureName
        )
      if(requestType == "requestToken")
        (CallbackName :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="authorizationToken")
        (TokenName :: VerifierName :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="protectedResource")
        (TokenName :: parametersBase).toSet diff parameters.keySet
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

    val sRequest = S.request
    val urlParams: Map[String, List[String]] = sRequest.map(_.params).getOrElse(Map.empty)
    val sUri = S.uri

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
    else if(!supportedOAuthVersion(parameters.get(VersionName)))
    {
      message = "OAuth version not supported"
      httpCode = 400
    }
    //supported signature method
    else if (!supportedSignatureMethod(parameters.get(SignatureMethodName).get))
    {
      message = "Unsupported signature method, please use hmac-sha1 or hmac-sha256"
      httpCode = 400
    }
    //check if the application is registered and active
    else if(! APIUtil.registeredApplication(parameters.get("oauth_consumer_key").get))
    {
      logger.error("application: " + parameters.get("oauth_consumer_key").get + " not found")
      message = ErrorMessages.InvalidConsumerCredentials
      httpCode = 401
    }
    //valid timestamp
    else if(! wrongTimestamp(parameters.get(TimestampName)).isEmpty)
    {
      message = wrongTimestamp(parameters.get(TimestampName)).get
      httpCode = 400
    }
    //unused nonce
    else if (alreadyUsedNonce(parameters))
    {
      message = "Nonce already used"
      httpCode = 401
    }
    //In the case OAuth authorization token request, check if the token is still valid and the verifier is correct
    else if(requestType=="authorizationToken" && !validToken(parameters.get(TokenName).get, parameters.get(VerifierName).get))
    {
      message = "Invalid or expired request token: " + parameters.get(TokenName).get
      httpCode = 401
    }
    //In the case protected resource access request, check if the token is still valid
    else if (
        requestType=="protectedResource" &&
      ! validToken2(parameters.get(TokenName).get)
    )
    {
      message = "Invalid or expired access token: " + parameters.get(TokenName).get
      httpCode = 401
    }
    //checking if the signature is correct
    else if(! verifySignature(parameters, httpMethod, urlParams, sUri))
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



  //Check if the request (access token or request token) is valid and return a tuple
  def validatorFuture(requestType : String, httpMethod : String) : Future[(Int, String, Map[String,String])] = {
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
              NonceName,
              SignatureMethodName,
              TimestampName,
              VersionName,
              SignatureName,
              CallbackName,
              TokenName,
              VerifierName
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
    def duplicatedParameters(req1: Box[Req]) = {
      logger.debug("duplicatedParameters 1")
      var output=false
      val authorizationParameters = req1.openOrThrowException(attemptedToOpenAnEmptyBox).header("Authorization").openOrThrowException(attemptedToOpenAnEmptyBox).split(",")
      logger.debug("duplicatedParameters 2")
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

    def alreadyUsedNonceFuture(parameters : Map[String, String]) : Future[Boolean] = {
      /*
      * The nonce value MUST be unique across all requests with the
      * same timestamp, client credentials, and token combinations.
      */
      val token = parameters.get(TokenName) getOrElse ""
      for {
        cnt <- Nonces.nonces.vend.countNoncesFuture(consumerKey = parameters.get("oauth_consumer_key").get,
                                                    tokenKey = token,
                                                    timestamp = new Date(parameters.get(TimestampName).get.toLong),
                                                    value = parameters.get(NonceName).get
                                                    )
      } yield {
        cnt != 0
      }
    }




    //check if the token exists and is still valid
    def validTokenFuture(tokenKey : String, verifier : String) = {
      Tokens.tokens.vend.getTokenByKeyAndTypeFuture(tokenKey, TokenType.Request) map {
        case Full(token) =>
          token.isValid && token.verifier == verifier
        case _ => false
      }
    }

    def validToken2Future(tokenKey : String) = {
      Tokens.tokens.vend.getTokenByKeyAndTypeFuture(tokenKey, TokenType.Access) map {
        case Full(token) => token.isValid
        case _ => false
      }
    }

    //@return the missing parameters depending of the request type
    def missingOAuthParameters(parameters : Map[String, String], requestType : String) : Set[String] = {
      val parametersBase =
        List(
          "oauth_consumer_key",
          NonceName,
          SignatureMethodName,
          TimestampName,
          SignatureName
        )
      if(requestType == "requestToken")
        (CallbackName :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="authorizationToken")
        (TokenName :: VerifierName :: parametersBase).toSet diff parameters.keySet
      else if(requestType=="protectedResource")
        (TokenName :: parametersBase).toSet diff parameters.keySet
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

    val alreadyUsedNonceF = alreadyUsedNonceFuture(parameters)
    val validToken2F = {
      if (requestType == "protectedResource") {
        validToken2Future(parameters.get(TokenName).get)
      } else {
        Future{true}
      }
    }
    val validTokenF = {
      if (requestType == "authorizationToken") {
        validTokenFuture(parameters.get(TokenName).get, parameters.get(VerifierName).get)
      } else {
        Future{true}
      }
    }
    val registeredApplicationF = APIUtil.registeredApplicationFuture(parameters.get("oauth_consumer_key").get)
    val sRequest = S.request
    val urlParams: Map[String, List[String]] = sRequest.map(_.params).getOrElse(Map.empty)
    val sUri = S.uri

    // Please note that after this point S.request for instance cannot be used directly
    // If you need it later assign it to some variable and pass it
    for {
      alreadyUsedNonce <- alreadyUsedNonceF
      validToken2 <- validToken2F
      validToken <- validTokenF
      registeredApplication <- registeredApplicationF

    } yield {
      //are all the necessary OAuth parameters present?
      val missingParams = missingOAuthParameters(parameters,requestType)
      if(missingParams.size != 0)
      {
        message = "the following parameters are missing : " + missingParams.mkString(", ")
        httpCode = 400
      }
      //no parameter exists more than one times
      else if (duplicatedParameters(sRequest))
      {
        message = "Duplicated oauth protocol parameters"
        httpCode = 400
      }
      //valid OAuth
      else if(!supportedOAuthVersion(parameters.get(VersionName)))
      {
        message = "OAuth version not supported"
        httpCode = 400
      }
      //supported signature method
      else if (!supportedSignatureMethod(parameters.get(SignatureMethodName).get))
      {
        message = "Unsupported signature method, please use hmac-sha1 or hmac-sha256"
        httpCode = 400
      }
      //check if the application is registered and active
      else if(!registeredApplication)
      {
        logger.error("application: " + parameters.get("oauth_consumer_key").get + " not found")
        message = ErrorMessages.InvalidConsumerCredentials
        httpCode = 401
      }
      //valid timestamp
      else if(! wrongTimestamp(parameters.get(TimestampName)).isEmpty)
      {
        message = wrongTimestamp(parameters.get(TimestampName)).get
        httpCode = 400
      }
      //unused nonce
      else if (alreadyUsedNonce)
      {
        message = "Nonce already used"
        httpCode = 401
      }
      //In the case OAuth authorization token request, check if the token is still valid and the verifier is correct
      else if(!validToken)
      {
        message = "Invalid or expired request token: " + parameters.get(TokenName).get
        httpCode = 401
      }
      //In the case protected resource access request, check if the token is still valid
      else if (!validToken2)
      {
        message = "Invalid or expired access token: " + parameters.get(TokenName).get
        httpCode = 401
      }
      //checking if the signature is correct
      else if(! verifySignature(parameters, httpMethod, urlParams, sUri))
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
  }


  private def decodeOAuthParams(OAuthparameters: Map[String, String]) = {
    val decodedOAuthParams = OAuthparameters.toList map {
      t =>
        (
          URLDecoder.decode(t._1, "UTF-8"),
          URLDecoder.decode(t._2, "UTF-8")
        )
    }
    decodedOAuthParams
  }

  /**
    * This function gets parameters in form Map[String, List[String]] and transform they into a List[(String, String)]
    * i.e. (username -> List(Jon), roles -> (manager, admin)) becomes ((username, Jon), (roles, admin), (roles, manager))
    *
    * @return the Url parameters as list of Tuples
    */

  private def urlParameters(urlParams: Map[String, List[String]]): List[(String, String)] = {
    val mapOfParams: Map[String, List[String]] = for ((k, l) <- urlParams) yield (k, l.sortWith(_ < _))
    val listOfTuples: List[(String, String)] =
      for {
        (k, l) <- mapOfParams.toList
        v <- l
      }
        yield {
          (k, v)
        }
    listOfTuples
  }

  private def getConsumerAndTokenSecret(OAuthparameters: Map[String, String]) = {
    val consumer = Consumers.consumers.vend.getConsumerByConsumerKey(OAuthparameters.get("oauth_consumer_key").get).openOrThrowException(attemptedToOpenAnEmptyBox)
    val consumerSecret = consumer.secret.toString

    val tokenSecret = OAuthparameters.get(TokenName) match {
      case Some(tokenKey) => Tokens.tokens.vend.getTokenByKey(tokenKey) match {
        case Full(token) =>
          token.secret.toString()
        case _ => ""
      }
      case _ => ""
    }
    (consumerSecret, tokenSecret)
  }

  /**
    * OAuth 1.0 specification (http://tools.ietf.org/html/rfc5849)
    * Recalculating the request signature independently as described in Section 3.4 and
    * comparing it to the value received from the client via the "oauth_signature" parameter.
    * For example, the HTTP request:
    * POST /request?b5=%3D%253D&a3=a&c%40=&a2=r%20b HTTP/1.1
    * Host: example.com
    * Content-Type: application/x-www-form-urlencoded
    * Authorization: OAuth realm="Example",
    *                oauth_consumer_key="9djdj82h48djs9d2",
    *                oauth_token="kkk9d7dh3k39sjv7",
    *                oauth_signature_method="HMAC-SHA1",
    *                oauth_timestamp="137131201",
    *                oauth_nonce="7d8f3e4a",
    *                oauth_signature="bYT5CMsGcbgUdFHObYMEfcx6bsw%3D"
    *
    * c2&a3=2+q
    * is represented by the following signature base string (line breaks are for display purposes only):
    * POST&http%3A%2F%2Fexample.com%2Frequest&a2%3Dr%2520b%26a3%3D2%2520q
    * %26a3%3Da%26b5%3D%253D%25253D%26c%2540%3D%26c2%3D%26oauth_consumer_
    * key%3D9djdj82h48djs9d2%26oauth_nonce%3D7d8f3e4a%26oauth_signature_m
    * ethod%3DHMAC-SHA1%26oauth_timestamp%3D137131201%26oauth_token%3Dkkk
    * 9d7dh3k39sjv7
    *
    * @param OAuthparameters List of URL encoded OAuth parameters
    * @param httpMethod The HTTP request method in uppercase. For example: "HEAD", "GET", "POST", etc.  If the request uses a custom HTTP method, it MUST be encoded
    * @param urlParams For example: b5=%3D%253D&a3=a&c%40=&a2=r%20b
    * @param sUri For example: http://example.com
    * @return Boolean: True/False
    */
  private def verifySignature(OAuthparameters : Map[String, String], httpMethod : String, urlParams: Map[String, List[String]], sUri: String): Boolean = {
    // Chose signature algorithm according to a value of parameter of OAuth.
    // For example: oauth_signature_method="HMAC-SHA1"
    val signingAlgorithm: String = OAuthparameters.get(SignatureMethodName).get.toLowerCase match {
      case "hmac-sha256" => Arithmetics.HmacSha256Algorithm
      case _             => Arithmetics.HmacSha1Algorithm
    }
    // Find secret of consumer and token by oauth_consumer_key and oauth_token parameters
    // For example:
    // oauth_consumer_key="9djdj82h48djs9d2"
    // oauth_token="kkk9d7dh3k39sjv7"
    val (consumerSecret: String, tokenSecret: String) = getConsumerAndTokenSecret(OAuthparameters)
    // Decode OAuth parameters in order to get original state
    val decodedOAuthParams: List[(String, String)] = decodeOAuthParams(OAuthparameters)
    // Signature Base String Construction
    val signatureBase = Arithmetics.concatItemsForSignature(httpMethod, HostName + sUri, urlParameters(urlParams), Nil, decodedOAuthParams)
    val computedSignature = Arithmetics.sign(signatureBase, consumerSecret, tokenSecret, signingAlgorithm)
    val received: String = OAuthparameters.get(SignatureName).get
    val receivedAndDecoded: String = URLDecoder.decode(OAuthparameters.get(SignatureName).get,"UTF-8")
    val computedAndEncoded: String = URLEncoder.encode(computedSignature, "UTF-8")
    logger.debug("OAuthparameters: " + OAuthparameters)
    logger.debug("Decoded OAuthparameters: " + decodedOAuthParams)
    logger.debug("Signature's base: " + signatureBase)
    logger.debug("Computed signature: " + computedSignature)
    logger.debug("Computed and encoded signature: " + computedAndEncoded)
    logger.debug("Received signature:" + received)
    logger.debug("Received and decoded signature:" + receivedAndDecoded)

    // Please note that received OAuth signature is encoded so we need to encode computed signature as well.
    // For example: oauth_signature="bYT5CMsGcbgUdFHObYMEfcx6bsw%3D"
    computedAndEncoded == received
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
    import code.model.TokenType

    val nonceSaved = Nonces.nonces.vend.createNonce(
      id = None,
      consumerKey = Some(oAuthParameters.get("oauth_consumer_key").get),
      tokenKey = None,
      timestamp = Some(new Date(oAuthParameters.get(TimestampName).get.toLong)),
      value = Some(oAuthParameters.get(NonceName).get)
    ) match {
      case Full(_) => true
      case _ => false
    }

    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(oAuthParameters.get("oauth_consumer_key").get) match {
      case Full(consumer) => Some(consumer.id.get)
      case _ => None
    }
    val callbackURL =
      if(! oAuthParameters.get(CallbackName).get.isEmpty)
        URLDecoder.decode(oAuthParameters.get(CallbackName).get,"UTF-8")
      else
        "oob"
    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.minutes(30)

    val tokenSaved = Tokens.tokens.vend.createToken(TokenType.Request,
      consumerId,
      None,
      Some(tokenKey),
      Some(tokenSecret),
      Some(tokenDuration),
      Some(new Date(currentTime+tokenDuration)),
      Some(new Date(currentTime)),
      Some(callbackURL)
    ) match {
      case Full(_) => true
      case _       => false
    }

    nonceSaved && tokenSaved
  }

  private def saveAuthorizationToken(oAuthParameters : Map[String, String], tokenKey : String, tokenSecret : String) =
  {
    import code.model.TokenType

    val nonceSaved = Nonces.nonces.vend.createNonce(
      id = None,
      consumerKey = Some(oAuthParameters.get("oauth_consumer_key").get),
      tokenKey = Some(oAuthParameters.get(TokenName).get),
      timestamp = Some(new Date(oAuthParameters.get(TimestampName).get.toLong)),
      value = Some(oAuthParameters.get(NonceName).get)
    ) match {
      case Full(_) => true
      case _ => false
    }

    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(oAuthParameters.get("oauth_consumer_key").get) match {
      case Full(consumer) => Some(consumer.id.get)
      case _ => None
    }
    val userId = Tokens.tokens.vend.getTokenByKey(oAuthParameters.get(TokenName).get) match {
      case Full(requestToken) => Some(requestToken.userForeignKey.get)
      case _ => None
    }

    val currentTime = Platform.currentTime
    val tokenDuration : Long = Helpers.weeks(4)

    val tokenSaved = Tokens.tokens.vend.createToken(TokenType.Access,
      consumerId,
      userId,
      Some(tokenKey),
      Some(tokenSecret),
      Some(tokenDuration),
      Some(new Date(currentTime+tokenDuration)),
      Some(new Date(currentTime)),
      None
    ) match {
      case Full(_) => true
      case _       => false
    }

    nonceSaved && tokenSaved
  }

  def getConsumer: Box[Consumer] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)
    import code.model.Token
    val consumer: Option[Consumer] = for {
      tokenId: String <- oAuthParameters.get(TokenName)
      token: Token <- Tokens.tokens.vend.getTokenByKey(tokenId)
      consumer: Consumer <- token.consumer
    } yield {
      consumer
    }
    consumer
  }

  def getConsumer(token: String): Box[Consumer] = {
    import code.model.Token
    val consumer: Option[Consumer] = for {
      tokenId: String <- tryo(token)
      token: Token <- Tokens.tokens.vend.getTokenByKey(tokenId)
      consumer: Consumer <- token.consumer
    } yield {
      consumer
    }
    consumer
  }


  def getUser : Box[User] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)

    if(httpCode== 200) getUser(httpCode, oAuthParameters.get(TokenName))
    else ParamFailure(message, Empty, Empty, APIFailure(message, httpCode))
  }
  def getUserAndCallContext(cc: CallContext) : (Box[User], CallContext)  = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    val (httpCode, message, oAuthParameters) = validator("protectedResource", httpMethod)

    if(httpCode== 200) {
      val oauthToken = oAuthParameters.get(TokenName)
      val consumer = getConsumer(oauthToken.getOrElse(""))
      (getUser(httpCode, oauthToken), cc.copy(oAuthParams = oAuthParameters, consumer = consumer))
    }
    else
      (ParamFailure(message, Empty, Empty, APIFailure(message, httpCode)), cc)
  }

  def getUser(httpCode : Int, tokenID : Box[String]) : Box[User] =
    if(httpCode==200)
    {
      //logger.debug("OAuth header correct ")
      Tokens.tokens.vend.getTokenByKey(tokenID.openOrThrowException(attemptedToOpenAnEmptyBox)) match {
        case Full(token) => {
          //logger.debug("access token: "+ token + " found")
          val user = token.user
          //just a log
          //user match {
          //  case Full(u) => logger.debug("user " + u.name + " was found from the oauth token")
          //  case _ => logger.debug("no user was found for the oauth token")
          //}
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

  def getUserFromOAuthHeaderFuture(sc: CallContext): Future[(Box[User], Option[CallContext])] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    for {
      (httpCode, message, oAuthParameters) <- validatorFuture("protectedResource", httpMethod)
      _ <- Future { if (httpCode == 200) Full("ok") else Empty } map { x => APIUtil.fullBoxOrException(x ?~! message) }
      consumer <- getConsumerFromTokenFuture(httpCode, oAuthParameters.get(TokenName))
      user <- getUserFromTokenFuture(httpCode, oAuthParameters.get(TokenName))
    } yield {
      (user, Some(sc.copy(user = user, oAuthParams = oAuthParameters, consumer=consumer)))
    }
  }
  def getUserFromTokenFuture(httpCode : Int, key: Box[String]) : Future[Box[User]] = {
    httpCode match {
      case 200 =>
        for {
          c: Box[Long] <- Tokens.tokens.vend.getTokenByKeyFuture(key.openOrThrowException(attemptedToOpenAnEmptyBox)) map (_.map(_.userForeignKey.get))
          u <- c match {
            case Full(id) =>
              Users.users.vend.getResourceUserByResourceUserIdFuture(id)
            case _ =>
              Future {Empty}
          }
        } yield {
          u
        }
      case _ =>
        Future {Empty}
    }

  }

  def getConsumerFromTokenFuture(httpCode : Int, key: Box[String]) : Future[Box[Consumer]] = {
    httpCode match {
      case 200 =>
        Tokens.tokens.vend.getTokenByKeyFuture(key.openOrThrowException(attemptedToOpenAnEmptyBox)) map (_.map(_.consumerId.foreign.openOrThrowException(attemptedToOpenAnEmptyBox)))
      case _ =>
        Future {Empty}
    }
  }
}