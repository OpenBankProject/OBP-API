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
      logger.debug("getTokenByKey(" + key +")")
      sender ! extractResult(mapper.getTokenByKey(key))

    case cc.getTokenByKeyFuture(key: String) =>
      logger.debug("getTokenByKeyFuture(" + key +")")
      sender ! (mapper.getTokenByKey(key))

    case cc.getTokenByKeyAndType(key: String, tokenType: TokenType) =>
      logger.debug("getTokenByKeyAndType(" + key + ", " + tokenType + ")")
      sender ! extractResult(mapper.getTokenByKeyAndType(key, tokenType))

    case cc.getTokenByKeyAndTypeFuture(key: String, tokenType: TokenType) =>
      logger.debug("getTokenByKeyAndTypeFuture(" + key + ", " + tokenType + ")")
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
      logger.debug("createToken(" + tokenType + ", " +
                                    consumerId + ", " +
                                    userId + ", " +
                                    key + ", " +
                                    secret + ", " +
                                    duration + ", " +
                                    insertDate + ", " +
                                    tokenType + ", " +
                                    callbackUrl + ")")
      sender ! extractResult(mapper.createToken(tokenType, consumerId, userId, key, secret, duration, expirationDate, insertDate, callbackUrl))

    case cc.gernerateVerifier(id: Long) =>
      logger.debug("gernerateVerifier(" + id +")")
      sender ! extractResult(mapper.gernerateVerifier(id))

    case cc.updateToken(id: Long, userId: Long) =>
      logger.debug("updateToken(" + id + ", " + userId + ")")
      sender ! extractResult(mapper.updateToken(id, userId))

    case cc.deleteToken(id: Long) =>
      logger.debug("deleteToken(" + id +")")
      sender ! extractResult(mapper.deleteToken(id))

    case cc.deleteExpiredTokens(currentDate: Date) =>
      logger.debug("deleteExpiredTokens(" + currentDate +")")
      sender ! extractResult(mapper.deleteExpiredTokens(currentDate))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


