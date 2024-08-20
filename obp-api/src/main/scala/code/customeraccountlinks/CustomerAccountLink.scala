package code.customeraccountlinks

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object CustomerAccountLinkTrait extends SimpleInjector {

  val customerAccountLink = new Inject(buildOne _) {}

  def buildOne: CustomerAccountLinkProvider = MappedCustomerAccountLinkProvider

}

trait CustomerAccountLinkProvider {
  def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getOrCreateCustomerAccountLink(customerId: String, bankId: String,  accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinksByAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]]
  def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait]
  def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait]
  def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]]
  def bulkDeleteCustomerAccountLinks(): Boolean
  def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]]
}

trait CustomerAccountLinkTrait {
  def customerAccountLinkId: String
  def customerId: String
  def bankId: String
  def accountId: String
  def relationshipType: String
}