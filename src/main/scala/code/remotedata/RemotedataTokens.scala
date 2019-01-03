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

  def getTokenByKey(key: String): Box[Token] = getValueFromFuture(
    (actor ? cc.getTokenByKey(key)).mapTo[Box[Token]]
  )

  def getTokenByKeyFuture(key: String): Future[Box[Token]] =
    (actor ? cc.getTokenByKeyFuture(key)).mapTo[Box[Token]]

  def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token] = getValueFromFuture(
    (actor ? cc.getTokenByKeyAndType(key, tokenType)).mapTo[Box[Token]]
  )

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
                  callbackURL: Option[String]): Box[Token] = getValueFromFuture(
    (actor ? cc.createToken(tokenType, consumerId, userId, key, secret, duration, expirationDate, insertDate, callbackURL)).mapTo[Box[Token]]
  )

  def gernerateVerifier(id: Long): String = getValueFromFuture(
    (actor ? cc.gernerateVerifier(id)).mapTo[String]
  )

  def updateToken(id: Long, userId: Long): Boolean = getValueFromFuture(
    (actor ? cc.updateToken(id, userId)).mapTo[Boolean]
  )

  def deleteToken(id: Long): Boolean = getValueFromFuture(
    (actor ? cc.deleteToken(id)).mapTo[Boolean]
  )

  def deleteExpiredTokens(currentDate: Date): Boolean = getValueFromFuture(
    (actor ? cc.deleteExpiredTokens(currentDate)).mapTo[Boolean]
  )


}
