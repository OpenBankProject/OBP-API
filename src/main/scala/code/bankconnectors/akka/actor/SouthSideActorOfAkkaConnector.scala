package code.bankconnectors.akka.actor

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import code.api.util.APIUtil
import code.bankconnectors.LocalMappedConnector._
import code.bankconnectors.akka._
import code.customer.{CreditLimit, CreditRating, Customer, CustomerFaceImage}
import code.model.dataAccess.MappedBank
import code.model.{Bank => _, _}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box

import scala.collection.immutable.List


/**
  * This Actor acts in next way:
  */
class SouthSideActorOfAkkaConnector extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutboundGetAdapterInfo(_, cc) =>
      val result = 
        InboundAdapterInfo(
          "systemName",
          "version", 
          APIUtil.gitCommit, 
          (new Date()).toString,
          cc
        )
      sender ! result   
    
    case OutboundGetBanks(cc) =>
      val result: Box[List[MappedBank]] = getBanks(None).map(r => r._1)
      sender ! InboundGetBanks(result.map(l => l.map(Transformer.bank(_))).toOption, cc)
    
    case OutboundGetBank(bankId, cc) =>
      val result: Box[MappedBank] = getBank(BankId(bankId), None).map(r => r._1)
      sender ! InboundGetBank(result.map(Transformer.bank(_)).toOption, cc)  
      
    case OutboundCheckBankAccountExists(bankId, accountId, cc) =>
      val result: Box[BankAccount] = checkBankAccountExists(BankId(bankId), AccountId(accountId), None).map(r => r._1)
      sender ! InboundCheckBankAccountExists(result.map(Transformer.bankAccount(_)).toOption, cc)
      
    case OutboundGetAccount(bankId, accountId, cc) =>
      val result: Box[BankAccount] = getBankAccount(BankId(bankId), AccountId(accountId), None).map(r => r._1)
      sender ! InboundGetAccount(result.map(Transformer.bankAccount(_)).toOption, cc)
      
    case OutboundGetCoreBankAccounts(bankIdAccountIds, cc) =>
      val result: Box[List[CoreAccount]] = getCoreBankAccounts(bankIdAccountIds, None).map(r => r._1)
      sender ! InboundGetCoreBankAccounts(result.getOrElse(Nil).map(Transformer.coreAccount(_)), cc)
       
    case OutboundGetCustomersByUserId(userId, cc) =>
      val result: Box[List[Customer]] = getCustomersByUserId(userId, None).map(r => r._1)
      sender ! InboundGetCustomersByUserId(result.getOrElse(Nil).map(Transformer.toInternalCustomer(_)), cc)
      
      
  }

}



object Transformer {
  def bank(mb: MappedBank): Bank = 
    Bank(
      bankId=mb.bankId,
      shortName=mb.shortName,
      fullName=mb.fullName,
      logoUrl=mb.logoUrl,
      websiteUrl=mb.websiteUrl,
      bankRoutingScheme=mb.bankRoutingScheme,
      bankRoutingAddress=mb.bankRoutingAddress
    )
  
  def bankAccount(acc: BankAccount) =
    InboundAccountDec2018(
      bankId = acc.bankId.value,
      branchId = acc.branchId,
      accountId = acc.accountId.value,
      accountNumber = acc.number,
      accountType = acc.accountType,
      balanceAmount = acc.balance.toString(),
      balanceCurrency = acc.currency,
      owners = acc.customerOwners.map(_.customerId).toList,
      viewsToGenerate = Nil,
      bankRoutingScheme = acc.bankRoutingScheme,
      bankRoutingAddress = acc.bankRoutingAddress,
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = acc.accountRoutingScheme,
      accountRoutingAddress = acc.accountRoutingAddress,
      accountRouting = Nil,
      accountRules = Nil
    )
  
  def coreAccount(a: CoreAccount) =
    InternalInboundCoreAccount(
      id = a.id,
      label = a.label,
      bankId = a.bankId,
      accountType = a.accountType,
      accountRoutings = a.accountRoutings
    )

  def toInternalCustomer(customer: Customer): InternalCustomer = {
    InternalCustomer(
      customerId = customer.customerId,
      bankId = customer.bankId,
      number = customer.number,
      legalName = customer.legalName,
      mobileNumber = customer.mobileNumber,
      email = customer.email,
      faceImage = CustomerFaceImage(customer.faceImage.date,customer.faceImage.url),
      dateOfBirth = customer.dateOfBirth,
      relationshipStatus = customer.relationshipStatus,
      dependents = customer.dependents,
      dobOfDependents = customer.dobOfDependents,
      highestEducationAttained = customer.highestEducationAttained,
      employmentStatus = customer.employmentStatus,
      creditRating = CreditRating(customer.creditRating.rating, customer.creditRating.source),
      creditLimit = CreditLimit(customer.creditLimit.amount,customer.creditLimit.currency),
      kycStatus = customer.kycStatus,
      lastOkDate = customer.lastOkDate,
    )
  }
}

