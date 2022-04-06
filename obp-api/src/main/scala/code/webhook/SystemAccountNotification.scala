package code.webhook

import code.api.util.OBPQueryParam
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object SystemAccountNotificationWebhookTrait extends SimpleInjector {
  val systemAccountNotificationWebhook = new Inject(buildOne _) {}

  def buildOne: SystemAccountNotificationWebhookProvider = MappedSystemAccountNotificationWebhookProvider
}


trait SystemAccountNotificationWebhookProvider {
  def getSystemAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[SystemAccountNotificationWebhookTrait]]

  def getSystemAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[SystemAccountNotificationWebhookTrait]]]

  def getSystemAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[SystemAccountNotificationWebhookTrait]]]

  def createSystemAccountNotificationWebhookFuture(
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String
  ): Future[Box[SystemAccountNotificationWebhookTrait]]

  def deleteSystemAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]]

}

trait SystemAccountNotificationWebhookTrait {

  def webhookId: String

  def triggerName: String

  def url: String

  def httpMethod: String

  def httpProtocol: String

  def createdByUserId: String

}