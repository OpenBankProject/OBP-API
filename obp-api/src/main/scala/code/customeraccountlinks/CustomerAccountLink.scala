package code.customeraccountlinks

import code.api.util.APIUtil
import code.remotedata.RemotedataCustomerAccountLinks
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object CustomerAccountLinkTrait extends SimpleInjector {

  val customerAccountLink = new Inject(buildOne _) {}

  def buildOne: CustomerAccountLinkProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedCustomerAccountLinkProvider
      case true => RemotedataCustomerAccountLinks     // We will use Akka as a middleware
    }

}

trait CustomerAccountLinkProvider {
  def createCustomerAccountLink(customerId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getOrCreateCustomerAccountLink(customerId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinksByAccountId(accountId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLink(customerId: String, accountId: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait]
  def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]]
  def bulkDeleteCustomerAccountLinks(): Boolean
  def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]]
}

class RemotedataCustomerAccountLinkProviderCaseClass {
  case class createCustomerAccountLink(customerId: String, accountId: String, relationshipType: String)
  case class getOrCreateCustomerAccountLink(customerId: String, accountId: String, relationshipType: String)
  case class getCustomerAccountLinkByCustomerId(customerId: String)
  case class getCustomerAccountLinksByCustomerId(customerId: String)
  case class getCustomerAccountLinksByAccountId(accountId: String)
  case class getCustomerAccountLinkById(customerAccountLinkId: String)
  case class updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String)
  case class getCustomerAccountLink(customerId: String, accountId: String)
  case class getCustomerAccountLinks()
  case class bulkDeleteCustomerAccountLinks()
  case class deleteCustomerAccountLinkById(customerAccountLinkId: String)
}

object RemotedataCustomerAccountLinkProviderCaseClass extends RemotedataCustomerAccountLinkProviderCaseClass

trait CustomerAccountLinkTrait {
  def customerAccountLinkId: String
  def customerId: String
  def accountId: String
  def relationshipType: String
}