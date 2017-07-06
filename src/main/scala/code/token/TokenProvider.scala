package code.token

import java.util.Date

import code.model.TokenType.TokenType
import code.model.{MappedTokenProvider, Token}
import code.remotedata.RemotedataTokens
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}


object Tokens extends SimpleInjector {

  val tokens = new Inject(buildOne _) {}

  def buildOne: TokensProvider =
    Props.getBool("use_akka", false) match {
      case false  => MappedTokenProvider
      case true => RemotedataTokens     // We will use Akka as a middleware
    }

}

trait TokensProvider {
  def getTokenByKey(key: String): Box[Token]
  def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token]
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
