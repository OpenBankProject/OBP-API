package code.customerinfo

import java.util.Date

import code.model.{BankId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object CustomerInfo extends SimpleInjector {

  val customerInfoProvider = new Inject(buildOne _) {}

  def buildOne: CustomerInfoProvider = MappedCustomerInfoProvider

}

trait CustomerInfoProvider {
  def getInfo(bankId : BankId, user : User) : Box[CustomerInfo]

  def getUser(bankId : BankId, customerId : String) : Box[User]
}

trait CustomerInfo {
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