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

package code.webhook

import code.api.util.{APIUtil, DoobieUtil, _}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import scala.collection.immutable.List
import scala.concurrent.Future

/** A system-wide account-notification webhook registration. */
case class SystemAccountNotificationWebhook(
  webhookId: String,
  triggerName: String,
  url: String,
  httpMethod: String,
  httpProtocol: String,
  createdByUserId: String
) extends SystemAccountNotificationWebhookTrait

object SystemAccountNotificationWebhook {

  private val selectColumns =
    fr"""SELECT webhookid, triggername, url, httpmethod, httpprotocol, createdbyuserid
         FROM systemaccountnotificationwebhook"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String])

  private def fromRow(row: Row): SystemAccountNotificationWebhook = row match {
    case (webhookId, triggerName, url, httpMethod, httpProtocol, createdByUserId) =>
      SystemAccountNotificationWebhook(webhookId.orNull, triggerName.orNull, url.orNull,
        httpMethod.orNull, httpProtocol.orNull, createdByUserId.orNull)
  }

  private def query(condition: Fragment): List[SystemAccountNotificationWebhook] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(userId: String, triggerName: String, url: String, httpMethod: String,
             httpProtocol: String): SystemAccountNotificationWebhook = {
    val webhookId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO systemaccountnotificationwebhook
            (webhookid, triggername, url, httpmethod, httpprotocol, createdbyuserid,
             createdat, updatedat)
            VALUES ($webhookId, $triggerName, $url, $httpMethod, $httpProtocol, $userId,
             $now, $now)"""
        .update.run)
    SystemAccountNotificationWebhook(webhookId, triggerName, url, httpMethod, httpProtocol, userId)
  }

  def findById(webhookId: String): Box[SystemAccountNotificationWebhook] =
    query(fr"WHERE webhookid = $webhookId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByUserId(userId: String): List[SystemAccountNotificationWebhook] =
    query(fr"WHERE createdbyuserid = $userId ORDER BY updatedat DESC, id DESC")

  /** See MappedAccountWebhook.findAllFiltered for why the id ordering is explicit. */
  def findAllFiltered(queryParams: List[OBPQueryParam]): List[SystemAccountNotificationWebhook] = {
    val where = queryParams.collectFirst { case OBPUserId(value) => fr"WHERE createdbyuserid = $value" }
      .getOrElse(Fragment.empty)
    val limit = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offset = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    query(where ++ fr"ORDER BY id ASC" ++ limit ++ offset)
  }

  /** The delivery path: every system-level webhook registered for this trigger. */
  def findAllByTrigger(triggerName: String): List[SystemAccountNotificationWebhook] =
    query(fr"WHERE triggername = $triggerName ORDER BY id ASC")

  def delete(webhookId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM systemaccountnotificationwebhook WHERE webhookid = $webhookId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM systemaccountnotificationwebhook".update.run)
    ()
  }
}

object MappedSystemAccountNotificationWebhookProvider extends SystemAccountNotificationWebhookProvider {

  override def getSystemAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[SystemAccountNotificationWebhookTrait]] =
    Future(SystemAccountNotificationWebhook.findById(webhookId))

  override def getSystemAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[SystemAccountNotificationWebhookTrait]]] =
    Future(Full(SystemAccountNotificationWebhook.findAllByUserId(userId)))

  override def getSystemAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[SystemAccountNotificationWebhookTrait]]] =
    Future(Full(SystemAccountNotificationWebhook.findAllFiltered(queryParams)))

  override def createSystemAccountNotificationWebhookFuture(
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String,
  ): Future[Box[SystemAccountNotificationWebhookTrait]] =
    Future(Full(SystemAccountNotificationWebhook.insert(userId, triggerName, url, httpMethod,
      httpProtocol)))

  override def deleteSystemAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] =
    Future(SystemAccountNotificationWebhook.findById(webhookId)
      .map(_ => SystemAccountNotificationWebhook.delete(webhookId)))
}
