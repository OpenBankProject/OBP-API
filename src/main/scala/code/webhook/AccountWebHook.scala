package code.webhook

import code.bankconnectors.OBPQueryParam
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object AccountWebHook extends SimpleInjector {
  val accountWebHook = new Inject(buildOne _) {}

  def buildOne: AccountWebHookProvider = MappedAccountWebHookProvider
}


trait AccountWebHookProvider {
  def getAccountWebHookByIdFuture(accountWebHookId: String): Future[Box[AccountWebHook]]
  def getAccountWebHooksByUserIdFuture(userId: String): Future[Box[List[AccountWebHook]]]
  def getAccountWebHooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebHook]]]
  def createAccountWebHookFuture(bankId: String,
                                 accountId: String,
                                 userId: String,
                                 triggerName: String,
                                 url: String,
                                 httpMethod: String,
                                 isActive: Boolean
                                ): Future[Box[AccountWebHook]]
  def updateAccountWebHookFuture(accountWebHookId: String,
                                isActive: Boolean
                                ): Future[Box[AccountWebHook]]
}

trait AccountWebHook {
  def accountWebHookId: String

  def bankId: String

  def accountId: String

  def triggerName: String

  def url: String

  def httpMethod: String

  def createdByUserId: String

  def isActive(): Boolean
}