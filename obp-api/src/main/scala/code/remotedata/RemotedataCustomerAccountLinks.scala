package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.customeraccountlinks.{RemotedataCustomerAccountLinkProviderCaseClass, CustomerAccountLinkTrait, CustomerAccountLinkProvider}
import net.liftweb.common._
import scala.concurrent.Future


object RemotedataCustomerAccountLinks extends ObpActorInit with CustomerAccountLinkProvider {

  val cc = RemotedataCustomerAccountLinkProviderCaseClass

  def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String) : Box[CustomerAccountLinkTrait] =  getValueFromFuture(
    (actor ? cc.createCustomerAccountLink(customerId, bankId: String, accountId: String,  relationshipType)).mapTo[Box[CustomerAccountLinkTrait]]
  )
  
  def getOrCreateCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String) : Box[CustomerAccountLinkTrait] =  getValueFromFuture(
    (actor ? cc.getOrCreateCustomerAccountLink(customerId, bankId, accountId, relationshipType)).mapTo[Box[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinkByCustomerId(customerId)).mapTo[Box[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String)).mapTo[Box[List[CustomerAccountLinkTrait]]]
  )
  def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinksByCustomerId(customerId)).mapTo[Box[List[CustomerAccountLinkTrait]]]
  )

  def getCustomerAccountLinksByAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinksByAccountId(bankId, accountId)).mapTo[Box[List[CustomerAccountLinkTrait]]]
  )

  def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinkById(customerAccountLinkId)).mapTo[Box[CustomerAccountLinkTrait]]
  )

  def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String)).mapTo[Box[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinks()).mapTo[Box[List[CustomerAccountLinkTrait]]]
  )

  def bulkDeleteCustomerAccountLinks(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteCustomerAccountLinks()).mapTo[Boolean]
  )
  def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteCustomerAccountLinkById(customerAccountLinkId)).mapTo[Box[Boolean]]
  

}
