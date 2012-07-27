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
package code.snippet
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.Req
import net.liftweb.http.GetRequest
import net.liftweb.http.PostRequest
import net.liftweb.http.LiftResponse
import net.liftweb.common.Box
import net.liftweb.http.InMemoryResponse
import net.liftweb.common.{Full,Empty}
import net.liftweb.http.S
import code.model.{Nonce, Consumer, Token}
import net.liftweb.mapper.By
import java.util.Date
import java.net.{URLEncoder, URLDecoder}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import net.liftweb.util.Helpers
import code.model.AppType._
import code.model.TokenType._
import scala.compat.Platform
import code.model.User
import scala.xml.NodeSeq
import net.liftweb.util.Helpers._

object OAuthHandshake extends RestHelper
{
  	serve 
  	{
  		//Handling get request for a "request token"	    
	  	case Req("oauth" :: "initiate" :: Nil,_ ,PostRequest) => 
	  	{
	  		//Extract the OAuth parameters from the header and test if the request is valid
		  	var (httpCode, data, oAuthParameters) = validator("requestToken", "POST")
		  	//Test if the request is valid 
		  	if(httpCode==200)
		  	{
		  		//Generate the token and secret 
		    	val (token,secret) = generateTokenAndSecret(oAuthParameters.get
		    		("oauth_consumer_key").get)
		    	//Save the token that we have generated
		    	if(saveRequestToken(oAuthParameters,token, secret))
		    		data=("oauth_token="+token+"&oauth_token_secret="+
		    			secret+"&oauth_callback_confirmed=true").getBytes()
			}									    		
	      	val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
	      	//return an HTTP response 
	      	Full(InMemoryResponse(data,headers,Nil,httpCode))
	  	}
	  	case Req("oauth" :: "token" :: Nil,_, PostRequest) => 
	  	{
	  		//Extract the OAuth parameters from the header and test if the request is valid
			var (httpCode, data, oAuthParameters) = validator("authorizationToken", "POST")
		  	//Test if the request is valid 
		  	if(httpCode==200)
		  	{
		  		//Generate the token and secret 
		    	val (token,secret) = generateTokenAndSecret(oAuthParameters.get
		    		("oauth_consumer_key").get)
		    	//Save the token that we have generated	
		    	if(saveAuthorizationToken(oAuthParameters,token, secret))
		    		//remove the request token so the application could not exchange it 
		    		//again to get an other access token
			    	Token.find(By(Token.key,oAuthParameters.get("oauth_token").get)) match {
			      		case Full(requestToken) => requestToken.delete_!
			      		case _ => None
			      	}

			    data=("oauth_token="+token+"&oauth_token_secret="+secret).getBytes()
		  	}		      
		  	val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
		  	//return an HTTP response 
	      	Full(InMemoryResponse(data,headers,Nil,httpCode))
	  	}
  	}
  
  	//Check if the request (access toke or request token) is valid and return a tuple  
  	def validator(requestType : String, httpMethod : String) = 
  	{
  		//return a Map containing the OAuth parameters : oauth_prameter -> value 
	    def getAllParameters = 
	    {
			//Convert the string containing the list of OAuth parameters to a Map 
			def toMap(parametersList : String) = 
			{
				//transform the string "oauth_prameter="value"" 
				//to a tuple (oauth_parameter,Decoded(value)) 
				def dynamicListExtract(input: String)  = 
			  	{
					val oauthPossibleParameters = List("oauth_consumer_key","oauth_nonce",
						"oauth_signature_method", "oauth_timestamp","oauth_version", 
						"oauth_signature","oauth_callback", "oauth_token","oauth_verifier")
				    
				    if (input contains "=") {
				    	val split = input.split("=",2)
				      	val parameterValue = URLDecoder.decode(split(1)).replace("\"","")
				      	//add only OAuth parameters and not empty
				      	if(oauthPossibleParameters.contains(split(0)) && ! parameterValue.isEmpty)
				       		Some(split(0),parameterValue)  // return key , value
				      	else
				        	None
				    } 
				    else 
				      None 
			  	}

				Map(parametersList.split(",").flatMap(dynamicListExtract _): _*)
			}
			
			S.request match 
		    {
		      	case Full(a) =>  a.header("Authorization") match
		        {
			      case Full(parameters) => toMap(parameters)
			      case _ => Map(("",""))
			    }
		      	case _ => Map(("",""))
		    }
	    }
	    //return true if the authorization header has a duplicated parameter 
	    def duplicatedParameters =
	    {
		  	var output=false
		  	val authorizationParameters = S.request.get.header("Authorization").get.split(",") 
		  	
		  	//count the iterations of a parameter in the authorization header 
		  	def countPram(parameterName : String, parametersArray :Array[String] )={
		  	  var i = 0
		  	  parametersArray.foreach(t => {if (t.split("=")(0) == parameterName) i+=1})
		  	  i
		  	}
		  	
		  	//return true if on of the Authorization header parameter is present more than one time
		  	authorizationParameters.foreach(t => { 
		  	  if(countPram(t.split("=")(0),authorizationParameters)>1 && !output)
		  	    output=true 
		  	 })
	    	
		  	output
	    }

	    def suportedOAuthVersion(OAuthVersion : Option[String]) : Boolean = {
		    //auth_version is OPTIONAL.  If present, MUST be set to "1.0".
		    OAuthVersion match
		    {
		      case Some(a) =>  a=="1" || a=="1.0"
		      case _ => true
		    }
	    }
	    def wrongTimestamp(requestTimestamp : Date) = {
	    	val currentTime = Platform.currentTime
	    	val timeRange : Long = 180000 //3 minutes
	    	//check if the timestamp is positive and in the time range	
	    	requestTimestamp.getTime < 0 || requestTimestamp.before(new Date(currentTime - timeRange)) ||  requestTimestamp.after(new Date(currentTime + timeRange))
	    }

	    def alReadyUsedNonce(parameters : Map[String, String]) = {

			 /*The nonce value MUST be unique across all requests with the
			   same timestamp, client credentials, and token combinations.
			 */
	    	val token = parameters.get("oauth_token") getOrElse ""
		    
	    	Nonce.findAll(By(Nonce.value,parameters.get("oauth_nonce").get), By(Nonce.tokenKey, token),
		        By(Nonce.consumerkey,parameters.get("oauth_consumer_key").get),
		        By(Nonce.timestamp, new Date(parameters.get("oauth_timestamp").get.toLong))).length !=0 
	    }
	    def registeredApplication(consumerKey : String ) : Boolean =
	    {
		    Consumer.find(By(Consumer.key,consumerKey)) match
		    {
		      case Full(application) => application.isActive
		      case _ => false
		    }
	    }
	    def correctSignature(OAuthparameters : Map[String, String], httpMethod : String) =
	    {
	    	//Normalize an encode the request parameters as explained in Section 3.4.1.3.2
	    	//of OAuth 1.0 specification (http://tools.ietf.org/html/rfc5849)
		    def generateOAuthParametersString(OAuthparameters : Map[String, String]) : String = 
		    {
		       	def sortParam( keyAndValue1 : (String, String), keyAndValue2 : (String, String))= keyAndValue1._1.compareTo(keyAndValue2._1) < 0
		       
		       	var parameters =""

		       	//sort the parameters by name
		       	OAuthparameters.toList.sort(sortParam _).foreach(t => 
		        	if(t._1 != "oauth_signature")
			     		parameters += URLEncoder.encode(t._1,"UTF-8")+"%3D"+
			     		URLEncoder.encode(t._2,"UTF-8")+"%26"
		       	)
			   	parameters = parameters.dropRight(3) //remove the "&" encoded sign
			   	parameters
		    }
			
			//prepare the base string	     
		    var baseString = httpMethod+"&"+URLEncoder.encode(S.hostAndPath,"UTF-8")+"&"
		    baseString+= generateOAuthParametersString(OAuthparameters)
		    
		    //get the key to sign 
		    val comsumer = Consumer.find(By(Consumer.key,OAuthparameters.
		    	get("oauth_consumer_key").get)).get  
		    var secret= comsumer.secret.toString()
		    OAuthparameters.get("oauth_token") match
		    {
		      case Some(tokenKey) => Token.find(By(Token.key,tokenKey)) match {
		        	case Full(token) => secret+= "&" +token.secret.toString() 
		        	case _ => None 
		        }
		      case _ => None
		    }

		    //signing process
		    var m = Mac.getInstance("HmacSHA256");
		    m.init(new SecretKeySpec(secret.getBytes(),"HmacSHA256"))
		    val calculatedSignature = Helpers.base64Encode(m.doFinal(baseString.getBytes)).dropRight(1) //remove the "=" added by the base64Encode method
		    

		    calculatedSignature==OAuthparameters.get("oauth_signature").get
	    }

	    //check if the token exists and is still valid
	    def validToken(tokenKey : String, verifier : String) =
	    {
	      	Token.find(By(Token.key, tokenKey)) match
	      	{
		        case Full(token) => if(token.expirationDate.compareTo(new Date(
		        	Platform.currentTime)) == 1 && token.verifier==verifier)
		        						true
		        				    else
		        					    false
		        case _ => false
	      	}
	    }
	    def validToken2(tokenKey : String) =
	    {
	      	Token.find(By(Token.key, tokenKey)) match
	      	{
		        case Full(token) => if(token.expirationDate.compareTo(new Date(
		        	Platform.currentTime)) == 1)
		        						true
		        				    else
		        					    false
		        case _ => false
	      	}
	    }
	    //check if the all the necessary OAuth parameters are present regarding 
	    //the request type 
	    def enoughtOauthParameters(parameters : Map[String, String], requestType : String) : Boolean = 
	    {
	   		val parametersBase = List("oauth_consumer_key","oauth_nonce","oauth_signature_method",
	   			"oauth_timestamp", "oauth_signature")
			
			if (parameters.size < 6)
	    		false
	    	else if(requestType == "requestToken")
	    		("oauth_callback" :: parametersBase).toSet.subsetOf(parameters.keySet)
	    	else if(requestType=="authorizationToken")
	    	    ("oauth_token" :: "oauth_verifier" :: parametersBase).toSet.subsetOf(parameters.keySet)
	    	else if(requestType=="protectedResource")
	    	    ("oauth_token" :: parametersBase).toSet.subsetOf(parameters.keySet)
	    	else 
	    		false
	    }
	    	
	    var data =""
	    var httpCode : Int = 500
	     
	    var parameters = getAllParameters

	    //does all the OAuth parameters are presents?
	    if(! enoughtOauthParameters(parameters,requestType))
	    {
	      	data = "One or several parameters are missing"
		  	httpCode = 400
	    }
	    //no parameter exists more than one times
	    else if (duplicatedParameters)
	    {
	      	data = "Duplicated protocol parameters"
	      	httpCode = 400
	    }
	    //valid OAuth  
	    else if(!suportedOAuthVersion(parameters.get("oauth_version")))
	    {
	      	data = "OAuth version not supported"
	      	httpCode = 400
	    }
	    //supported signature method
	    else if (parameters.get("oauth_signature_method").get.toLowerCase()!="hmac-sha256")
	    {
	      	data = "Unsupported signature method"
	      	httpCode = 400
	    }
	    //check if the application is registered and active
	    else if(! registeredApplication(parameters.get("oauth_consumer_key").get))
	    {
	      	data = "Invalid client credentials"
	      	httpCode = 401
	    }
	    //valid timestamp
	    else if(wrongTimestamp(new Date(parameters.get("oauth_timestamp").get.toLong)))
	    {
	      	data = "wrong timestamps"
	      	httpCode = 400
	    }
	    //unused nonce
	    else if (alReadyUsedNonce(parameters))
	    {
	      	data = "Nonce already used"
	      	httpCode = 401
	    }
	    //In the case OAuth authorization token request, check if the token is still valid and the verifier is correct  
	    else if(requestType=="authorizationToken" && !validToken(parameters.get("oauth_token").get, parameters.get("oauth_verifier").get))
	    {
	      	data = "Invalid or expired token"
	      	httpCode = 401
	    }
	    //In the case protected resource access request, check if the token is still valid
	    else if (requestType=="protectedResource" && 
	    	! validToken2(parameters.get("oauth_token").get))
	    {
			data = "Invalid or expired token"
	      	httpCode = 401	    	
	    }
	    //checking if the signature is correct  
	    else if(! correctSignature(parameters, httpMethod))
	    {
	      	data = "Invalid signature"
	      	httpCode = 401
	    }
	    else
		  	httpCode = 200

	    (httpCode, data.getBytes(), parameters)
  	}
  	private def generateTokenAndSecret(ConsumerKey : String) = 
  	{
    	import java.util.UUID._
	    
	    // generate random string
	    val token_data = ConsumerKey + randomUUID().toString() + Helpers.randomString(20)
	    //use HmacSHA256 to compute the token 
	    val m = Mac.getInstance("HmacSHA256");
	    m.init(new SecretKeySpec(token_data.getBytes(),"HmacSHA256"))
	    val token = Helpers.base64Encode(m.doFinal(token_data.getBytes)).dropRight(1) 
	    
	    // generate random string
	    val secret_data = ConsumerKey + randomUUID().toString() + Helpers.randomString(20) + token
	    //use HmacSHA256 to compute the token 
	    val n = Mac.getInstance("HmacSHA256");
	    n.init(new SecretKeySpec(token_data.getBytes(),"HmacSHA256"))
	    val secret = Helpers.base64Encode(n.doFinal(token_data.getBytes)).dropRight(1) 
	    
	    (token,secret)
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
	    Consumer.find(By(Consumer.key,oAuthParameters.get("oauth_consumer_key").get)) match
	    {
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
	    val tokenDuration : Long = 1800000  //the duration is 30 minutes TODO: 300000 in production mode
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
	    Consumer.find(By(Consumer.key,oAuthParameters.get("oauth_consumer_key").get)) match
	    {
	      case Full(consumer) => token.consumerId(consumer.id)
	      case _ => None
	    }
	    Token.find(By(Token.key, oAuthParameters.get("oauth_token").get)) match {
	    	case Full(requestToken) => token.userId(requestToken.userId)
	    	case _ => None
	    }
	    token.key(tokenKey)
	    token.secret(tokenSecret)
	    val currentTime = Platform.currentTime
	    val tokenDuration : Long = 86400000 //the duration is 1 day
	    token.duration(tokenDuration) 
	    token.expirationDate(new Date(currentTime+tokenDuration))
	    token.insertDate(new Date(currentTime))
	    val tokenSaved = token.save()
	    
	    nonceSaved && tokenSaved
 	}

 	// this method is specific to the authorization page ( where the user login to grant access 
 	// to the application (step 2))  
 	def tokenCheck = 
	  	S.param("oauth_token") match
	  	{
		    case Full(token) => 
		      	Token.find(By(Token.key,token.toString)) match
		      	{
			        case Full(appToken) => 
			          	//check if the token is still valid
			          	if(appToken.expirationDate.compareTo(new Date(Platform.currentTime)) == 1)
			        	  	if(User.loggedIn_?)
			              	{
							    var verifier =""
							    // if the user is logged in and non verifier have been generated 
							    if(appToken.verifier.isEmpty) 
							    {
							    	val randomVerifier = Helpers.base64Encode(Helpers.randomString(20).getBytes()).dropRight(1)  
							    	appToken.verifier(randomVerifier)
							    	appToken.userId(User.currentUserId.get.toLong)
							    	if(appToken.save())
							    		verifier = randomVerifier
							    }
							    else
							    	verifier=appToken.verifier
							   
							   	// show the verifier if the application does not support 
							   	// redirection  	
				                if(Token.callbackURL=="oob")
				                  	"#verifier " #> verifier 
				                else
				                {
				                  	//redirect the user to the application with the verifier
				                  	S.redirectTo(appToken.callbackURL+"?oauth_token="+token+
				                  		"&oauth_verifier="+verifier)
				                  	"#verifier" #> "you should be redirected"
				                }
						  	} 
			              	else 
			              		//the user is not logged in so we show a login form
				        	  	Consumer.find(By(Consumer.id,appToken.consumerId)) match
					          	{
						            case Full(consumer) =>
						            {
						              	"#applicationName" #> consumer.name &
						              	"#verifier" #>NodeSeq.Empty &
						              	"#errorMessage" #> NodeSeq.Empty &
						              	{
						            		".login [action]" #> User.loginPageURL &
									    	".forgot [href]" #> 
									    	{
									        	val href = for {
									          	menu <- User.resetPasswordMenuLoc
									        	} yield menu.loc.calcDefaultHref
									        	href getOrElse "#"
									    	} & 
									    	".signup [href]" #> 
									    	User.signUpPath.foldLeft("")(_ + "/" + _) 
						              }
						            }
					            	case _ => 
					            	{
					            		"#errorMessage" #> "Application not found" &
								    	"#userAccess" #> NodeSeq.Empty 
					            	}
					          	}
						else
			          	{
			        	  	"#errorMessage" #> "Token expired" &
					      	"#userAccess" #> NodeSeq.Empty 
			          	}
			        case _ =>
			        {
			          	"#errorMessage" #> "This token does not exist" &
				      	"#userAccess" #> NodeSeq.Empty 
			        }
		      	}
		    case _ => 
		    {
		      	"#errorMessage" #> "There is no Token"&
		      	"#userAccess" #> NodeSeq.Empty 
		    }
		}
	def dataBaseCleaner : Unit = 
	{
		import net.liftweb.util.Schedule
		import net.liftweb.mapper.By_<
		Schedule.schedule(dataBaseCleaner _, 1 hour)

		val currentDate = new Date()
		
		//As in "wrong timestamp" function, 3 minutes is the timestamp limit where we accept //requests 
		val timeLimit = new Date(currentDate.getTime + 180000) 
		
		//delete expired tokens and nonces
		(Token.findAll(By_<(Token.expirationDate,currentDate)) ++ Nonce.findAll(By_<(Nonce.timestamp,timeLimit))).foreach(t => t.delete_!)
	}	
}