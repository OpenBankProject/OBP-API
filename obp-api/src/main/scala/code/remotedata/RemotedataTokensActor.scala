package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.token.RemotedataTokensCaseClasses
import code.model.TokenType
import code.model._
import code.util.Helper.MdcLoggable


class RemotedataTokensActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTokenProvider
  val cc = RemotedataTokensCaseClasses

  def receive = {

    case cc.getTokenByKey(key: String) =>
      logger.debug(s"getTokenByKey($key)")
      sender ! (mapper.getTokenByKey(key))

    case cc.getTokenByKeyFuture(key: String) =>
      logger.debug(s"getTokenByKeyFuture($key)")
      sender ! (mapper.getTokenByKey(key))

    case cc.getTokenByKeyAndType(key: String, tokenType: TokenType) =>
      logger.debug(s"getTokenByKeyAndType($key, $tokenType)")
      sender ! (mapper.getTokenByKeyAndType(key, tokenType))

    case cc.getTokenByKeyAndTypeFuture(key: String, tokenType: TokenType) =>
      logger.debug(s"getTokenByKeyAndTypeFuture($key, $tokenType)")
      sender ! (mapper.getTokenByKeyAndType(key, tokenType))

    case cc.createToken(tokenType: TokenType,
                        consumerId: Option[Long],
                        userId: Option[Long],
                        key: Option[String],
                        secret: Option[String],
                        duration: Option[Long],
                        expirationDate: Option[Date],
                        insertDate: Option[Date],
                        callbackUrl: Option[String]) =>
      logger.debug(s"createToken($tokenType,$consumerId, $userId, $key, $secret, $duration, $insertDate, $tokenType, $callbackUrl)")
      sender ! (mapper.createToken(tokenType, consumerId, userId, key, secret, duration, expirationDate, insertDate, callbackUrl))

    case cc.gernerateVerifier(id: Long) =>
      logger.debug(s"gernerateVerifier($id)")
      sender ! (mapper.gernerateVerifier(id))

    case cc.updateToken(id: Long, userId: Long) =>
      logger.debug(s"updateToken($id, $userId")
      sender ! (mapper.updateToken(id, userId))

    case cc.deleteToken(id: Long) =>
      logger.debug(s"deleteToken($id)")
      sender ! (mapper.deleteToken(id))

    case cc.deleteExpiredTokens(currentDate: Date) =>
      logger.debug(s"deleteExpiredTokens($currentDate)")
      sender ! (mapper.deleteExpiredTokens(currentDate))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


