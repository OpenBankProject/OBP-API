package code.remotedata

import java.util.Date
import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.usercustomerlinks.{RemotedataUserCustomerLinkProviderCaseClass, UserCustomerLink, UserCustomerLinkProvider}
import net.liftweb.common._


object RemotedataUserCustomerLinks extends ObpActorInit with UserCustomerLinkProvider {

  val cc = RemotedataUserCustomerLinkProviderCaseClass

  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean) : Box[UserCustomerLink] =
    extractFutureToBox(actor ? cc.createUserCustomerLink(userId, customerId, dateInserted, isActive))

  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] =
    extractFutureToBox(actor ? cc.getUserCustomerLinkByCustomerId(customerId))

  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] =
    extractFuture(actor ? cc.getUserCustomerLinksByUserId(userId))

  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink] =
    extractFutureToBox(actor ? cc.getUserCustomerLink(userId, customerId))

  def getUserCustomerLinks: Box[List[UserCustomerLink]] =
    extractFutureToBox(actor ? cc.getUserCustomerLinks())

  def bulkDeleteUserCustomerLinks(): Boolean =
    extractFuture(actor ? cc.bulkDeleteUserCustomerLinks())

}
