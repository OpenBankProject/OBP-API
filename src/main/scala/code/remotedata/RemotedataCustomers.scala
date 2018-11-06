package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.bankconnectors.OBPQueryParam
import code.customer.{AmountOfMoneyTrait, CreditRatingTrait, Customer, CustomerFaceImageTrait, CustomerProvider, RemotedataCustomerProviderCaseClasses}
import code.model._
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future

object RemotedataCustomers extends ObpActorInit with CustomerProvider {

  val cc = RemotedataCustomerProviderCaseClasses

  def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    (actor ? cc.getCustomersFuture(bankId, queryParams)).mapTo[Box[List[Customer]]]

  def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer] =
    extractFutureToBox(actor ? cc.getCustomerByUserId(bankId, userId))

  def getCustomersByUserId(userId: String): List[Customer] =
    extractFuture(actor ? cc.getCustomersByUserId(userId))

  def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]] =
    (actor ? cc.getCustomersByUserIdFuture(userId)).mapTo[Box[List[Customer]]]

  def getCustomerByCustomerIdFuture(customerId: String): Future[Box[Customer]] =
    (actor ? cc.getCustomerByCustomerIdFuture(customerId)).mapTo[Box[Customer]]

  def getCustomerByCustomerId(customerId: String) : Box[Customer] =
    extractFutureToBox(actor ? cc.getCustomerByCustomerId(customerId))

  def getBankIdByCustomerId(customerId: String) : Box[String] =
    extractFutureToBox(actor ? cc.getBankIdByCustomerId(customerId))

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId) : Box[Customer] =
    extractFutureToBox(actor ? cc.getCustomerByCustomerNumber(customerNumber, bankId))

  def getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId): Future[Box[Customer]] =
    (actor ? cc.getCustomerByCustomerNumberFuture(customerNumber, bankId)).mapTo[Box[Customer]]

  def getUser(bankId : BankId, customerNumber : String) : Box[User] =
    extractFutureToBox(actor ? cc.getUser(bankId, customerNumber))

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String): Boolean =
    extractFuture(actor ? cc.checkCustomerNumberAvailable(bankId, customerNumber))

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
                 ) : Box[Customer] =
    extractFutureToBox(actor ? cc.addCustomer(
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
                                            ))

  def bulkDeleteCustomers(): Boolean =
    extractFuture(actor ? cc.bulkDeleteCustomers())

  def populateMissingUUIDs(): Boolean =
    extractFuture(actor ? cc.populateMissingUUIDs())


}
