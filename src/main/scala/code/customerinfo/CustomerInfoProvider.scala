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
}

trait CustomerInfo {
  val number : String
  val legalName : String
  val mobileNumber : String
  val email : String
  val faceImage : CustomerFaceImage
}

trait CustomerFaceImage {
  val url : String
  val date : Date
}