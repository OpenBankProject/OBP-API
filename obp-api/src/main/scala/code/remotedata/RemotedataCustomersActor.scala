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
import com.openbankproject.commons.model.{AmountOfMoney, AmountOfMoneyTrait, BankId, CreditRatingTrait, CustomerFaceImageTrait}

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataCustomersActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerProvider
  val cc = RemotedataCustomerProviderCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getCustomersFuture(bankId: BankId, queryParams: List[OBPQueryParam]) =>
      logger.debug(s"getCustomersFuture($bankId, $queryParams)")
      (mapper.getCustomersFuture(bankId, queryParams)) pipeTo sender

    case cc.getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String) =>
      logger.debug(s"getCustomersByCustomerPhoneNumber($bankId, $phoneNumber)")
      mapper.getCustomersByCustomerPhoneNumber(bankId, phoneNumber) pipeTo sender

    case cc.getCustomerByUserId(bankId: BankId, userId: String) =>
      logger.debug(s"getCustomerByUserId($bankId, $userId)")
      sender ! (mapper.getCustomerByUserId(bankId, userId))

    case cc.getCustomersByUserId(userId: String) =>
      logger.debug(s"getCustomersByUserId($userId)")
      sender ! (mapper.getCustomersByUserId(userId))

    case cc.getCustomersByUserIdFuture(userId: String) =>
      logger.debug(s"getCustomersByUserIdFuture($userId)")
      sender ! (mapper.getCustomersByUserIdBoxed(userId))

    case cc.getCustomerByCustomerIdFuture(customerId: String) =>
      logger.debug(s"getCustomerByCustomerIdFuture($customerId)")
      sender ! (mapper.getCustomerByCustomerId(customerId))

    case cc.getCustomerByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerByCustomerId($customerId)")
      sender ! (mapper.getCustomerByCustomerId(customerId))

    case cc.getBankIdByCustomerId(customerId: String) =>
      logger.debug(s"getBankIdByCustomerId(customerId)")
      sender ! (mapper.getBankIdByCustomerId(customerId))

    case cc.getCustomerByCustomerNumber(customerNumber: String, bankId: BankId) =>
      logger.debug(s"getCustomerByCustomerNumber($customerNumber, $bankId)")
      sender ! (mapper.getCustomerByCustomerNumber(customerNumber, bankId))

    case cc.getCustomerByCustomerNumberFuture(customerNumber: String, bankId: BankId) =>
      logger.debug(s"getCustomerByCustomerNumberFuture($customerNumber, $bankId)")
      sender ! (mapper.getCustomerByCustomerNumber(customerNumber, bankId))

    case cc.getUser(bankId: BankId, customerNumber: String) =>
      logger.debug(s"getUser($bankId, $customerNumber)")
      sender ! (mapper.getUser(bankId, customerNumber))

    case cc.checkCustomerNumberAvailable(bankId: BankId, customerNumber: String) =>
      logger.debug(s"checkCustomerNumberAvailable($bankId, $customerNumber)")
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
      logger.debug(s"addCustomer($bankId, $number)")
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
      
    case cc.updateCustomerScaData(customerId: String, mobileNumber: Option[String], email: Option[String], customerNumber: Option[String]) =>
      logger.debug(s"updateCustomerScaData($customerId, $mobileNumber, $email)")
      (mapper.updateCustomerScaData(customerId, mobileNumber, email, customerNumber)) pipeTo sender
      
    case cc.updateCustomerCreditData(customerId: String,
                                    creditRating: Option[String],
                                    creditSource: Option[String],
                                    creditLimit: Option[AmountOfMoney]) =>
      logger.debug(s"updateCustomerCreditData($customerId, $creditRating, $creditSource, $creditLimit)")
      (mapper.updateCustomerCreditData(customerId, creditRating, creditSource, creditLimit)) pipeTo sender

    case cc.updateCustomerGeneralData(customerId: String,
                                      legalName: Option[String],
                                      faceImage: Option[CustomerFaceImageTrait],
                                      dateOfBirth: Option[Date],
                                      relationshipStatus: Option[String],
                                      dependents: Option[Int],
                                      highestEducationAttained: Option[String],
                                      employmentStatus: Option[String],
                                      title: Option[String],
                                      branchId: Option[String],
                                      nameSuffix: Option[String]) =>
      (mapper.updateCustomerGeneralData(
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
        nameSuffix)) pipeTo sender
      
    case cc.bulkDeleteCustomers() =>
      logger.debug("bulkDeleteCustomers()")
      sender ! (mapper.bulkDeleteCustomers())

    case cc.populateMissingUUIDs() =>
      logger.debug("populateMissingUUIDs()")
      sender ! (mapper.populateMissingUUIDs())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

