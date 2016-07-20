package code.usercustomerlinks

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object UserCustomerLink extends SimpleInjector {

  val userCustomerLink = new Inject(buildOne _) {}

  def buildOne: UserCustomerLink = MappedUserCustomerLink

}

trait UserCustomerLink {
  def userCustomerLinkId: String
  def userId: String
  def customerId: String
  def dateInserted: Date
  def isActive: Boolean

  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getUserCustomerLink(customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinkByUserId(userId: String): List[UserCustomerLink]
  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinks: Box[List[UserCustomerLink]]
}