package code.customer

import java.lang
import java.util.Date

import code.model.{BankId, User}
import code.remotedata.RemotedataCustomers
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}
import scala.concurrent.Future

object Customer extends SimpleInjector {

  val customerProvider = new Inject(buildOne _) {}

  def buildOne: CustomerProvider =
    Props.getBool("use_akka", false) match {
      case false  => MappedCustomerProvider
      case true => RemotedataCustomers     // We will use Akka as a middleware
    }

}

trait CustomerProvider {
  def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer]

  def getCustomersByUserId(userId: String): List[Customer]

  def getCustomersByUserIdBox(userId: String): Box[List[Customer]]

  def getCustomerByCustomerId(customerId: String): Box[Customer]

  def getBankIdByCustomerId(customerId: String): Box[String]

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId): Box[Customer]

  def getUser(bankId : BankId, customerNumber : String) : Box[User]

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String) : Boolean


  def addCustomer(bankId: BankId,
                  number: String,
                  legalName: String,
                  mobileNumber: String,
                  email: String,
                  faceImage:
                  CustomerFaceImage,
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

  def bulkDeleteCustomers(): Boolean

}

class RemotedataCustomerProviderCaseClasses {
  case class getCustomerByUserId(bankId: BankId, userId: String)
  case class getCustomersByUserId(userId: String)
  case class getCustomersByUserIdFuture(userId: String)
  case class getCustomerByCustomerId(customerId: String)
  case class getBankIdByCustomerId(customerId: String)
  case class getCustomerByCustomerNumber(customerNumber: String, bankId : BankId)
  case class getUser(bankId : BankId, customerNumber : String)
  case class checkCustomerNumberAvailable(bankId : BankId, customerNumber : String)
  case class addCustomer(bankId: BankId,
                         number: String,
                         legalName: String,
                         mobileNumber: String,
                         email: String,
                         faceImage: CustomerFaceImage,
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
                        )
  case class bulkDeleteCustomers()

}

object RemotedataCustomerProviderCaseClasses extends RemotedataCustomerProviderCaseClasses

trait Customer {
  def customerId : String // The UUID for the customer. To be used in URLs
  def bankId : String
  def number : String // The Customer number i.e. the bank identifier for the customer.
  def legalName : String
  def mobileNumber : String
  def email : String
  def faceImage : CustomerFaceImage
  def dateOfBirth: Date
  def relationshipStatus: String
  def dependents: Integer
  def dobOfDependents: List[Date]
  def highestEducationAttained: String
  def employmentStatus: String
  def creditRating : CreditRating
  def creditLimit: AmountOfMoney
  def kycStatus: lang.Boolean
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