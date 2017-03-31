package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.token.{RemotedataTokensCaseClasses, TokensProvider}
import code.model.Token
import code.model.TokenType.TokenType
import net.liftweb.common._


object RemotedataTokens extends ActorInit with TokensProvider {

  val cc = RemotedataTokensCaseClasses

  def getTokenByKey(key: String): Box[Token] =
    extractFutureToBox(actor ? cc.getTokenByKey(key))

  def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token] =
    extractFutureToBox(actor ? cc.getTokenByKeyAndType(key, tokenType))

  def createToken(tokenType: TokenType,
                  consumerId: Option[Long],
                  userId: Option[Long],
                  key: Option[String],
                  secret: Option[String],
                  duration: Option[Long],
                  expirationDate: Option[Date],
                  insertDate: Option[Date],
                  callbackURL: Option[String]): Box[Token] =
    extractFutureToBox(actor ? cc.createToken(tokenType, consumerId, userId, key, secret, duration, expirationDate, insertDate, callbackURL))

  def gernerateVerifier(id: Long): String =
    extractFuture(actor ? cc.gernerateVerifier(id))

  def updateToken(id: Long, userId: Long): Boolean =
    extractFuture(actor ? cc.updateToken(id, userId))

  def deleteToken(id: Long): Boolean =
    extractFuture(actor ? cc.deleteToken(id))

  def deleteExpiredTokens(currentDate: Date): Boolean =
    extractFuture(actor ? cc.deleteExpiredTokens(currentDate))


}
