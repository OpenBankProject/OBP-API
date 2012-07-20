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
	  	case Req("oauth" :: "initiate" :: Nil,_ ,PostRequest) => 
	  	{
		  	val httpCodeDataAndMap = validator("requestToken")
		  	val httpCode = httpCodeDataAndMap._1
		  	var data = httpCodeDataAndMap._2 
		  	val oAuthParameters = httpCodeDataAndMap._3
		  	if(httpCode==200)
		  	{
		    	val tokenAndSecret = generateTokenAndSecret(oAuthParameters.get("oauth_consumer_key").get)
		    	if(saveRequestToken(oAuthParameters,tokenAndSecret._1, tokenAndSecret._2))
		    		data=("oauth_token="+tokenAndSecret._1+"&oauth_token_secret="+tokenAndSecret._2+"&oauth_callback_confirmed=true").getBytes()
			}									    		
	      	val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
	      	Full(InMemoryResponse(data,headers,Nil,httpCode))
	  	}
	  	case Req("oauth" :: "token" :: Nil,_, PostRequest) => 
	  	{
		  	val httpCodeDataAndMap = validator("authorizationToken")
		  	val httpCode = httpCodeDataAndMap._1
		  	var data = httpCodeDataAndMap._2 
		  	val oAuthParameters = httpCodeDataAndMap._3
		  	if(httpCode==200)
		  	{
		    	val tokenAndSecret = generateTokenAndSecret(oAuthParameters.get("oauth_consumer_key").get)

		    	if(saveAuthorizationToken(oAuthParameters,tokenAndSecret._1, tokenAndSecret._2))
			      Token.find(By(Token.key,oAuthParameters.get("oauth_token").get)) match {
			      	case Full(requestToken) => requestToken.delete_!
			      	case _ => None
			      }

			    data=("oauth_token="+tokenAndSecret._1+"&oauth_token_secret="+tokenAndSecret._2+"&oauth_callback_confirmed=true").getBytes()
		  	}		      
		  	val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
	      	Full(InMemoryResponse(data,headers,Nil,httpCode))
	  	}
  	}
  
  	private def validator(requestType : String) = 
  	{
	    def getAllParameters(requestType : String) = 
	    {
			//convert the string containing the list of OAuth parameters to a Map 
			def toMap(parametersList : String) = 
			{
				def dynamicListExtract(input: String)  = 
			  	{
					val oauthPossibleParameters = List("oauth_consumer_key","oauth_nonce","oauth_signature_method",
						"oauth_timestamp","oauth_version", "oauth_signature","oauth_callback","oauth_token","oauth_verifier")
				    if (input contains "=") {
				      val split = input.split("=",2)
				      val parameterValue = URLDecoder.decode(split(1)).replace("\"","")
				      //add only oauth parameters and not empty
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
	    def duplicatedParameters =
	    {
		  	var output=false
		  	val authorizationParameters = S.request.get.header("Authorization").get.split(",") 
		  	//count the iteration of a parameter in the authorization header 
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
		      case Some(a) =>  if(a=="1" || a=="1.0") true else false
		      case _ => true
		    }
	    }
	    def wrongTimestamp(requestTimestamp : Date) = {
	    	val currentTime = Platform.currentTime
	    	val timeRange : Long = 60000 //3 minutes

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
	    def supportedSignatureMethod(signatureMethod : String) : Boolean= 
	    {
		    signatureMethod.toLowerCase()=="hmac-sha256"
	    }
	    def correctSignature(OAuthparameters : Map[String, String]) =
	    {
		     def generateOAuthParametersString(OAuthparameters : Map[String, String]) : String = 
		     {
		       def sortParam( keyAndValue1 : (String, String), keyAndValue2 : (String, String))= keyAndValue1._1.compareTo(keyAndValue2._1) < 0
		       
		       var parameters =""

		       //sort the parameters by name
		       OAuthparameters.toList.sort(sortParam _).foreach(t => 
		         if(t._1 != "oauth_signature")
			     	parameters += URLEncoder.encode(t._1,"UTF-8")+"%3D"+URLEncoder.encode(t._2,"UTF-8")+"%26"
		       )
			   parameters = parameters.dropRight(3) //remove the "&" encoded sign
			   parameters
		     }
		     
		    var baseString = "POST&"+URLEncoder.encode(S.hostAndPath,"UTF-8")+"&"
		    baseString+= generateOAuthParametersString(OAuthparameters)
		    val comsumer = Consumer.find(By(Consumer.key,OAuthparameters.get("oauth_consumer_key").get)).get  
		    var secret= comsumer.secret.toString()
		    OAuthparameters.get("oauth_token") match
		    {
		      case Some(tokenKey) => Token.find(By(Token.key,tokenKey)) match {
		        	case Full(token) => secret+= "&" +token.secret.toString() 
		        	case _ => None 
		        }
		      case _ => None
		    }
		    var m = Mac.getInstance("HmacSHA256");
		    m.init(new SecretKeySpec(secret.getBytes(),"HmacSHA256"))
		    val calculatedSignature = Helpers.base64Encode(m.doFinal(baseString.getBytes)).dropRight(1) 
		    println("base string : "+baseString)
		    println("calculated Signature : "+ calculatedSignature)

		    calculatedSignature==OAuthparameters.get("oauth_signature").get
	    }
	    def validToken(tokenKey : String, verifier : String) =
	    {
	      	Token.find(By(Token.key, tokenKey)) match
	      	{
		        case Full(token) => if(token.expirationDate.compareTo(new Date(Platform.currentTime)) == 1 && token.verifier==verifier)
		        						true
		        				    else
		        					    false
		        case _ => false
	      	}
	    }
	    def enoughtOauthParameters(parameters : Map[String, String], requestType : String) : Boolean = 
	    {
	   		val parametersBase = List("oauth_consumer_key","oauth_nonce","oauth_signature_method","oauth_timestamp", "oauth_signature")
			
			if (parameters.size < 6)
	    		false
	    	else if(requestType == "requestToken")
	    	{
	    		val requestTokenParameters = "oauth_callback" :: parametersBase
	    		requestTokenParameters.toSet.subsetOf(parameters.keySet)
	    	}
	    	else if(requestType=="authorizationToken")
	    	{
	    	    val authorizationTokenParameters =  "oauth_token" :: "oauth_verifier" :: parametersBase
				authorizationTokenParameters.toSet.subsetOf(parameters.keySet)
	    	}
	    	else 
	    		false
	    }
	    	
	    var data =""
	    var httpCode : Int = 200
	     
	    var parameters = getAllParameters(requestType)
	    correctSignature(parameters)
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
	      data = "OAuth version not suported"
	      httpCode = 400
	    }
	    //supported signature method
	    else if (!supportedSignatureMethod(parameters.get("oauth_signature_method").get))
	    {
	      data = "Unsuported signature method"
	      httpCode = 400
	    }
	    //check if the application is registered 
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
	    //In the case ot authorisation token request, check if the token is still valid and the verifier is correct  
	    else if(requestType=="authorizationToken" && !validToken(parameters.get("oauth_token").get, parameters.get("oauth_verifier").get))
	    {
	      data = "Invalid or expired token"
	      httpCode = 401
	    }
	    //checking if the signature is correct  
	    else if(! correctSignature(parameters))
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
 	def tokenCheck = 
	{
	  S.param("oauth_token") match
	  {
	    case Full(token) => 
	    {
	      Token.find(By(Token.key,token.toString)) match
	      {
	        case Full(appToken) => 
	        {
	          //check if the token is still valid
	          if(appToken.expirationDate.compareTo(new Date(Platform.currentTime)) == 1)
	          {
	        	  if(User.loggedIn_?)
	              {
					    var verifier =""
					    if(appToken.verifier.length()==0) 
					    {
					    	verifier = Helpers.base64Encode(Helpers.randomString(20).getBytes()).dropRight(1)  
					    	appToken.verifier(verifier)
					    	appToken.userId(User.currentUserId.get.toLong)
					    	appToken.save()
					    }
					    else
					    	verifier=appToken.verifier
					    	
		                if(Token.callbackURL=="oob")
		                {
		                  //show the verifier
		                  	"#verifier " #> verifier 
		                }
		                else
		                {
		                  //redirect the user to the application with the verifier
		                  S.redirectTo(appToken.callbackURL+"?oauth_token="+token+"&oauth_verifier="+verifier)
		                  "#verifier" #> "we should be redirected"
		                }
				  } 
	              else 
	              {
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
						      ".signup [href]" #> User.signUpPath.foldLeft("")(_ + "/" + _)			                
			              }
			            }
			            case _ => 
			            {
			            	"#errorMessage" #> "Application not found" &
						    "#account" #> NodeSeq.Empty &
						    "#verifier" #>NodeSeq.Empty
			            }
			          }
				  }
	          }
	          else
	          {
	        	  "#errorMessage" #> "Token expired" &
			      "#account" #> NodeSeq.Empty &
			      "#verifier" #>NodeSeq.Empty
	          }
	        }
	        case _ =>
	        {
	          "#errorMessage" #> "This token does not exist" &
		      "#account" #> NodeSeq.Empty &
		      "#verifier" #>NodeSeq.Empty
	        }
	      }
	    }
	    case _ => 
	    {
	      "#errorMessage" #> "There is no Token"&
	      "#account" #> NodeSeq.Empty &
	      "#verifier" #>NodeSeq.Empty
	    }
	  }
	}
}