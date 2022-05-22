package code.webhook

import code.api.util._
import code.util.{AccountIdString, MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedBankAccountNotificationWebhookProvider extends BankAccountNotificationWebhookProvider {
  
  override def getBankAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[BankAccountNotificationWebhookTrait]] = {
    Future(
      BankAccountNotificationWebhook.find(
        By(BankAccountNotificationWebhook.WebhookId, webhookId)
      )
    )
  }
  
  override def getBankAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[BankAccountNotificationWebhookTrait]]] = {
    Future(
      Full(
        BankAccountNotificationWebhook.findAll(
          By(BankAccountNotificationWebhook.CreatedByUserId, userId),
          OrderBy(BankAccountNotificationWebhook.updatedAt, Descending)
        )
      )
    )
  }
  
  override def getBankAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[BankAccountNotificationWebhookTrait]]] = {
    val limit = queryParams.collectFirst { case OBPLimit(value) => MaxRows[BankAccountNotificationWebhook](value) }
    val offset = queryParams.collectFirst { case OBPOffset(value) => StartAt[BankAccountNotificationWebhook](value) }
    val userId = queryParams.collectFirst { case OBPUserId(value) => By(BankAccountNotificationWebhook.CreatedByUserId, value) }
    val optionalParams: Seq[QueryParam[BankAccountNotificationWebhook]] = Seq(limit.toSeq, offset.toSeq, userId.toSeq).flatten
    Future(
      Full(
        BankAccountNotificationWebhook.findAll(optionalParams: _*)
      )
    )
  }

  override def createBankAccountNotificationWebhookFuture(
    bankId: String,
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String,
  ): Future[Box[BankAccountNotificationWebhookTrait]] = {
    val createBankAccountNotificationWebhook = BankAccountNotificationWebhook.create
      .BankId(bankId)
      .CreatedByUserId(userId)
      .TriggerName(triggerName)
      .Url(url)
      .HttpMethod(httpMethod)
      .HttpProtocol(httpProtocol)
      .saveMe()
    Future(Full(createBankAccountNotificationWebhook))
  }

  override def deleteBankAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] = {
    Future{BankAccountNotificationWebhook.find(By(BankAccountNotificationWebhook.WebhookId, webhookId)).map(_.delete_!)}
  }

}

class BankAccountNotificationWebhook extends BankAccountNotificationWebhookTrait with LongKeyedMapper[BankAccountNotificationWebhook] with IdPK with CreatedUpdated {
  def getSingleton = BankAccountNotificationWebhook

  object WebhookId extends MappedUUID(this)
  object BankId extends UUIDString(this)
  object TriggerName extends MappedString(this, 64)
  object Url extends MappedString(this, 1024)
  object HttpMethod extends MappedString(this, 64)
  object HttpProtocol extends MappedString(this, 64)
  object CreatedByUserId extends UUIDString(this)

  def webhookId: String = WebhookId.get
  def bankId: String = BankId.get
  def triggerName: String = TriggerName.get
  def url: String = Url.get
  def httpMethod: String = HttpMethod.get
  def httpProtocol: String = HttpProtocol.get
  def createdByUserId: String = CreatedByUserId.get
}

object BankAccountNotificationWebhook extends BankAccountNotificationWebhook with LongKeyedMetaMapper[BankAccountNotificationWebhook] {
  override def dbIndexes = UniqueIndex(WebhookId) :: super.dbIndexes
}