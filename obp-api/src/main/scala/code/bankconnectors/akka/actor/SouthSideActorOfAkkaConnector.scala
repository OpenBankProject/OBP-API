package code.bankconnectors.akka.actor

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions._
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.api.util.{APIUtil, OBPFromDate, OBPLimit, OBPToDate}
import code.bankconnectors.LocalMappedConnector._
import code.model.dataAccess.MappedBank
import code.model._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model.{CounterpartyTrait, CreditLimit, _}
import net.liftweb.common.Box

import scala.collection.immutable.List


/**
  * This Actor acts in next way:
  */
class SouthSideActorOfAkkaConnector extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutBoundGetAdapterInfoFuture(cc) =>
      val result = 
        InBoundGetAdapterInfoFuture(
          cc,
          inboundAdapterInfoInternal
        )
      sender ! result   
    
    case OutBoundGetBanksFuture(cc) =>
      val result: Box[List[MappedBank]] = getBanks(None).map(r => r._1)
      sender ! InBoundGetBanksFuture(cc, result.map(l => l.map(Transformer.bank(_))).openOrThrowException(attemptedToOpenAnEmptyBox))
    
    case OutBoundGetBankFuture(cc, bankId) =>
      val result: Box[MappedBank] = getBank(bankId, None).map(r => r._1)
      sender ! InBoundGetBankFuture(cc, result.map(Transformer.bank(_)).openOrThrowException(attemptedToOpenAnEmptyBox) )  
      
    case OutBoundCheckBankAccountExistsFuture(cc, bankId, accountId) =>
      val result: Box[BankAccount] = checkBankAccountExists(bankId, accountId, None).map(r => r._1)
      sender ! InBoundCheckBankAccountExistsFuture(cc, result.map(Transformer.bankAccount(_)).openOrThrowException(attemptedToOpenAnEmptyBox))
      
    case OutBoundGetBankAccountFuture(cc, bankId, accountId) =>
      val result: Box[BankAccount] = getBankAccount(bankId, accountId, None).map(r => r._1)
      org.scalameta.logger.elem(result)
      sender ! InBoundGetBankAccountFuture(cc, result.map(Transformer.bankAccount(_)).openOrThrowException(attemptedToOpenAnEmptyBox))
      
    case OutBoundGetCoreBankAccountsFuture(cc, bankIdAccountIds) =>
      val result: Box[List[CoreAccount]] = getCoreBankAccounts(bankIdAccountIds, None).map(r => r._1)
      sender ! InBoundGetCoreBankAccountsFuture(cc, result.map(l => l.map(Transformer.coreAccount(_))).openOrThrowException(attemptedToOpenAnEmptyBox))
      
      
       
//    case OutBoundGetCustomersByUserIdFuture(cc, userId) =>
//      val result: Box[List[Customer]] = getCustomersByUserId(userId, None).map(r => r._1)
//      sender ! InBoundGetCustomersByUserIdFuture(cc, result.getOrElse(Nil).map(Transformer.toInternalCustomer(_)).openOrThrowException(attemptedToOpenAnEmptyBox))
//       
//    case OutboundGetCounterparties(thisBankId, thisAccountId, viewId, cc) =>
//      val result: Box[List[CounterpartyTrait]] = getCounterparties(BankId(thisBankId), AccountId(thisAccountId), ViewId(viewId), None).map(r => r._1)
//      sender ! InboundGetCounterparties(result.getOrElse(Nil).map(Transformer.toInternalCounterparty(_)), cc)
//        
//    case OutboundGetTransactions(bankId, accountId, limit, fromDate, toDate, cc) =>
//      val from = APIUtil.DateWithMsFormat.parse(fromDate)
//      val to = APIUtil.DateWithMsFormat.parse(toDate)
//      val result = getTransactions(BankId(bankId), AccountId(accountId), None, List(OBPLimit(limit), OBPFromDate(from), OBPToDate(to)): _*).map(r => r._1)
//      sender ! InboundGetTransactions(result.getOrElse(Nil).map(Transformer.toInternalTransaction(_)), cc)
//        
//    case OutboundGetTransaction(bankId, accountId, transactionId, cc) =>
//      val result = getTransaction(BankId(bankId), AccountId(accountId), TransactionId(transactionId),  None).map(r => r._1)
//      sender ! InboundGetTransaction(result.map(Transformer.toInternalTransaction(_)), cc)

    case message => 
      logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
      
  }

}



object Transformer {
  def bank(mb: MappedBank): BankCommons = 
    BankCommons(
      bankId=mb.bankId,
      shortName=mb.shortName,
      fullName=mb.fullName,
      logoUrl=mb.logoUrl,
      websiteUrl=mb.websiteUrl,
      bankRoutingScheme=mb.bankRoutingScheme,
      bankRoutingAddress=mb.bankRoutingAddress,
      swiftBic ="", //Not useful 
      nationalIdentifier ="" //Not useful 
    )
  
  
  def bankAccount(acc: BankAccount) =
    BankAccountCommons(
      accountId = acc.accountId,
      accountType = acc.accountType,
      balance = acc.balance,
      currency = acc.currency,
      name = acc.name,
      label = acc.label,
      swift_bic = None,
      iban = None,
      number = acc.number,
      bankId = acc.bankId,
      lastUpdate = acc.lastUpdate,
      branchId = acc.branchId,
      accountRoutingScheme = acc.accountRoutingScheme,
      accountRoutingAddress = acc.accountRoutingAddress,
      accountRoutings = Nil,
      accountRules = Nil,
      accountHolder = acc.accountHolder
    )
  
  
  def coreAccount(a: CoreAccount) =
    CoreAccount(
      id = a.id,
      label = a.label,
      bankId = a.bankId,
      accountType = a.accountType,
      accountRoutings = a.accountRoutings
    )

  def toInternalCustomer(customer: Customer): InboundCustomer = {
    InboundCustomer(
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
  
  def toInternalCounterparty(c: CounterpartyTrait) = {
    InboundCounterparty(
      createdByUserId=c.createdByUserId,
      name=c.name,
      thisBankId=c.thisBankId,
      thisAccountId=c.thisAccountId,
      thisViewId=c.thisViewId,
      counterpartyId=c.counterpartyId,
      otherAccountRoutingScheme=c.otherAccountRoutingScheme,
      otherAccountRoutingAddress=c.otherAccountRoutingAddress,
      otherBankRoutingScheme=c.otherBankRoutingScheme,
      otherBankRoutingAddress=c.otherBankRoutingAddress,
      otherBranchRoutingScheme=c.otherBankRoutingScheme,
      otherBranchRoutingAddress=c.otherBranchRoutingAddress,
      isBeneficiary=c.isBeneficiary,
      description=c.description,
      otherAccountSecondaryRoutingScheme=c.otherAccountSecondaryRoutingScheme,
      otherAccountSecondaryRoutingAddress=c.otherAccountSecondaryRoutingAddress,
      bespoke=c.bespoke
    )
  }

  def toInternalTransaction(t: Transaction): InboundTransaction = {
    InboundTransaction(
      uuid = t.uuid ,
      id  = t.id ,
      thisAccount = t.thisAccount ,
      otherAccount = t.otherAccount ,
      transactionType = t.transactionType ,
      amount = t.amount ,
      currency = t.currency ,
      description = t.description ,
      startDate = t.startDate ,
      finishDate = t.finishDate ,
      balance = t.balance
    )
  }
}

