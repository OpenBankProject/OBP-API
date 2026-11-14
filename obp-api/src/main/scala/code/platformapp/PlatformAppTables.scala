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

package code.platformapp

import java.util.Date

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

object PlatformAppDbProvider extends PlatformAppProvider {

  override def createPlatformApp(consumerId: String, label: String, markedByUserId: String): Box[PlatformAppTrait] =
    tryo {
      val now = new Date()
      PlatformApp.insert(PlatformApp(
        platformAppId = java.util.UUID.randomUUID().toString,
        consumerId = consumerId,
        label = label,
        markedByUserId = markedByUserId,
        createdAt = now,
        updatedAt = now,
        declaredAtRaw = null,
        declaredVersionRaw = null))
    }

  override def getPlatformApp(consumerId: String): Box[PlatformAppTrait] =
    PlatformApp.findByConsumerId(consumerId)

  override def getPlatformApps(): Box[List[PlatformAppTrait]] =
    tryo { PlatformApp.findAllOrderedByLabel() }

  override def deletePlatformApp(consumerId: String): Box[Boolean] =
    PlatformApp.findByConsumerId(consumerId).flatMap { app =>
      tryo {
        // One transaction: the declaration goes with the mark.
        DoobieUtil.runUpdate(
          PlatformAppRequiredScope.deleteByConsumerIdIO(consumerId)
            .flatMap(_ => PlatformApp.deleteIO(app.platformAppId))) > 0
      }
    }

  override def declareRequiredScopes(
    consumerId: String,
    version: Option[String],
    scopes: List[PlatformAppRequiredScopeInput]
  ): Box[PlatformAppTrait] =
    PlatformApp.findByConsumerId(consumerId).flatMap { app =>
      tryo {
        // One transaction: a reader never sees the old declaration half replaced.
        val now = new Date()
        val declared = app.copy(declaredAtRaw = now, declaredVersionRaw = version.getOrElse(""), updatedAt = now)
        val replaceScopes = scopes.foldLeft(PlatformAppRequiredScope.deleteByConsumerIdIO(consumerId)) { (io, s) =>
          io.flatMap(_ => PlatformAppRequiredScope.insertIO(consumerId, s))
        }
        DoobieUtil.runUpdate(replaceScopes.flatMap(_ => PlatformApp.updateDeclarationIO(declared)))
        declared
      }
    }

  override def getRequiredScopes(consumerId: String): Box[List[PlatformAppRequiredScopeTrait]] =
    tryo {
      PlatformAppRequiredScope.findByConsumerId(consumerId)
    }
}

/**
 * A Consumer marked as a platform app. Upstream declares this as a Lift Mapper entity; here it is a
 * Doobie store over the platform_app table the changelog creates (db.changelog-develop-merge-3.yaml),
 * unique on consumer_id. created_at is when it was marked.
 */
case class PlatformApp(
  platformAppId: String,
  consumerId: String,
  label: String,
  markedByUserId: String,
  createdAt: Date,
  updatedAt: Date,
  private[platformapp] val declaredAtRaw: Date,
  private[platformapp] val declaredVersionRaw: String
) extends PlatformAppTrait {
  override def markedAt: Date = createdAt
  override def declaredAt: Option[Date] = Option(declaredAtRaw)
  override def declaredVersion: Option[String] = Option(declaredVersionRaw).filter(_.nonEmpty)
}

object PlatformApp {

  private val selectColumns =
    fr"""SELECT platform_app_id, consumer_id, label, marked_by_user_id, created_at, updated_at, declared_at,
                declared_version
         FROM platform_app"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[String])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def ts(d: Date): Option[java.sql.Timestamp] = Option(d).map(x => new java.sql.Timestamp(x.getTime))

  private def fromRow(r: Row): PlatformApp =
    PlatformApp(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, readDate(r._5), readDate(r._6),
      readDate(r._7), r._8.orNull)

  private def query(condition: Fragment): List[PlatformApp] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findByConsumerId(consumerId: String): Box[PlatformApp] =
    query(fr"WHERE consumer_id = ${Option(consumerId)} ORDER BY id ASC").headOption match {
      case Some(app) => Full(app)
      case None => Empty
    }

  def findAllOrderedByLabel(): List[PlatformApp] = query(fr"ORDER BY label ASC, id ASC")

  def insert(app: PlatformApp): PlatformApp = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO platform_app (platform_app_id, consumer_id, label, marked_by_user_id, created_at, updated_at,
              declared_at, declared_version)
            VALUES (${Option(app.platformAppId)}, ${Option(app.consumerId)}, ${Option(app.label)},
              ${Option(app.markedByUserId)}, ${ts(app.createdAt)}, ${ts(app.updatedAt)}, ${ts(app.declaredAtRaw)},
              ${Option(app.declaredVersionRaw)})""".update.run)
    app
  }

  private[platformapp] def updateDeclarationIO(app: PlatformApp): ConnectionIO[Int] =
    sql"""UPDATE platform_app SET declared_at = ${ts(app.declaredAtRaw)},
            declared_version = ${Option(app.declaredVersionRaw)}, updated_at = ${ts(app.updatedAt)}
          WHERE platform_app_id = ${Option(app.platformAppId)}""".update.run

  private[platformapp] def deleteIO(platformAppId: String): ConnectionIO[Int] =
    sql"DELETE FROM platform_app WHERE platform_app_id = ${Option(platformAppId)}".update.run

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM platform_app".update.run)
    ()
  }
}

/**
 * A Scope a platform app declared it needs. Upstream declares this as a Lift Mapper entity; here it is
 * a row of the platform_app_required_scope table the changelog creates, with the store below.
 */
case class PlatformAppRequiredScope(
  consumerId: String,
  roleName: String,
  bankId: String,
  neededFor: String,
  isOptional: Boolean
) extends PlatformAppRequiredScopeTrait

object PlatformAppRequiredScope {

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[Boolean])

  def findByConsumerId(consumerId: String): List[PlatformAppRequiredScope] =
    DoobieUtil.runQuery(
      sql"""SELECT consumer_id, role_name, bank_id, needed_for, is_optional FROM platform_app_required_scope
            WHERE consumer_id = ${Option(consumerId)} ORDER BY id ASC""".query[Row].to[List]).map {
      case (c, r, b, n, o) => PlatformAppRequiredScope(c.orNull, r.orNull, b.orNull, n.orNull, o.getOrElse(false))
    }

  private[platformapp] def insertIO(consumerId: String, s: PlatformAppRequiredScopeInput): ConnectionIO[Int] =
    sql"""INSERT INTO platform_app_required_scope (consumer_id, role_name, bank_id, needed_for, is_optional)
          VALUES (${Option(consumerId)}, ${Option(s.roleName)}, ${Option(s.bankId)}, ${Option(s.neededFor)},
            ${s.isOptional})""".update.run

  private[platformapp] def deleteByConsumerIdIO(consumerId: String): ConnectionIO[Int] =
    sql"DELETE FROM platform_app_required_scope WHERE consumer_id = ${Option(consumerId)}".update.run

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM platform_app_required_scope".update.run)
    ()
  }
}
