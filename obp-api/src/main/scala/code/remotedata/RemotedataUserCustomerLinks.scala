package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.usercustomerlinks.{RemotedataUserCustomerLinkProviderCaseClass, UserCustomerLink, UserCustomerLinkProvider}
import net.liftweb.common._


object RemotedataUserCustomerLinks extends ObpActorInit with UserCustomerLinkProvider {

  val cc = RemotedataUserCustomerLinkProviderCaseClass

  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean) : Box[UserCustomerLink] =  getValueFromFuture(
    (actor ? cc.createUserCustomerLink(userId, customerId, dateInserted, isActive)).mapTo[Box[UserCustomerLink]]
  )

  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] = getValueFromFuture(
    (actor ? cc.getUserCustomerLinkByCustomerId(customerId)).mapTo[Box[UserCustomerLink]]
  )

  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] = getValueFromFuture(
    (actor ? cc.getUserCustomerLinksByUserId(userId)).mapTo[List[UserCustomerLink]]
  )

  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink] = getValueFromFuture(
    (actor ? cc.getUserCustomerLink(userId, customerId)).mapTo[Box[UserCustomerLink]]
  )

  def getUserCustomerLinks: Box[List[UserCustomerLink]] = getValueFromFuture(
    (actor ? cc.getUserCustomerLinks()).mapTo[Box[List[UserCustomerLink]]]
  )

  def bulkDeleteUserCustomerLinks(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteUserCustomerLinks()).mapTo[Boolean]
  )

}
