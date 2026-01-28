package code.customer

import java.util.Date

import code.api.util.{APIUtil, OBPQueryParam}
import com.openbankproject.commons.model.{User, _}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import org.apache.pekko.pattern.pipe

import scala.collection.immutable.List
import scala.concurrent.Future


object CustomerX extends SimpleInjector {

  val customerProvider = new Inject(buildOne _) {}

  def buildOne: CustomerProvider = MappedCustomerProvider

}

trait CustomerProvider {
  def getCustomersAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]]
  
  def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]]

  def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Customer]]]
  def getCustomersByCustomerLegalName(bankId: BankId, legalName: String): Future[Box[List[Customer]]]

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

  def updateCustomerScaData(customerId: String, 
                            mobileNumber: Option[String], 
                            email: Option[String],
                            customerNumber: Option[String]): Future[Box[Customer]]
  
  def updateCustomerCreditData(customerId: String,
                               creditRating: Option[String],
                               creditSource: Option[String],
                               creditLimit: Option[AmountOfMoney]): Future[Box[Customer]]
  
  def updateCustomerGeneralData(customerId: String,
                                legalName: Option[String],
                                faceImage: Option[CustomerFaceImageTrait],
                                dateOfBirth: Option[Date],
                                relationshipStatus: Option[String],
                                dependents: Option[Int],
                                highestEducationAttained: Option[String],
                                employmentStatus: Option[String],
                                title: Option[String],
                                branchId: Option[String],
                                nameSuffix: Option[String]
                               ): Future[Box[Customer]]
  
  def bulkDeleteCustomers(): Boolean
  def populateMissingUUIDs(): Boolean
}