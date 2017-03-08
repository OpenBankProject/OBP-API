package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import code.api.APIFailure
import code.customer.{AmountOfMoney, CreditRating, Customer, CustomerFaceImage, CustomerProvider, RemotedataCustomerProviderCaseClasses}
import code.model._
import net.liftweb.common.{Full, _}

import scala.concurrent.Await


object RemotedataCustomers extends ActorInit with CustomerProvider {

  val cc = RemotedataCustomerProviderCaseClasses

  def getCustomerByResourceUserId(bankId: BankId, resourceUserId: Long): Box[Customer] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.getCustomerByResourceUserId(bankId, resourceUserId)).mapTo[Customer],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Customer not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getCustomerByCustomerId(customerId: String) : Box[Customer] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.getCustomerByCustomerId(customerId)).mapTo[Customer],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Customer not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getBankIdByCustomerId(customerId: String) : Box[String] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.getBankIdByCustomerId(customerId)).mapTo[String],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Bank not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId) : Box[Customer] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.getCustomerByCustomerNumber(customerNumber, bankId)).mapTo[Customer],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Customer not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getUser(bankId : BankId, customerNumber : String) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.getUser(bankId, customerNumber)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String): Boolean = {
    Await.result(
      (actor ? cc.checkCustomerNumberAvailable(bankId, customerNumber)).mapTo[Boolean],
      TIMEOUT
    )
  }

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
                 ) : Box[Customer] = {
      Full(
        Await.result(
        (actor ? cc.addCustomer(bankId = bankId,
                                user = user,
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
        )).mapTo[Customer],
        TIMEOUT
      )
    )
  }

  def bulkDeleteCustomers(): Boolean = {
    Await.result(
      (actor ? cc.bulkDeleteCustomers()).mapTo[Boolean],
      TIMEOUT
    )
  }


}
