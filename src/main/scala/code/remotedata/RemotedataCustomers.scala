package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.customer.{AmountOfMoney, CreditRating, Customer, CustomerFaceImage, CustomerProvider, RemotedataCustomerProviderCaseClasses}
import code.model._
import net.liftweb.common.Box

import scala.concurrent.Future

object RemotedataCustomers extends ObpActorInit with CustomerProvider {

  val cc = RemotedataCustomerProviderCaseClasses

  def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer] =
    extractFutureToBox(actor ? cc.getCustomerByUserId(bankId, userId))

  def getCustomersByUserId(userId: String): List[Customer] =
    extractFuture(actor ? cc.getCustomersByUserId(userId))

  def getCustomersByUserIdFuture(userId: String): Future[List[Customer]] = (actor ? cc.getCustomersByUserIdFuture(userId)).mapTo[List[Customer]]

  def getCustomerByCustomerId(customerId: String) : Box[Customer] =
    extractFutureToBox(actor ? cc.getCustomerByCustomerId(customerId))

  def getBankIdByCustomerId(customerId: String) : Box[String] =
    extractFutureToBox(actor ? cc.getBankIdByCustomerId(customerId))

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId) : Box[Customer] =
    extractFutureToBox(actor ? cc.getCustomerByCustomerNumber(customerNumber, bankId))

  def getUser(bankId : BankId, customerNumber : String) : Box[User] =
    extractFutureToBox(actor ? cc.getUser(bankId, customerNumber))

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String): Boolean =
    extractFuture(actor ? cc.checkCustomerNumberAvailable(bankId, customerNumber))

  def addCustomer(bankId: BankId,
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
                                              creditLimit = creditLimit
                                            ))

  def bulkDeleteCustomers(): Boolean =
    extractFuture(actor ? cc.bulkDeleteCustomers())


}
