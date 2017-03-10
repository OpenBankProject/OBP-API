package code.usercustomerlinks

import java.util.Date

import net.liftweb.common.Box
import code.remotedata.RemotedataUserCustomerLinks
import net.liftweb.util.SimpleInjector


object UserCustomerLink extends SimpleInjector {

  val userCustomerLink = new Inject(buildOne _) {}

  //def buildOne: UserCustomerLinkProvider = MappedUserCustomerLinkProvider
  def buildOne: UserCustomerLinkProvider = RemotedataUserCustomerLinks

}

trait UserCustomerLinkProvider {
  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinkByUserId(userId: String): List[UserCustomerLink]
  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinks: Box[List[UserCustomerLink]]
  def bulkDeleteUserCustomerLinks(): Boolean
}

class RemotedataUserCustomerLinkProviderCaseClass {
  case class createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean)
  case class getUserCustomerLinkByCustomerId(customerId: String)
  case class getUserCustomerLinkByUserId(userId: String)
  case class getUserCustomerLink(userId: String, customerId: String)
  case class getUserCustomerLinks()
  case class bulkDeleteUserCustomerLinks()
}

object RemotedataUserCustomerLinkProviderCaseClass extends RemotedataUserCustomerLinkProviderCaseClass

trait UserCustomerLink {
  def userCustomerLinkId: String
  def userId: String
  def customerId: String
  def dateInserted: Date
  def isActive: Boolean
}