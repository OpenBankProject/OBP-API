package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.util.OBPQueryParam
import code.customer._
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future

object RemotedataCustomers extends ObpActorInit with CustomerProvider {

  val cc = RemotedataCustomerProviderCaseClasses

  def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    (actor ? cc.getCustomersFuture(bankId, queryParams)).mapTo[Box[List[Customer]]]
  
  def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Customer]]] =
    (actor ? cc.getCustomersByCustomerPhoneNumber(bankId, phoneNumber)).mapTo[Box[List[Customer]]]

  def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer] = getValueFromFuture(
    (actor ? cc.getCustomerByUserId(bankId, userId)).mapTo[Box[Customer]]
  )

  def getCustomersByUserId(userId: String): List[Customer] = getValueFromFuture(
    (actor ? cc.getCustomersByUserId(userId)).mapTo[List[Customer]]
  )

  def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]] =
    (actor ? cc.getCustomersByUserIdFuture(userId)).mapTo[Box[List[Customer]]]

  def getCustomerByCustomerIdFuture(customerId: String): Future[Box[Customer]] =
    (actor ? cc.getCustomerByCustomerIdFuture(customerId)).mapTo[Box[Customer]]

  def getCustomerByCustomerId(customerId: String) : Box[Customer] = getValueFromFuture(
    (actor ? cc.getCustomerByCustomerId(customerId)).mapTo[Box[Customer]]
  )

  def getBankIdByCustomerId(customerId: String) : Box[String] = getValueFromFuture(
    (actor ? cc.getBankIdByCustomerId(customerId)).mapTo[Box[String]]
  )

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId) : Box[Customer] = getValueFromFuture(
    (actor ? cc.getCustomerByCustomerNumber(customerNumber, bankId)).mapTo[Box[Customer]]
  )

  def getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId): Future[Box[Customer]] =
    (actor ? cc.getCustomerByCustomerNumberFuture(customerNumber, bankId)).mapTo[Box[Customer]]

  def getUser(bankId : BankId, customerNumber : String) : Box[User] = getValueFromFuture(
    (actor ? cc.getUser(bankId, customerNumber)).mapTo[Box[User]]
  )

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String): Boolean = getValueFromFuture(
    (actor ? cc.checkCustomerNumberAvailable(bankId, customerNumber)).mapTo[Boolean]
  )

  def addCustomer(bankId: BankId,
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
                 ) : Box[Customer] = getValueFromFuture(
    (actor ? cc.addCustomer(
                                              bankId = bankId,
                                              number = number,
                                              legalName = legalName,
                                              mobileNumber = mobileNumber,
                                              email = email,
                                              faceImage = faceImage,
                                              dateOfBirth = dateOfBirth,
                                              relationshipStatus = relationshipStatus,
                                              dependents = dependents,
                                              dobOfDependents = dobOfDependents,
                                              highestEducationAttained = highestEducationAttained,
                                              employmentStatus = employmentStatus,
                                              kycStatus = kycStatus,
                                              lastOkDate = lastOkDate,
                                              creditRating = creditRating,
                                              creditLimit = creditLimit,
                                              title = title,
                                              branchId = branchId,
                                              nameSuffix= nameSuffix
                                            )).mapTo[Box[Customer]]
  )

  def updateCustomerScaData(customerId: String,
                            mobileNumber: Option[String],
                            email: Option[String],
                            customerNumber: Option[String]): Future[Box[Customer]] = 
    (actor ? cc.updateCustomerScaData(customerId, mobileNumber, email, customerNumber)).mapTo[Box[Customer]]
  
  def updateCustomerCreditData(customerId: String,
                               creditRating: Option[String],
                               creditSource: Option[String],
                               creditLimit: Option[AmountOfMoney]): Future[Box[Customer]] = 
    (actor ? cc.updateCustomerCreditData(customerId, creditRating, creditSource, creditLimit)).mapTo[Box[Customer]]
  
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
                                nameSuffix: Option[String]): Future[Box[Customer]] = 
    (actor ? cc.updateCustomerGeneralData(
      customerId, 
      legalName, 
      faceImage,
      dateOfBirth,
      relationshipStatus,
      dependents,
      highestEducationAttained,
      employmentStatus,
      title,
      branchId,
      nameSuffix
    )).mapTo[Box[Customer]]

  def bulkDeleteCustomers(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteCustomers()).mapTo[Boolean]
  )

  def populateMissingUUIDs(): Boolean = getValueFromFuture(
    (actor ? cc.populateMissingUUIDs()).mapTo[Boolean]
  )


}
