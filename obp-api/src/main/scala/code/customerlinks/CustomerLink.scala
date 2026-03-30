package code.customerlinks

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object CustomerLinkX extends SimpleInjector {

  val customerLink = new Inject(buildOne _) {}

  def buildOne: CustomerLinkProvider = MappedCustomerLinkProvider

}

trait CustomerLinkProvider {
  def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLink]
  def getCustomerLinkById(customerLinkId: String): Box[CustomerLink]
  def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLink]]
  def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLink]]
  def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLink]
  def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]]
  def bulkDeleteCustomerLinks(): Boolean
}

trait CustomerLink {
  def customerLinkId: String
  def bankId: String
  def customerId: String
  def otherBankId: String
  def otherCustomerId: String
  def relationshipTo: String
  def dateInserted: Date
  def dateUpdated: Date
}
