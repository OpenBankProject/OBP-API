package code.usercustomerlinks

import java.util.Date
import code.model.{User, BankId}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

trait UserCustomerLink {
  def userId: String
  def customerId: String
  def bankId: String
  def dateInserted: Date
  def isActive: Boolean
}


object UserCustomerLink extends SimpleInjector {

  val userCustomerLinkProvider = new Inject(buildOne _) {}

  def buildOne: UserCustomerLinkProvider = MappedUserCustomerLinkProvider

}

trait UserCustomerLinkProvider {
  def createUserCustomerLink(userId: String, customerId: String, bankId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinks: Box[List[UserCustomerLink]]
}