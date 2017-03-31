package code.token

import java.util.Date

import code.model.Token
import code.model.TokenType.TokenType
import code.remotedata.RemotedataTokens
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Tokens extends SimpleInjector {

  val tokens = new Inject(buildOne _) {}

  def buildOne: TokensProvider = RemotedataTokens

}

trait TokensProvider {
  def getTokenByKey(key: String): Box[Token]
  def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token]
  def createToken(tokenType: TokenType,
                  consumerId: Option[Long],
                  userId: Option[Long],
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

class RemotedataTokensCaseClasses {
  case class getTokenByKey(key: String)
  case class getTokenByKeyAndType(key: String, tokenType: TokenType)
  case class createToken(tokenType: TokenType,
                         consumerId: Option[Long],
                         userId: Option[Long],
                         key: Option[String],
                         secret: Option[String],
                         duration: Option[Long],
                         expirationDate: Option[Date],
                         insertDate: Option[Date],
                         callbackURL: Option[String])
  case class gernerateVerifier(id: Long)
  case class updateToken(id: Long, userId: Long)
  case class deleteToken(id: Long)
  case class deleteExpiredTokens(currentDate: Date)
}

object RemotedataTokensCaseClasses extends RemotedataTokensCaseClasses
