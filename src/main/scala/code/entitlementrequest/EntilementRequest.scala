package code.entitlementrequest

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
  def getEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]]
  def getEntitlementRequestsFuture(): Future[Box[List[EntitlementRequest]]]
  def getEntitlementRequestsFuture(userId: String): Future[Box[List[EntitlementRequest]]]
}

trait EntitlementRequest {
  def entitlementId: String

  def bankId: String

  def userId: String

  def roleName: String
}
