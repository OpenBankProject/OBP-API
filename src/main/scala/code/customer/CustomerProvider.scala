package code.customer

import java.util.Date

import code.model.{BankId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object Customer extends SimpleInjector {

  val customerProvider = new Inject(buildOne _) {}

  def buildOne: CustomerProvider = MappedCustomerProvider

}

trait CustomerProvider {
  def getCustomer(bankId : BankId, user : User) : Box[Customer]

  def getUser(bankId : BankId, customerId : String) : Box[User]
}

trait Customer {
  def number : String
  def legalName : String
  def mobileNumber : String
  def email : String
  def faceImage : CustomerFaceImage
}

trait CustomerFaceImage {
  def url : String
  def date : Date
}