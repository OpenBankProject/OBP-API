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
  def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLinkTrait]
  def getCustomerLinkById(customerLinkId: String): Box[CustomerLinkTrait]
  def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLinkTrait]]
  def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLinkTrait]]
  def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLinkTrait]
  def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]]
  def bulkDeleteCustomerLinks(): Boolean
}

trait CustomerLinkTrait {
  def customerLinkId: String
  def bankId: String
  def customerId: String
  def otherBankId: String
  def otherCustomerId: String
  def relationshipTo: String
  def dateInserted: Date
  def dateUpdated: Date
}
