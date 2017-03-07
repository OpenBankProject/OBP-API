package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.customer.{AmountOfMoney, _}
import code.model._
import net.liftweb.util.ControlHelpers._


class RemotedataCustomersActor extends Actor {

  val logger = Logging(context.system, this)

  val mapper = MappedCustomerProvider
  val cc = RemotedataCustomerProviderCaseClasses

  def receive = {


    case cc.getCustomerByUser(bankId: BankId, user: User) =>

      logger.info("getCustomerByUser(" + bankId + ", " + user + ")")

      {
        for {
          res <- mapper.getCustomerByUser(bankId, user)
        } yield {
          sender ! res.asInstanceOf[Customer]
        }
      }.getOrElse(context.stop(sender))


    case cc.getCustomerByCustomerId(customerId: String) =>

      logger.info("getCustomerByCustomerId(" + customerId + ")")

      {
        for {
          res <- mapper.getCustomerByCustomerId(customerId)
        } yield {
          sender ! res.asInstanceOf[Customer]
        }
      }.getOrElse(context.stop(sender))


    case cc.getBankIdByCustomerId(customerId: String) =>

      logger.info("getBankIdByCustomerId(" + customerId + ")")

      {
        for {
          res <- mapper.getBankIdByCustomerId(customerId)
        } yield {
          sender ! res.asInstanceOf[String]
        }
      }.getOrElse(context.stop(sender))


    case cc.getCustomerByCustomerNumber(customerNumber: String, bankId: BankId) =>

      logger.info("getCustomerByCustomerNumber(" + customerNumber + ", " + bankId + ")")

      {
        for {
          res <- mapper.getCustomerByCustomerNumber(customerNumber, bankId)
        } yield {
          sender ! res.asInstanceOf[Customer]
        }
      }.getOrElse(context.stop(sender))


    case cc.getUser(bankId: BankId, customerNumber: String) =>

      logger.info("getUser(" + bankId + ", " + customerNumber + ")")

      {
        for {
          res <- mapper.getUser(bankId, customerNumber)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse(context.stop(sender))


    case cc.checkCustomerNumberAvailable(bankId: BankId, customerNumber: String) =>

      logger.info("checkCustomerNumberAvailable(" + bankId + ", " + customerNumber + ")")

      {
        for {
          res <- tryo {
            mapper.checkCustomerNumberAvailable(bankId, customerNumber)
          }
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse(context.stop(sender))


    case cc.addCustomer(bankId: BankId, user: User, number: String, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImage,
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
    ) =>

      logger.info("addCustomer(" + bankId + ", " + user + ")")

      {
        for {
          res <- mapper.addCustomer(bankId, user, number, legalName, mobileNumber, email, faceImage,
            dateOfBirth,
            relationshipStatus,
            dependents,
            dobOfDependents,
            highestEducationAttained,
            employmentStatus,
            kycStatus,
            lastOkDate,
            creditRating,
            creditLimit
          )
        } yield {
          sender ! res.asInstanceOf[Customer]
        }
      }.getOrElse(context.stop(sender))


    case cc.bulkDeleteCustomers() =>

      logger.info("bulkDeleteCustomers()")

      {
        for {
          res <- tryo {
            mapper.bulkDeleteCustomers()
          }
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse(context.stop(sender))


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

