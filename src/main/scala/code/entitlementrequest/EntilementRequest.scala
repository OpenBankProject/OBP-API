package code.entitlementrequest

import java.util.Date

import code.model.User
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object EntitlementRequest extends SimpleInjector {

  val entitlementRequest = new Inject(buildOne _) {}

  def buildOne: EntitlementRequestProvider = MappedEntitlementRequestsProvider
}

trait EntitlementRequestProvider {
  def addEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest]
  def addEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest]
  def getEntitlementRequestFuture(entitlementRequestId: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequestsFuture(): Future[Box[List[EntitlementRequest]]]
  def getEntitlementRequestsFuture(userId: String): Future[Box[List[EntitlementRequest]]]
  def deleteEntitlementRequestFuture(entitlementRequestId: String): Future[Box[Boolean]]
}

trait EntitlementRequest {
  def entitlementRequestId: String

  def bankId: String

  def user: Box[User]

  def roleName: String

  def created: Date
}
