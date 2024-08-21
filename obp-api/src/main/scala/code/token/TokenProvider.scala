package code.token

import java.util.Date

import code.api.util.APIUtil
import code.model.{MappedTokenProvider, Token, TokenType}
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object Tokens extends SimpleInjector {

  val tokens = new Inject(buildOne _) {}

  def buildOne: TokensProvider = MappedTokenProvider

}

trait TokensProvider {
  def getTokenByKey(key: String): Box[Token]
  def getTokenByKeyFuture(key: String): Future[Box[Token]]
  def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token]
  def getTokenByKeyAndTypeFuture(key: String, tokenType: TokenType): Future[Box[Token]]
  def createToken(tokenType: TokenType,
                  consumerId: Option[Long],
                  userId: Option[Long], //Why do we use the UserId Long type??
                  key: Option[String],
                  secret: Option[String],
                  duration: Option[Long],
                  expirationDate: Option[Date],
                  insertDate: Option[Date],
                  callbackURL: Option[String]): Box[Token]
  def gernerateVerifier(id: Long): String
  def updateToken(id: Long, userId: Long): Boolean
  def deleteToken(id: Long): Boolean
  def deleteExpiredTokens(currentDate: Date): Boolean
}
