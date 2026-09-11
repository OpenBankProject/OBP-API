package code.customeraccountlinks
import com.openbankproject.commons.model.CustomerAccountLinkTrait
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object CustomerAccountLinkX extends SimpleInjector {

  val customerAccountLink = new Inject(() => buildOne) {}

  def buildOne: CustomerAccountLinkProvider = DoobieCustomerAccountLinkProvider

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