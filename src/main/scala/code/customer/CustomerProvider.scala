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

  def getUser(bankId : BankId, customerNumber : String) : Box[User]

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String) : Boolean


  def addCustomer(bankId: BankId, user: User, number: String, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImage,
                  dateOfBirth: Date,
                  relationshipStatus: String,
                  dependents: Int,
                  dobOfDependents: List[Date],
                  highestEducationAttained: String,
                  employmentStatus: String,
                  kycStatus: Boolean,
                  lastOkDate: Date): Box[Customer]

}

trait Customer {
  def customerId : String // The UUID for the customer. To be used in URLs
  def number : String // The Customer number i.e. the bank identifier for the customer.
  def legalName : String
  def mobileNumber : String
  def email : String
  def faceImage : CustomerFaceImage
  def dateOfBirth: Date
  def relationshipStatus: String
  def dependents: Int
  def dobOfDependents: List[Date]
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