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

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.Box

import java.util.Date

/**
 * One OpenID Connect token set obtained for an AuthUser at login.
 *
 * Keyed to the user by `authUserPrimaryKey` — AuthUser's internal row id, not a user_id UUID.
 *
 * Rows accumulate rather than being replaced: createToken always inserts, and the read picks the
 * newest by createdAt, so a user's token history is retained. Preserved as-is.
 */
case class OpenIDConnectToken(
  accessToken: String,
  idToken: String,
  refreshToken: String,
  scope: String,
  tokenType: String,
  expiresIn: Long,
  authUserPrimaryKey: Long,
  createdAt: Date
) extends OpenIDConnectTokenTrait

object OpenIDConnectToken {

  private val selectColumns =
    fr"""SELECT accesstoken, idtoken, refreshtoken, scope, tokentype, expiresin,
                authuserprimarykey, createdat
         FROM openidconnecttoken"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Long], Option[Long], Option[java.sql.Timestamp])

  private def fromRow(row: Row): OpenIDConnectToken = row match {
    case (accessToken, idToken, refreshToken, scope, tokenType, expiresIn, authUserPrimaryKey, createdAt) =>
      OpenIDConnectToken(accessToken.orNull, idToken.orNull, refreshToken.orNull, scope.orNull,
        tokenType.orNull, expiresIn.getOrElse(0L), authUserPrimaryKey.getOrElse(0L),
        createdAt.orNull)
  }

  def insert(
    tokenType: String, accessToken: String, idToken: String, refreshToken: String,
    scope: String, expiresIn: Long, authUserPrimaryKey: Long
  ): OpenIDConnectToken = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO openidconnecttoken
            (tokentype, accesstoken, idtoken, refreshtoken, scope, expiresin, authuserprimarykey, createdat, updatedat)
            VALUES ($tokenType, $accessToken, $idToken, $refreshToken, $scope, $expiresIn, $authUserPrimaryKey, $now, $now)"""
        .update.run)
    OpenIDConnectToken(accessToken, idToken, refreshToken, scope, tokenType, expiresIn, authUserPrimaryKey, now)
  }

  /** The newest token set for a user, or None when they have never logged in via OIDC. */
  def newestByAuthUserPrimaryKey(authUserPrimaryKey: Long): Option[OpenIDConnectToken] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE authuserprimarykey = $authUserPrimaryKey ORDER BY createdat DESC LIMIT 1")
        .query[Row].option
    ).map(fromRow)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM openidconnecttoken".update.run)
    ()
  }
}

object MappedOpenIDConnectTokensProvider extends OpenIDConnectTokensProvider {
  def createToken(tokenType: String,
                  accessToken: String,
                  idToken: String,
                  refreshToken: String,
                  scope: String,
                  expiresIn: Long,
                  authUserPrimaryKey: Long): Box[OpenIDConnectToken] = Box.tryo {
    OpenIDConnectToken.insert(tokenType.toString(), accessToken, idToken, refreshToken, scope, expiresIn, authUserPrimaryKey)
  }

  def getOpenIDConnectTokenByAuthUser(authUserPrimaryKey: Long) =
    OpenIDConnectToken.newestByAuthUserPrimaryKey(authUserPrimaryKey)
}
