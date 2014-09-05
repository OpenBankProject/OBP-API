package code.api

import code.model.TokenType._
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import code.model.dataAccess.{ViewPrivileges, ViewImpl, APIUser}
import code.util.APIUtil.OAuth.{Token, Consumer}
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import net.liftweb.util.TimeHelpers.TimeSpan

trait DefaultUsers {

  //create the application
  lazy val testConsumer =
    OBPConsumer.create.
      name("test application").
      isActive(true).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      saveMe

  val defaultProvider = Props.get("hostname","")

  lazy val consumer = new Consumer (testConsumer.key,testConsumer.secret)
  // create the access token
  lazy val tokenDuration = weeks(4)

  lazy val obpuser1 =
    APIUser.create.provider_(defaultProvider).
      saveMe

  lazy val testToken =
    OBPToken.create.
      tokenType(Access).
      consumerId(testConsumer.id).
      userForeignKey(obpuser1.id.toLong).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      duration(tokenDuration).
      expirationDate({(now : TimeSpan) + tokenDuration}).
      insertDate(now).
      saveMe

  lazy val token = new Token(testToken.key, testToken.secret)

  // create a user for test purposes
  lazy val obpuser2 =
    APIUser.create.provider_(defaultProvider).
      saveMe

  //we create an access token for the other user
  lazy val testToken2 =
    OBPToken.create.
      tokenType(Access).
      consumerId(testConsumer.id).
      userForeignKey(obpuser2.id.toLong).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      duration(tokenDuration).
      expirationDate({(now : TimeSpan) + tokenDuration}).
      insertDate(now).
      saveMe

  lazy val token2 = new Token(testToken2.key, testToken2.secret)

  // create a user for test purposes
  lazy val obpuser3 =
    APIUser.create.provider_(defaultProvider).
      saveMe

  //we create an access token for the other user
  lazy val testToken3 =
    OBPToken.create.
      tokenType(Access).
      consumerId(testConsumer.id).
      userForeignKey(obpuser3.id.toLong).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      duration(tokenDuration).
      expirationDate({(now : TimeSpan) + tokenDuration}).
      insertDate(now).
      saveMe

  lazy val token3 = new Token(testToken3.key, testToken3.secret)

  lazy val user1 = Some((consumer, token))
  lazy val user2 = Some((consumer, token2))
  lazy val user3 = Some((consumer, token3))


}
