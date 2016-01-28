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

  def addCustomer(bankId: BankId, user: User, number: String, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImage,
                  dateOfBirth: Date,
                  relationshipStatus: String,
                  dependents: Int,
                  dobOfDependents: Array[Date],
                  highestEducationAttained: String,
                  employmentStatus: String,
                  kycStatus: Boolean,
                  lastOkDate: Date): Box[Customer]

}

trait Customer {
  def number : String
  def legalName : String
  def mobileNumber : String
  def email : String
  def faceImage : CustomerFaceImage
  def dateOfBirth: Date
  def relationshipStatus: String
  def dependents: Int
  def dobOfDependents: Array[Date]
  def highestEducationAttained: String
  def employmentStatus: String
  def kycStatus: Boolean
  def lastOkDate: Date
}

trait CustomerFaceImage {
  def url : String
  def date : Date
}

case class MockCustomerFaceImage(date : Date, url : String) extends CustomerFaceImage