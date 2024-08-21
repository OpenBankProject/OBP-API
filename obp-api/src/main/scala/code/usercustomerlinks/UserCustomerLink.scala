package code.usercustomerlinks

import java.util.Date

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future


object UserCustomerLink extends SimpleInjector {

  val userCustomerLink = new Inject(buildOne _) {}

  def buildOne: UserCustomerLinkProvider = MappedUserCustomerLinkProvider 

}

trait UserCustomerLinkProvider {
  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink]
  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink]
  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink]
  def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink]
  def getUserCustomerLinks: Box[List[UserCustomerLink]]
  def bulkDeleteUserCustomerLinks(): Boolean
  def deleteUserCustomerLink(userCustomerLinkId: String): Future[Box[Boolean]]
}

trait UserCustomerLink {
  def userCustomerLinkId: String
  def userId: String
  def customerId: String
  def dateInserted: Date
  def isActive: Boolean
}