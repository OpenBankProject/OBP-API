package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.customeraccountlinks.{RemotedataCustomerAccountLinkProviderCaseClass, CustomerAccountLinkTrait, CustomerAccountLinkProvider}
import net.liftweb.common._
import scala.concurrent.Future


object RemotedataCustomerAccountLinks extends ObpActorInit with CustomerAccountLinkProvider {

  val cc = RemotedataCustomerAccountLinkProviderCaseClass

  def createCustomerAccountLink(customerId: String, accountId: String, relationshipType: String) : Box[CustomerAccountLinkTrait] =  getValueFromFuture(
    (actor ? cc.createCustomerAccountLink(accountId, customerId, relationshipType)).mapTo[Box[CustomerAccountLinkTrait]]
  )
  
  def getOCreateCustomerAccountLink(customerId: String, accountId: String, relationshipType: String) : Box[CustomerAccountLinkTrait] =  getValueFromFuture(
    (actor ? cc.getOCreateCustomerAccountLink(accountId, customerId, relationshipType)).mapTo[Box[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinkByCustomerId(customerId)).mapTo[Box[CustomerAccountLinkTrait]]
  )
  def getCustomerAccountLinksByCustomerId(customerId: String): List[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinksByCustomerId(customerId)).mapTo[List[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLinksByAccountId(accountId: String): List[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinksByAccountId(accountId)).mapTo[List[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLink(customerId: String, accountId: String): Box[CustomerAccountLinkTrait] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLink(accountId, customerId)).mapTo[Box[CustomerAccountLinkTrait]]
  )

  def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]] = getValueFromFuture(
    (actor ? cc.getCustomerAccountLinks()).mapTo[Box[List[CustomerAccountLinkTrait]]]
  )

  def bulkDeleteCustomerAccountLinks(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteCustomerAccountLinks()).mapTo[Boolean]
  )
  def deleteCustomerAccountLink(customerAccountLinkId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteCustomerAccountLink(customerAccountLinkId)).mapTo[Box[Boolean]]
  

}
