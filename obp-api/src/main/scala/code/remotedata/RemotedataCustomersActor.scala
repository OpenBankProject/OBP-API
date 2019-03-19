package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.api.util.OBPQueryParam
import code.customer._
import code.model._
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import akka.pattern.pipe
import com.openbankproject.commons.model.{AmountOfMoneyTrait, BankId, CreditRatingTrait, CustomerFaceImageTrait}

import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataCustomersActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerProvider
  val cc = RemotedataCustomerProviderCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getCustomersFuture(bankId: BankId, queryParams: List[OBPQueryParam]) =>
      logger.debug("getCustomersFuture(" + bankId + ", " + queryParams + ")")
      (mapper.getCustomersFuture(bankId, queryParams)) pipeTo sender

    case cc.getCustomerByUserId(bankId: BankId, userId: String) =>
      logger.debug("getCustomerByUserId(" + bankId + ", " + userId + ")")
      sender ! (mapper.getCustomerByUserId(bankId, userId))

    case cc.getCustomersByUserId(userId: String) =>
      logger.debug("getCustomersByUserId(" + userId + ")")
      sender ! (mapper.getCustomersByUserId(userId))

    case cc.getCustomersByUserIdFuture(userId: String) =>
      logger.debug("getCustomersByUserIdFuture(" + userId + ")")
      sender ! (mapper.getCustomersByUserIdBoxed(userId))

    case cc.getCustomerByCustomerIdFuture(customerId: String) =>
      logger.debug("getCustomerByCustomerIdFuture(" + customerId + ")")
      sender ! (mapper.getCustomerByCustomerId(customerId))

    case cc.getCustomerByCustomerId(customerId: String) =>
      logger.debug("getCustomerByCustomerId(" + customerId + ")")
      sender ! (mapper.getCustomerByCustomerId(customerId))

    case cc.getBankIdByCustomerId(customerId: String) =>
      logger.debug("getBankIdByCustomerId(" + customerId + ")")
      sender ! (mapper.getBankIdByCustomerId(customerId))

    case cc.getCustomerByCustomerNumber(customerNumber: String, bankId: BankId) =>
      logger.debug("getCustomerByCustomerNumber(" + customerNumber + ", " + bankId + ")")
      sender ! (mapper.getCustomerByCustomerNumber(customerNumber, bankId))

    case cc.getCustomerByCustomerNumberFuture(customerNumber: String, bankId: BankId) =>
      logger.debug("getCustomerByCustomerNumberFuture(" + customerNumber + ", " + bankId + ")")
      sender ! (mapper.getCustomerByCustomerNumber(customerNumber, bankId))

    case cc.getUser(bankId: BankId, customerNumber: String) =>
      logger.debug("getUser(" + bankId + ", " + customerNumber + ")")
      sender ! (mapper.getUser(bankId, customerNumber))

    case cc.checkCustomerNumberAvailable(bankId: BankId, customerNumber: String) =>
      logger.debug("checkCustomerNumberAvailable(" + bankId + ", " + customerNumber + ")")
      sender ! (mapper.checkCustomerNumberAvailable(bankId, customerNumber))

    case cc.addCustomer(bankId: BankId,
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
                        ) =>
      logger.debug("addCustomer(" + bankId + ", " + number + ")")
      sender ! (mapper.addCustomer(bankId,
                                    number,
                                    legalName,
                                    mobileNumber,
                                    email,
                                    faceImage,
                                    dateOfBirth,
                                    relationshipStatus,
                                    dependents,
                                    dobOfDependents,
                                    highestEducationAttained,
                                    employmentStatus,
                                    kycStatus,
                                    lastOkDate,
                                    creditRating,
                                    creditLimit,
                                    title: String,     
                                    branchId: String,  
                                    nameSuffix: String
                                  ))

    case cc.bulkDeleteCustomers() =>
      logger.debug("bulkDeleteCustomers()")
      sender ! (mapper.bulkDeleteCustomers())

    case cc.populateMissingUUIDs() =>
      logger.debug("populateMissingUUIDs()")
      sender ! (mapper.populateMissingUUIDs())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

