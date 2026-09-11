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
