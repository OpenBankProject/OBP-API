package code.customer

import java.util.Date

import code.api.util.{APIUtil, OBPQueryParam}
import code.remotedata.RemotedataCustomers
import com.openbankproject.commons.model.{User, _}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future


object Customer extends SimpleInjector {

  val customerProvider = new Inject(buildOne _) {}

  def buildOne: CustomerProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedCustomerProvider
      case true => RemotedataCustomers     // We will use Akka as a middleware
    }

}

trait CustomerProvider {
  def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]]

  def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer]

  def getCustomersByUserId(userId: String): List[Customer]

  def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]]

  def getCustomerByCustomerId(customerId: String): Box[Customer]

  def getCustomerByCustomerIdFuture(customerId: String): Future[Box[Customer]]

  def getBankIdByCustomerId(customerId: String): Box[String]

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId): Box[Customer]

  def getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId): Future[Box[Customer]]

  def getUser(bankId : BankId, customerNumber : String) : Box[User]

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String) : Boolean


  def addCustomer(bankId: BankId,
                  number: String,
                  legalName: String,
                  mobileNumber: String,
                  email: String,
                  faceImage:
                  CustomerFaceImageTrait,
                  dateOfBirth: Date,
                  relationshipStatus: String,
                  dependents: Int,
                  dobOfDependents: List[Date],
                  highestEducationAttained: String,
                  employmentStatus: String,
                  kycStatus: Boolean,
                  lastOkDate: Date,
                  creditRating: Option[CreditRatingTrait],
                  creditLimit: Option[AmountOfMoneyTrait],
                  title: String,      
                  branchId: String,   
                  nameSuffix: String   
                 ): Box[Customer]

  def bulkDeleteCustomers(): Boolean
  def populateMissingUUIDs(): Boolean
}

class RemotedataCustomerProviderCaseClasses {
  case class getCustomersFuture(bankId: BankId, queryParams: List[OBPQueryParam])
  case class getCustomerByUserId(bankId: BankId, userId: String)
  case class getCustomersByUserId(userId: String)
  case class getCustomersByUserIdFuture(userId: String)
  case class getCustomerByCustomerId(customerId: String)
  case class getCustomerByCustomerIdFuture(customerId: String)
  case class getBankIdByCustomerId(customerId: String)
  case class getCustomerByCustomerNumber(customerNumber: String, bankId : BankId)
  case class getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId)
  case class getUser(bankId : BankId, customerNumber : String)
  case class checkCustomerNumberAvailable(bankId : BankId, customerNumber : String)
  case class addCustomer(bankId: BankId,
                         number: String,
                         legalName: String,
                         mobileNumber: String,
                         email: String,
                         faceImage: CustomerFaceImageTrait,
                         dateOfBirth: Date,
                         relationshipStatus: String,
                         dependents: Int,
                         dobOfDependents: List[Date],
                         highestEducationAttained: String,
                         employmentStatus: String,
                         kycStatus: Boolean,
                         lastOkDate: Date,
                         creditRating: Option[CreditRatingTrait],
                         creditLimit: Option[AmountOfMoneyTrait],
                         title: String,     
                         branchId: String,  
                         nameSuffix: String
                        )
  case class bulkDeleteCustomers()
  case class populateMissingUUIDs()

}

object RemotedataCustomerProviderCaseClasses extends RemotedataCustomerProviderCaseClasses

