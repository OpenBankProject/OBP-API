/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.token

import java.util.Date

import code.api.util.APIUtil
import code.model.{MappedTokenProvider, Token, TokenType}
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object Tokens extends SimpleInjector {

  val tokens = new Inject(() => buildOne) {}

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
