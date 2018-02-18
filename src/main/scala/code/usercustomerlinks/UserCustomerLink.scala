package code.usercustomerlinks

import java.util.Date

import code.api.util.APIUtil
import code.remotedata.RemotedataUserCustomerLinks
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}


object UserCustomerLink extends SimpleInjector {

  val userCustomerLink = new Inject(buildOne _) {}

  def buildOne: UserCustomerLinkProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserCustomerLinkProvider
      case true => RemotedataUserCustomerLinks     // We will use Akka as a middleware
    }

}

trait UserCustomerLinkProvider {
  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink]
  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinks: Box[List[UserCustomerLink]]
  def bulkDeleteUserCustomerLinks(): Boolean
}

class RemotedataUserCustomerLinkProviderCaseClass {
  case class createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean)
  case class getUserCustomerLinkByCustomerId(customerId: String)
  case class getUserCustomerLinksByUserId(userId: String)
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