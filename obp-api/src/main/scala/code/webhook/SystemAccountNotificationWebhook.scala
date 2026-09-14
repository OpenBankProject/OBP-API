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

import code.api.util._
import code.util.{AccountIdString, MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedSystemAccountNotificationWebhookProvider extends SystemAccountNotificationWebhookProvider {
  
  override def getSystemAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[SystemAccountNotificationWebhookTrait]] = {
    Future(
      SystemAccountNotificationWebhook.find(
        By(SystemAccountNotificationWebhook.WebhookId, webhookId)
      )
    )
  }
  
  override def getSystemAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[SystemAccountNotificationWebhookTrait]]] = {
    Future(
      Full(
        SystemAccountNotificationWebhook.findAll(
          By(SystemAccountNotificationWebhook.CreatedByUserId, userId),
          OrderBy(SystemAccountNotificationWebhook.updatedAt, Descending)
        )
      )
    )
  }
  
  override def getSystemAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[SystemAccountNotificationWebhookTrait]]] = {
    val limit = queryParams.collectFirst { case OBPLimit(value) => MaxRows[SystemAccountNotificationWebhook](value) }
    val offset = queryParams.collectFirst { case OBPOffset(value) => StartAt[SystemAccountNotificationWebhook](value) }
    val userId = queryParams.collectFirst { case OBPUserId(value) => By(SystemAccountNotificationWebhook.CreatedByUserId, value) }
    val optionalParams: Seq[QueryParam[SystemAccountNotificationWebhook]] = Seq(limit.toSeq, offset.toSeq, userId.toSeq).flatten
    Future(
      Full(
        SystemAccountNotificationWebhook.findAll(optionalParams: _*)
      )
    )
  }

  override def createSystemAccountNotificationWebhookFuture(
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String,
  ): Future[Box[SystemAccountNotificationWebhookTrait]] = {
    val createSystemAccountNotificationWebhook = SystemAccountNotificationWebhook.create
      .CreatedByUserId(userId)
      .TriggerName(triggerName)
      .Url(url)
      .HttpMethod(httpMethod)
      .HttpProtocol(httpProtocol)
      .saveMe()
    Future(Full(createSystemAccountNotificationWebhook))
  }

  override def deleteSystemAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] = {
    Future{SystemAccountNotificationWebhook.find(By(SystemAccountNotificationWebhook.WebhookId, webhookId)).map(_.delete_!)}
  }

}

class SystemAccountNotificationWebhook extends SystemAccountNotificationWebhookTrait with LongKeyedMapper[SystemAccountNotificationWebhook] with IdPK with CreatedUpdated {
  def getSingleton = SystemAccountNotificationWebhook

  object WebhookId extends MappedUUID(this)
  object TriggerName extends MappedString(this, 64)
  object Url extends MappedString(this, 1024)
  object HttpMethod extends MappedString(this, 64)
  object HttpProtocol extends MappedString(this, 64)
  object CreatedByUserId extends UUIDString(this)

  def webhookId: String = WebhookId.get
  def triggerName: String = TriggerName.get
  def url: String = Url.get
  def httpMethod: String = HttpMethod.get
  def httpProtocol: String = HttpProtocol.get
  def createdByUserId: String = CreatedByUserId.get
}

object SystemAccountNotificationWebhook extends SystemAccountNotificationWebhook with LongKeyedMetaMapper[SystemAccountNotificationWebhook] {
  override def dbIndexes = UniqueIndex(WebhookId) :: super.dbIndexes
}