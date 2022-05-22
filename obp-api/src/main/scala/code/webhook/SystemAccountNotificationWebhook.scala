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