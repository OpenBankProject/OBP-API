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

  def getCustomerByCustomerId(customerId: String): Box[Customer]

  def getBankIdByCustomerId(customerId: String): Box[String]

  def getCustomer(customerId: String, bankId : BankId): Box[Customer]

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
                  lastOkDate: Date,
                  creditRating: Option[CreditRating],
                  creditLimit: Option[AmountOfMoney]
                 ): Box[Customer]

}

trait Customer {
  def customerId : String // The UUID for the customer. To be used in URLs
  def bank : String
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
  def creditRating : CreditRating
  def creditLimit: AmountOfMoney
  def kycStatus: Boolean
  def lastOkDate: Date
}

trait CustomerFaceImage {
  def url : String
  def date : Date
}

trait AmountOfMoney {
  def currency: String
  def amount: String
}

trait CreditRating {
  def rating: String
  def source: String
}

case class MockCustomerFaceImage(date : Date, url : String) extends CustomerFaceImage
case class MockCreditRating(rating: String, source: String) extends CreditRating
case class MockCreditLimit(currency: String, amount: String) extends AmountOfMoney