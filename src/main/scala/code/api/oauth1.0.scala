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

import java.net.{URLDecoder, URLEncoder}
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import code.api.Constant._
import code.api.util.{APIUtil, ErrorMessages}
import code.consumer.Consumers
import code.model.dataAccess.ResourceUserCaseClass
import code.model.{Consumer, TokenType, User}
import code.nonce.Nonces
import code.token.Tokens
import code.users.Users
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{InMemoryResponse, PostRequest, Req, S}
import net.liftweb.util.{Helpers, Props}
import net.liftweb.util.Helpers.{tryo, _}
import code.util.Helper.MdcLoggable

import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
* This object provides the API calls necessary to third party applications
* so they could authenticate their users.
*/

object OAuthHandshake extends RestHelper with MdcLoggable {

  /** Get the current app CONSUMER_KEY, it is used to get the redirectURL from CONSUMER table by CONSUMER_KEY. */
  var currentAppConsumerKey = ""
  
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
         Tokens.tokens.vend.getTokenByKey(oAuthParameters.get("oauth_token").get) match {
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
      val authorizationParameters = S.request.openOrThrowException("Attempted to open an empty Box.").header("Authorization").openOrThrowException("Attempted to open an empty Box.").split(",")

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
      Nonces.nonces.vend.countNonces(consumerKey = parameters.get("oauth_consumer_key").get,
        tokenKey = token,
        timestamp = new Date(parameters.get("oauth_timestamp").get.toLong),
        value = parameters.get("oauth_nonce").get
      ) !=0
    }

    def correctSignature(OAuthparameters : Map[String, String], httpMethod : String) = {
      //Normalize an encode the request parameters as explained in Section 3.4.1.3.2
      //of OAuth 1.0 specification (http://tools.ietf.org/html/rfc5849)
      def generateOAuthParametersString(OAuthparameters : List[(String, String)]) : String = {
        def sortParam( keyAndValue1 : (String, String), keyAndValue2 : (String, String))= keyAndValue1._1.compareTo(keyAndValue2._1) < 0
        var parameters =""

        //sort the parameters by name
        OAuthparameters.sortWith(sortParam _).foreach(
          t =>
            if(t._1 != "oauth_signature")
              parameters += URLEncoder.encode(t._1,"UTF-8")+"%3D"+ URLEncoder.encode(t._2,"UTF-8")+"%26"

        )
        parameters = parameters.dropRight(3) //remove the "&" encoded sign
        parameters
      }
      /**
        * This function gets parameters in form Map[String, List[String]] and transform they into a List[(String, String)]
        * i.e. (username -> List(Jon), roles -> (manager, admin)) becomes ((username, Jon), (roles, admin), (roles, manager))
        * @return the Url parameters as list of Tuples
        */

      def urlParameters(): List[(String, String)] = {
        val mapOfParams: Map[String, List[String]] = for ((k, l) <- S.request.map(_.params).getOrElse(Map.empty)) yield (k, l.sortWith(_ < _))
        val listOfTuples: List[(String, String)] = for {(k, l) <- mapOfParams.toList
                                                        v <- l}
          yield {
            (k, v)
          }
        listOfTuples
      }
      //prepare the base string
      var baseString = httpMethod+"&"+URLEncoder.encode(HostName + S.uri ,"UTF-8")+"&"
      // Add OAuth and URL parameters to the base string
      // Parameters are provided as List[(String, String)]
      baseString+= generateOAuthParametersString((OAuthparameters.toList ::: urlParameters()))

      val encodeBaseString = URLEncoder.encode(baseString,"UTF-8")
      //get the key to sign
      val consumer = Consumers.consumers.vend.getConsumerByConsumerKey(OAuthparameters.get("oauth_consumer_key").get).openOrThrowException("Attempted to open an empty Box.")
      var secret= consumer.secret.toString

      OAuthparameters.get("oauth_token") match {
        case Some(tokenKey) => Tokens.tokens.vend.getTokenByKey(tokenKey) match {
            case Full(token) => secret+= "&" +token.secret.toString()
            case _ => secret+= "&"
          }
        case _ => secret+= "&"
      }
      //logger.debug("base string: " + baseString)
      //signing process // TODO default to HmacSHA256?
      val signingAlgorithm : String = if(OAuthparameters.get("oauth_signature_method").get.toLowerCase == "hmac-sha256")
        "HmacSHA256"
      else
        "HmacSHA1"

      //logger.debug("signing method: " + signingAlgorithm)
      //logger.debug("signing key: " + secret)
      //logger.debug("signing key in bytes: " + secret.getBytes("UTF-8"))

      var m = Mac.getInstance(signingAlgorithm);
      m.init(new SecretKeySpec(secret.getBytes("UTF-8"),signingAlgorithm))
      val calculatedSignature = Helpers.base64Encode(m.doFinal(baseString.getBytes))

      //logger.debug("calculatedSignature: " + calculatedSignature)
      //logger.debug("received signature:" + OAuthparameters.get("oauth_signature").get)
      //logger.debug("received signature after decoding: " + URLDecoder.decode(OAuthparameters.get("oauth_signature").get))

      calculatedSignature== URLDecoder.decode(OAuthparameters.get("oauth_signature").get,"UTF-8")
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
    //TODO store the consumer key in appConsumerKey variable, may be fixed latter.
    currentAppConsumerKey=getAllParameters.get("oauth_consumer_key").getOrElse("")

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
      message = ErrorMessages.InvalidConsumerCredentials
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
    def duplicatedParameters(req1: Box[Req]) = {
      println("duplicatedParameters 1")
      var output=false
      val authorizationParameters = req1.openOrThrowException("Attempted to open an empty Box.").header("Authorization").openOrThrowException("Attempted to open an empty Box.").split(",")
      println("duplicatedParameters 2")
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
      val token = parameters.get("oauth_token") getOrElse ""
      for {
        cnt <- Nonces.nonces.vend.countNoncesFuture(consumerKey = parameters.get("oauth_consumer_key").get,
                                                    tokenKey = token,
                                                    timestamp = new Date(parameters.get("oauth_timestamp").get.toLong),
                                                    value = parameters.get("oauth_nonce").get
                                                    )
      } yield {
        cnt != 0
      }
    }

    def correctSignature(OAuthparameters : Map[String, String], httpMethod : String, req: Box[Req], sUri: String) = {
      //Normalize an encode the request parameters as explained in Section 3.4.1.3.2
      //of OAuth 1.0 specification (http://tools.ietf.org/html/rfc5849)
      def generateOAuthParametersString(OAuthparameters : List[(String, String)]) : String = {
        def sortParam( keyAndValue1 : (String, String), keyAndValue2 : (String, String))= keyAndValue1._1.compareTo(keyAndValue2._1) < 0
        var parameters =""

        //sort the parameters by name
        OAuthparameters.sortWith(sortParam _).foreach(
          t =>
            if(t._1 != "oauth_signature")
              parameters += URLEncoder.encode(t._1,"UTF-8")+"%3D"+ URLEncoder.encode(t._2,"UTF-8")+"%26"

        )
        parameters = parameters.dropRight(3) //remove the "&" encoded sign
        parameters
      }
      /**
        * This function gets parameters in form Map[String, List[String]] and transform they into a List[(String, String)]
        * i.e. (username -> List(Jon), roles -> (manager, admin)) becomes ((username, Jon), (roles, admin), (roles, manager))
        * @return the Url parameters as list of Tuples
        */

      def urlParameters(): List[(String, String)] = {
        val mapOfParams: Map[String, List[String]] = for ((k, l) <- req.map(_.params).getOrElse(Map.empty)) yield (k, l.sortWith(_ < _))
        val listOfTuples: List[(String, String)] = for {(k, l) <- mapOfParams.toList
                                                        v <- l}
          yield {
            (k, v)
          }
        listOfTuples
      }
      //prepare the base string
      var baseString = httpMethod+"&"+URLEncoder.encode(HostName + sUri ,"UTF-8")+"&"
      // Add OAuth and URL parameters to the base string
      // Parameters are provided as List[(String, String)]
      baseString+= generateOAuthParametersString((OAuthparameters.toList ::: urlParameters()))

      val encodeBaseString = URLEncoder.encode(baseString,"UTF-8")
      //get the key to sign
      val consumer = Consumers.consumers.vend.getConsumerByConsumerKey(OAuthparameters.get("oauth_consumer_key").get).openOrThrowException("Attempted to open an empty Box.")
      var secret= consumer.secret.toString

      OAuthparameters.get("oauth_token") match {
        case Some(tokenKey) => Tokens.tokens.vend.getTokenByKey(tokenKey) match {
          case Full(token) => secret+= "&" +token.secret.toString()
          case _ => secret+= "&"
        }
        case _ => secret+= "&"
      }
      //logger.debug("base string: " + baseString)
      //signing process // TODO default to HmacSHA256?
      val signingAlgorithm : String = if(OAuthparameters.get("oauth_signature_method").get.toLowerCase == "hmac-sha256")
        "HmacSHA256"
      else
        "HmacSHA1"

      //logger.debug("signing method: " + signingAlgorithm)
      //logger.debug("signing key: " + secret)
      //logger.debug("signing key in bytes: " + secret.getBytes("UTF-8"))

      var m = Mac.getInstance(signingAlgorithm);
      m.init(new SecretKeySpec(secret.getBytes("UTF-8"),signingAlgorithm))
      val calculatedSignature = Helpers.base64Encode(m.doFinal(baseString.getBytes))

      //logger.debug("calculatedSignature: " + calculatedSignature)
      //logger.debug("received signature:" + OAuthparameters.get("oauth_signature").get)
      //logger.debug("received signature after decoding: " + URLDecoder.decode(OAuthparameters.get("oauth_signature").get))

      calculatedSignature== URLDecoder.decode(OAuthparameters.get("oauth_signature").get,"UTF-8")
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
    //TODO store the consumer key in appConsumerKey variable, may be fixed latter.
    currentAppConsumerKey=getAllParameters.get("oauth_consumer_key").getOrElse("")


    val alreadyUsedNonceF = alreadyUsedNonceFuture(parameters)
    val validToken2F = {
      if (requestType == "protectedResource") {
        validToken2Future(parameters.get("oauth_token").get)
      } else {
        Future{true}
      }
    }
    val validTokenF = {
      if (requestType == "authorizationToken") {
        validTokenFuture(parameters.get("oauth_token").get, parameters.get("oauth_verifier").get)
      } else {
        Future{true}
      }
    }
    val registeredApplicationF = APIUtil.registeredApplicationFuture(parameters.get("oauth_consumer_key").get)
    val sRequest = S.request
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
      else if(!registeredApplication)
      {
        logger.error("application: " + parameters.get("oauth_consumer_key").get + " not found")
        message = ErrorMessages.InvalidConsumerCredentials
        httpCode = 401
      }
      //valid timestamp
      else if(! wrongTimestamp(parameters.get("oauth_timestamp")).isEmpty)
      {
        message = wrongTimestamp(parameters.get("oauth_timestamp")).get
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
        message = "Invalid or expired request token: " + parameters.get("oauth_token").get
        httpCode = 401
      }
      //In the case protected resource access request, check if the token is still valid
      else if (!validToken2)
      {
        message = "Invalid or expired access token: " + parameters.get("oauth_token").get
        httpCode = 401
      }
      //checking if the signature is correct
      else if(! correctSignature(parameters, httpMethod, sRequest, sUri))
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
      timestamp = Some(new Date(oAuthParameters.get("oauth_timestamp").get.toLong)),
      value = Some(oAuthParameters.get("oauth_nonce").get)
    ) match {
      case Full(_) => true
      case _ => false
    }

    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(oAuthParameters.get("oauth_consumer_key").get) match {
      case Full(consumer) => Some(consumer.id.get)
      case _ => None
    }
    val callbackURL =
      if(! oAuthParameters.get("oauth_callback").get.isEmpty)
        URLDecoder.decode(oAuthParameters.get("oauth_callback").get,"UTF-8")
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
      tokenKey = Some(oAuthParameters.get("oauth_token").get),
      timestamp = Some(new Date(oAuthParameters.get("oauth_timestamp").get.toLong)),
      value = Some(oAuthParameters.get("oauth_nonce").get)
    ) match {
      case Full(_) => true
      case _ => false
    }

    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(oAuthParameters.get("oauth_consumer_key").get) match {
      case Full(consumer) => Some(consumer.id.get)
      case _ => None
    }
    val userId = Tokens.tokens.vend.getTokenByKey(oAuthParameters.get("oauth_token").get) match {
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
      tokenId: String <- oAuthParameters.get("oauth_token")
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

    if(httpCode== 200) getUser(httpCode, oAuthParameters.get("oauth_token"))
    else ParamFailure(message, Empty, Empty, APIFailure(message, httpCode))
  }

  def getUser(httpCode : Int, tokenID : Box[String]) : Box[User] =
    if(httpCode==200)
    {
      //logger.debug("OAuth header correct ")
      Tokens.tokens.vend.getTokenByKey(tokenID.openOrThrowException("Attempted to open an empty Box.")) match {
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

  def getUserFromOAuthHeaderFuture(): Future[Box[User]] = {
    val httpMethod = S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }
    for {
      (httpCode, message, oAuthParameters) <- validatorFuture("protectedResource", httpMethod)
      _ <- Future { if (httpCode == 200) Full("ok") else Empty } map { x => APIUtil.fullBoxOrException(x ?~! message) }
      user <- getUserFromTokenFuture(httpCode, oAuthParameters.get("oauth_token"))
    } yield {
      user
    }
  }
  def getUserFromTokenFuture(httpCode : Int, key: Box[String]) : Future[Box[User]] = {
    httpCode match {
      case 200 =>
        for {
          c <- Tokens.tokens.vend.getTokenByKeyFuture(key.openOrThrowException("Attempted to open an empty Box.")) map (_.map(_.userForeignKey.get))
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
}