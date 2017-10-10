package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.token.{RemotedataTokensCaseClasses, TokensProvider}
import code.model.Token
import code.model.TokenType
import net.liftweb.common.Box

import scala.concurrent.Future

object RemotedataTokens extends ObpActorInit with TokensProvider {

  val cc = RemotedataTokensCaseClasses

  def getTokenByKey(key: String): Box[Token] =
    extractFutureToBox(actor ? cc.getTokenByKey(key))

  def getTokenByKeyFuture(key: String): Future[Box[Token]] =
    (actor ? cc.getTokenByKeyFuture(key)).mapTo[Box[Token]]

  def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token] =
    extractFutureToBox(actor ? cc.getTokenByKeyAndType(key, tokenType))

  def getTokenByKeyAndTypeFuture(key: String, tokenType: TokenType): Future[Box[Token]] =
    (actor ? cc.getTokenByKeyAndTypeFuture(key, tokenType)).mapTo[Box[Token]]

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
