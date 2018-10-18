package code.webhook

import code.bankconnectors.OBPQueryParam
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object AccountWebhook extends SimpleInjector {
  val accountWebhook = new Inject(buildOne _) {}

  def buildOne: AccountWebhookProvider = MappedAccountWebhookProvider
}


trait AccountWebhookProvider {
  def getAccountWebhookByIdFuture(accountWebhookId: String): Future[Box[AccountWebhook]]
  def getAccountWebhooksByUserIdFuture(userId: String): Future[Box[List[AccountWebhook]]]
  def getAccountWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebhook]]]
  def createAccountWebhookFuture(bankId: String,
                                 accountId: String,
                                 userId: String,
                                 triggerName: String,
                                 url: String,
                                 httpMethod: String,
                                 isActive: Boolean
                                ): Future[Box[AccountWebhook]]
  def updateAccountWebhookFuture(accountWebhookId: String,
                                 isActive: Boolean
                                ): Future[Box[AccountWebhook]]
}

trait AccountWebhook {
  def accountWebhookId: String

  def bankId: String

  def accountId: String

  def triggerName: String

  def url: String

  def httpMethod: String

  def createdByUserId: String

  def isActive(): Boolean
}