package code.setup

import java.util.UUID

import code.api.util.ErrorMessages._
import code.api.GatewayLogin
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth.{Consumer, Token}
import code.consumer.Consumers
import code.model.TokenType._
import code.model.{User, Consumer => OBPConsumer, Token => OBPToken}
import code.token.Tokens
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import net.liftweb.util.TimeHelpers.TimeSpan

/**
  * This trait prepare the login users, it simulate the Direct Login,
  * Create the consumer -> create resource users --> create the tokens for this user --> provide the Login user 
  * The login users are tuples (consumer, token), contains the consumer and token, used for direct login.
  */
trait DefaultUsers {
  
  lazy val userId = Some(UUID.randomUUID.toString)

  //create the application(consumer, used it in the Login credential, mainly used the consume_key and consumer_secret)
  lazy val testConsumer = Consumers.consumers.vend.createConsumer(
    key = Some(randomString(40).toLowerCase),
    secret = Some(randomString(40).toLowerCase),
    isActive = Some(true),
    name = Some("test application"),
    appType = None,
    description = None,
    developerEmail = None,
    redirectURL = None,
    createdByUserId = userId
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  lazy val consumer = Consumer(testConsumer.key.get, testConsumer.secret.get)
  
  // create the access token
  val expiration = APIUtil.getPropsAsIntValue("token_expiration_weeks", 4)
  lazy val tokenDuration = weeks(expiration)
  
  // Create resource user, need provider 
  val defaultProvider = APIUtil.getPropsValue("hostname", "")
  
  // create some resource user for test purposes
  lazy val resourceUser1 = User.createResourceUser(defaultProvider, None, None, None, userId).openOrThrowException(attemptedToOpenAnEmptyBox)
  lazy val resourceUser2 = User.createResourceUser(defaultProvider, None, None, None, None).openOrThrowException(attemptedToOpenAnEmptyBox)
  lazy val resourceUser3 = User.createResourceUser(defaultProvider, None, None, None, None).openOrThrowException(attemptedToOpenAnEmptyBox)
  lazy val resourceUser4 = User.createResourceUser(GatewayLogin.gateway, Some("simonr"), Some("simonr"), None, None).openOrThrowException(attemptedToOpenAnEmptyBox)

  // create the tokens in database, we only need token-key and token-secret
  lazy val testToken1 = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id.get),
    Some(resourceUser1.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some({ (now: TimeSpan) + tokenDuration }),
    Some(now),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  
  lazy val testToken2 = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id.get),
    Some(resourceUser2.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some({ (now: TimeSpan) + tokenDuration }),
    Some(now),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)
  
  lazy val testToken3 = Tokens.tokens.vend.createToken(Access,
    Some(testConsumer.id.get),
    Some(resourceUser3.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some({ (now: TimeSpan) + tokenDuration }),
    Some(now),
    None
  ).openOrThrowException(attemptedToOpenAnEmptyBox)

  lazy val testToken4 = Tokens.tokens.vend.createToken(Access,
    Some(testConsumer.id.get),
    Some(resourceUser4.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some({ (now: TimeSpan) + tokenDuration }),
    Some(now),
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
