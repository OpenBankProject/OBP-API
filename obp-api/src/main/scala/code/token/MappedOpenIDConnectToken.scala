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

import net.liftweb.common.Box
import net.liftweb.mapper._

object MappedOpenIDConnectTokensProvider extends OpenIDConnectTokensProvider {
  def createToken(tokenType: String,
                  accessToken: String,
                  idToken: String,
                  refreshToken: String,
                  scope: String,
                  expiresIn: Long,
                  authUserPrimaryKey: Long): Box[OpenIDConnectToken] = Box.tryo {
    OpenIDConnectToken.create
        .TokenType(tokenType.toString())
        .AccessToken(accessToken)
        .IDToken(idToken)
        .RefreshToken(refreshToken)
        .Scope(scope)
        .ExpiresIn(expiresIn)
        .AuthUserPrimaryKey(authUserPrimaryKey)
      .saveMe()
  }
  def getOpenIDConnectTokenByAuthUser(authUserPrimaryKey: Long) =
    OpenIDConnectToken.findAll(By(OpenIDConnectToken.AuthUserPrimaryKey, authUserPrimaryKey))
      .sortBy(_.createdAt.get)(Ordering[Date].reverse).headOption

}

class OpenIDConnectToken extends OpenIDConnectTokenTrait with LongKeyedMapper[OpenIDConnectToken] with IdPK with CreatedUpdated {

  def getSingleton: OpenIDConnectToken.type = OpenIDConnectToken
  object AccessToken extends MappedText(this)
  object IDToken extends MappedText(this)
  object RefreshToken extends MappedText(this)
  object Scope extends MappedString(this, 250)
  object TokenType extends MappedString(this, 250)
  object ExpiresIn extends MappedLong(this)
  object AuthUserPrimaryKey extends MappedLong(this)

  override def accessToken: String = AccessToken.get
  override def idToken: String = IDToken.get
  override def refreshToken: String = RefreshToken.get
  override def scope: String = Scope.get
  override def tokenType: String = TokenType.get
  override def expiresIn: Long = ExpiresIn.get
  override def authUserPrimaryKey: Long = AuthUserPrimaryKey.get

}

object OpenIDConnectToken extends OpenIDConnectToken with LongKeyedMetaMapper[OpenIDConnectToken] {
  override def dbIndexes: List[BaseIndex[OpenIDConnectToken]] = super.dbIndexes
}