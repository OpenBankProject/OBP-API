package code.webhook

import code.api.util.OBPQueryParam
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object BankAccountNotificationWebhookTrait extends SimpleInjector {
  val bankAccountNotificationWebhook = new Inject(buildOne _) {}

  def buildOne: BankAccountNotificationWebhookProvider = MappedBankAccountNotificationWebhookProvider
}


trait BankAccountNotificationWebhookProvider {
  def getBankAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[BankAccountNotificationWebhookTrait]]

  def getBankAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[BankAccountNotificationWebhookTrait]]]

  def getBankAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[BankAccountNotificationWebhookTrait]]]

  def createBankAccountNotificationWebhookFuture(
    bankId: String,
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String
  ): Future[Box[BankAccountNotificationWebhookTrait]]

  def deleteBankAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]]

}

trait BankAccountNotificationWebhookTrait extends SystemAccountNotificationWebhookTrait{
  def bankId: String
}