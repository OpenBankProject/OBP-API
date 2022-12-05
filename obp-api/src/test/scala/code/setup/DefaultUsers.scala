package code.setup

import code.TestServer

import java.util.{Date, UUID}
import code.api.{Constant, GatewayLogin}
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth.{Consumer, Token}
import code.api.util.ErrorMessages._
import code.consumer.Consumers
import code.model.TokenType._
import code.model.dataAccess.ResourceUser
import code.model.{AppType, UserX}
import code.token.Tokens
import net.liftweb.util.Helpers._
import net.liftweb.util.TimeHelpers.TimeSpan

import scala.compat.Platform

/**
  * This trait prepare the login users, it simulate the Direct Login,
  * Create the consumer -> create resource users --> create the tokens for this user --> provide the Login user 
  * The login users are tuples (consumer, token), contains the consumer and token, used for direct login.
  */
trait DefaultUsers {
  
  lazy val userId1 = TestServer.userId1
  lazy val userId2 = TestServer.userId2
  lazy val userId3 = TestServer.userId3
  lazy val userId4 = TestServer.userId4
  
  lazy val resourceUser1Name = TestServer.resourceUser1Name 
  lazy val resourceUser2Name = TestServer.resourceUser2Name 
  lazy val resourceUser3Name = TestServer.resourceUser3Name 
  lazy val resourceUser4Name = TestServer.resourceUser4Name 

  //create the application(consumer, used it in the Login credential, mainly used the consume_key and consumer_secret)
  lazy val testConsumer = Consumers.consumers.vend.createConsumer(
    key = Some(randomString(40).toLowerCase),
    secret = Some(randomString(40).toLowerCase),
    isActive = Some(true),
    name = Some("test application"),
    appType = None,
    description = Some("description"),
    developerEmail = Some("eveline@example.com"),
    redirectURL = None,
    createdByUserId = userId1
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  lazy val consumer = Consumer(testConsumer.key.get, testConsumer.secret.get)
  
  // create the access token
  val expiration = APIUtil.getPropsAsIntValue("token_expiration_weeks", 4)
  lazy val tokenDuration = weeks(expiration)
  
  // Create resource user, need provider 
  val defaultProvider = Constant.HostName
  
  // create some resource user for test purposes
  lazy val resourceUser1 = UserX.findByProviderId(provider = defaultProvider, idGivenByProvider= resourceUser1Name).map(_.asInstanceOf[ResourceUser])
    .getOrElse(UserX.createResourceUser(provider = defaultProvider, providerId= Some(resourceUser1Name), createdByConsentId= None, name= Some(resourceUser1Name),
      email= Some("resourceUser1@123.com"), userId= userId1, company = Some("Tesobe GmbH"))
      .openOrThrowException(attemptedToOpenAnEmptyBox)
    )
  
  lazy val resourceUser2 = UserX.findByProviderId(provider = defaultProvider, idGivenByProvider= resourceUser2Name).map(_.asInstanceOf[ResourceUser])
    .getOrElse(UserX.createResourceUser(provider = defaultProvider, providerId= Some(resourceUser2Name), createdByConsentId= None, 
      name= Some(resourceUser2Name),email= Some("resourceUser2@123.com"), userId= userId2, company = Some("Tesobe GmbH"))
      .openOrThrowException(attemptedToOpenAnEmptyBox)
    )
  
  lazy val resourceUser3 = UserX.findByProviderId(provider = defaultProvider, idGivenByProvider= resourceUser3Name).map(_.asInstanceOf[ResourceUser])
    .getOrElse(UserX.createResourceUser(provider = defaultProvider, providerId= Some(resourceUser3Name), createdByConsentId= None, 
      name= Some(resourceUser3Name),email= Some("resourceUser3@123.com"), userId= userId3, company = Some("Tesobe GmbH"))
      .openOrThrowException(attemptedToOpenAnEmptyBox))
  
  lazy val resourceUser4 = UserX.findByProviderId(provider = defaultProvider, idGivenByProvider= resourceUser4Name).map(_.asInstanceOf[ResourceUser])
    .getOrElse(UserX.createResourceUser(provider = GatewayLogin.gateway, providerId = Some(resourceUser4Name), createdByConsentId= Some("simonr"), name= Some(resourceUser4Name), 
      email= Some("resourceUser4@123.com"), userId=userId4, company = Some("Tesobe GmbH"))
      .openOrThrowException(attemptedToOpenAnEmptyBox))

  // create the tokens in database, we only need token-key and token-secretAllCases
  lazy val testToken1 = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id.get),
    Some(resourceUser1.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  
  lazy val testToken2 = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id.get),
    Some(resourceUser2.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  
  lazy val testToken3 = Tokens.tokens.vend.createToken(Access,
    Some(testConsumer.id.get),
    Some(resourceUser3.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)

  lazy val testToken4 = Tokens.tokens.vend.createToken(Access,
    Some(testConsumer.id.get),
    Some(resourceUser4.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  
  // prepare the tokens
  lazy val token1 = Token(testToken1.key.get, testToken1.secret.get)
  lazy val token2 = Token(testToken2.key.get, testToken2.secret.get)
  lazy val token3 = Token(testToken3.key.get, testToken3.secret.get)
  lazy val token4 = Token(testToken4.key.get, testToken4.secret.get)

  // prepare the OAuth users to login 
  lazy val user1 = Some(consumer, token1)
  lazy val user2 = Some(consumer, token2)
  lazy val user3 = Some(consumer, token3)
  lazy val userGatewayLogin = Some(consumer, token4)

}
