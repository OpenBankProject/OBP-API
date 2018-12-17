package code.bankconnectors.akka

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpLookupSystem
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType}
import code.api.util.ExampleValue._
import code.api.util.{APIUtil, CallContext, CallContextAkka}
import code.bankconnectors._
import code.bankconnectors.akka.actor.{AkkaConnectorActorInit, AkkaConnectorHelperActor}
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.customer.{CreditLimit, CreditRating, Customer, CustomerFaceImage}
import code.metadata.counterparties.CounterpartyTrait
import code.model.{AccountId, AccountRouting, BankAccount, BankId, BankIdAccountId, CoreAccount, CounterpartyBespoke, Transaction, TransactionId, ViewId, Bank => BankTrait}
import com.sksamuel.avro4s.SchemaFor
import net.liftweb.common.{Box, Full}
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.parse

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AkkaConnector_vDec2018 extends Connector with AkkaConnectorActorInit {

  implicit override val nameOfConnector = AkkaConnector_vDec2018.toString

  val messageFormat: String = "Dec2018"
  implicit val formats = net.liftweb.json.DefaultFormats
  override val messageDocs = ArrayBuffer[MessageDoc]()
  val emptyObjectJson: JValue = decompose(Nil)
  
  lazy val southSideActor = ObpLookupSystem.getAkkaConnectorActor(AkkaConnectorHelperActor.actorName)

  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "Gets information about the active general (non bank specific) Adapter that is responding to messages sent by OBP.",
    outboundTopic = None,
    inboundTopic = None,
    emptyObjectJson,
    emptyObjectJson,
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetAdapterInfo]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundAdapterInfo]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  override def getAdapterInfoFuture(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
    val req = OutboundGetAdapterInfo((new Date()).toString, callContext.map(_.toCallContextAkka))
    val response = (southSideActor ? req).mapTo[InboundAdapterInfo]
    response.map(r =>
      Full(
        (
          InboundAdapterInfoInternal(
            errorCode = "",
            backendMessages = Nil,
            name = r.name,
            version = r.version,
            git_commit = r.git_commit,
            date = r.date
          )
          ,
          callContext
        )
      )
    )
  }

  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "Gets the banks list on this OBP installation.",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetBanks(Examples.callContextAkka)
    ),
    exampleInboundMessage = decompose(
      InboundGetBanks(
        Some(List(Examples.bank)),
        Examples.callContextAkka)
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBanks]().toString(true))),
    inboundAvroSchema =  Some(parse(SchemaFor[InboundGetBanks]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 2))
  )
  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[BankTrait], Option[CallContext])]] = {
    val req = OutboundGetBanks(callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetBanks] = (southSideActor ? req).mapTo[InboundGetBanks]
    response.map(_.payload.map(r => (r.map(BankAkka(_)), callContext)))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "Get a specific Bank as specified by bankId",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetBank(
        bankIdExample.value,
        Examples.callContextAkka)
    ),
    exampleInboundMessage = decompose(
      InboundGetBank(
        Some(Examples.bank),
        Examples.callContextAkka)
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBank]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBank]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 5))
  )
  override def getBankFuture(bankId : BankId, callContext: Option[CallContext]): Future[Box[(BankTrait, Option[CallContext])]] = {
    val req = OutboundGetBank(bankId.value, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetBank] = (southSideActor ? req).mapTo[InboundGetBank]
    response.map(_.payload.map(r => (BankAkka(r), callContext)))
  }

  messageDocs += MessageDoc(
    process = "obp.check.BankAccountExists",
    messageFormat = messageFormat,
    description = "Check a bank Account exists - as specified by bankId and accountId.",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundCheckBankAccountExists(
        bankIdExample.value,
        accountIdExample.value,
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundCheckBankAccountExists(
        Some(Examples.inboundAccountDec2018Example),
        Examples.callContextAkka
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Accounts", 4))
  )
  override def checkBankAccountExistsFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None): Future[Box[(BankAccount, Option[CallContext])]] = {
    val req = OutboundCheckBankAccountExists(bankId.value, accountId.value, callContext.map(_.toCallContextAkka))
    val response: Future[InboundCheckBankAccountExists] = (southSideActor ? req).mapTo[InboundCheckBankAccountExists]
    response.map(_.payload.map(r => (BankAccountDec2018(r), callContext)))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "Get a single Account as specified by the bankId and accountId.",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetAccount(
        bankIdExample.value,
        accountIdExample.value,
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetAccount(
        Some(Examples.inboundAccountDec2018Example),
        Examples.callContextAkka
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Accounts", 7))
  )
  override def getBankAccountFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
    val req = OutboundGetAccount(bankId.value, accountId.value, callContext.map(_.toCallContextAkka))
    val response = (southSideActor ? req).mapTo[InboundGetAccount]
    response.map(a => (a.payload.map(BankAccountDec2018(_)), callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.coreBankAccounts",
    messageFormat = messageFormat,
    description = "Get bank Accounts available to the User (without Metadata)",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetCoreBankAccounts(
        List(BankIdAccountId(BankId(bankIdExample.value), AccountId(accountIdExample.value))),
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetCoreBankAccounts(
        List(
          InternalInboundCoreAccount(
            accountIdExample.value, 
            "My private account for Uber", 
            bankIdExample.value, 
            accountTypeExample.value, 
            List(AccountRouting(accountRoutingSchemeExample.value, accountRoutingAddressExample.value)
            )
          )
        ),
        Examples.callContextAkka
      )),
    adapterImplementation = Some(AdapterImplementation("Accounts", 1))
  )
  override def getCoreBankAccountsFuture(BankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = {
    val req = OutboundGetCoreBankAccounts(BankIdAccountIds, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetCoreBankAccounts] = (southSideActor ? req).mapTo[InboundGetCoreBankAccounts]
    response.map(a => Full(a.payload.map( x => CoreAccount(x.id,x.label,x.bankId,x.accountType, x.accountRoutings)), callContext))
  }



  messageDocs += MessageDoc(
    process = "obp.get.CustomersByUserId",
    messageFormat = messageFormat,
    description = "Get Customers represented by the User.",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetCustomersByUserId(
        userIdExample.value,
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetCustomersByUserId(
        InternalCustomer(
          customerId = customerIdExample.value, 
          bankId = bankIdExample.value, 
          number = customerNumberExample.value,
          legalName = legalNameExample.value, 
          mobileNumber = "String", 
          email = "String",
          faceImage = CustomerFaceImage(date = APIUtil.DateWithSecondsExampleObject, url = "String"),
          dateOfBirth = APIUtil.DateWithSecondsExampleObject, relationshipStatus = "String",
          dependents = 1, 
          dobOfDependents = List(APIUtil.DateWithSecondsExampleObject),
          highestEducationAttained = "String", 
          employmentStatus = "String",
          creditRating = CreditRating(rating = "String", source = "String"),
          creditLimit = CreditLimit(currency = currencyExample.value, amount = creditLimitAmountExample.value),
          kycStatus = false, 
          lastOkDate = APIUtil.DateWithSecondsExampleObject
        ) :: Nil,
        Examples.callContextAkka
      )
    ),
    outboundAvroSchema = None,
    inboundAvroSchema = None,
    adapterImplementation = Some(AdapterImplementation("Accounts", 0))
  )
  override def getCustomersByUserIdFuture(userId: String , callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = {
    val req = OutboundGetCustomersByUserId(userId, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetCustomersByUserId] = (southSideActor ? req).mapTo[InboundGetCustomersByUserId]
    response.map(a => Full(InboundTransformerDec2018.toCustomers(a.payload), callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.counterparties",
    messageFormat = messageFormat,
    description = "Get Counterparties available to the View on the Account specified by thisBankId, thisAccountId and viewId.",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetCounterparties(
        thisBankId = bankIdExample.value,
        accountIdExample.value,
        viewId = "Auditor",
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetCounterparties(
        InternalCounterparty(
          createdByUserId = userIdExample.value,
          name = "",
          thisBankId = bankIdExample.value,
          thisAccountId = accountIdExample.value,
          thisViewId = "Auditor",
          counterpartyId = counterpartyIdExample.value,
          otherAccountRoutingScheme = accountRoutingSchemeExample.value,
          otherAccountRoutingAddress = accountRoutingAddressExample.value,
          otherBankRoutingScheme = bankRoutingSchemeExample.value,
          otherBankRoutingAddress = bankRoutingAddressExample.value,
          otherBranchRoutingScheme = accountRoutingSchemeExample.value,
          otherBranchRoutingAddress = accountRoutingAddressExample.value,
          isBeneficiary = true,
          description = "",
          otherAccountSecondaryRoutingScheme = accountRoutingSchemeExample.value,
          otherAccountSecondaryRoutingAddress = accountRoutingAddressExample.value,
          bespoke =  List(
            CounterpartyBespoke(key = "key", value = "value"))
        ) :: Nil,
        Examples.callContextAkka
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Payments", 0))
  )
  override def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = {
    val req = OutboundGetCounterparties(thisBankId.value, thisAccountId.value, viewId.value, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetCounterparties] = (southSideActor ? req).mapTo[InboundGetCounterparties]
    response.map(a => (Full(a.payload), callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "Get Transactions for an Account specified by bankId and accountId. Pagination is achieved with limit, fromDate and toDate.",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetTransactions(
        bankId = bankIdExample.value,
        accountId = accountIdExample.value,
        limit = 100,
        fromDate=APIUtil.DateWithSecondsExampleString,
        toDate=APIUtil.DateWithSecondsExampleString,
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetTransactions(
        Nil,
        Examples.callContextAkka
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Transactions", 10))
  )
  override def getTransactionsFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): OBPReturnType[Box[List[Transaction]]] = {
    val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => APIUtil.DateWithMsFormat.format(date) }.headOption.getOrElse(APIUtil.DefaultFromDate.toString)
    val toDate = queryParams.collect { case OBPToDate(date) => APIUtil.DateWithMsFormat.format(date) }.headOption.getOrElse(APIUtil.DefaultToDate.toString)

    val req = OutboundGetTransactions(bankId.value, accountId.value, limit, fromDate, toDate, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetTransactions] = (southSideActor ? req).mapTo[InboundGetTransactions]
    response.map(a => (Full(InboundTransformerDec2018.toTransactions(a.payload)), callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "Get a single Transaction specified by bankId, accountId and transactionId",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = decompose(
      OutboundGetTransaction(
        bankId = bankIdExample.value,
        accountId = accountIdExample.value,
        transactionId = transactionIdExample.value,
        Examples.callContextAkka
      )
    ),
    exampleInboundMessage = decompose(
      InboundGetTransaction(
        None,
        Examples.callContextAkka
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Transactions", 11))
  )
  override def getTransactionFuture(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = {
    val req = OutboundGetTransaction(bankId.value, accountId.value, transactionId.value, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetTransaction] = (southSideActor ? req).mapTo[InboundGetTransaction]
    response.map(a => (a.payload.map(InboundTransformerDec2018.toTransaction(_)), callContext))
  }



  }

object Examples {
  val inboundAccountDec2018Example = 
    InboundAccountDec2018(
      bankId = bankIdExample.value,
      branchId = branchIdExample.value,
      accountId = accountIdExample.value,
      accountNumber = accountNumberExample.value,
      accountType = accountTypeExample.value,
      balanceAmount = balanceAmountExample.value,
      balanceCurrency = currencyExample.value,
      owners = owner1Example.value :: owner1Example.value :: Nil,
      viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
      bankRoutingScheme = bankRoutingSchemeExample.value,
      bankRoutingAddress = bankRoutingAddressExample.value,
      branchRoutingScheme = branchRoutingSchemeExample.value,
      branchRoutingAddress = branchRoutingAddressExample.value,
      accountRoutingScheme = accountRoutingSchemeExample.value,
      accountRoutingAddress = accountRoutingAddressExample.value,
      accountRouting = Nil,
      accountRules = Nil
    )
  
  val callContextAkka = Some(
    CallContextAkka(
      Some(userIdExample.value),
      Some("9ddb6507-9cec-4e5e-b09a-ef1cb203825a"),
      correlationIdExample.value,
      Some(sessionIdExample.value)
    )
  )
  
  val bank = 
    Bank(
      bankId = bankIdExample.value,
      shortName = "The Royal Bank of Scotland",
      fullName = "The Royal Bank of Scotland",
      logoUrl = "http://www.red-bank-shoreditch.com/logo.gif",
      websiteUrl = "http://www.red-bank-shoreditch.com",
      bankRoutingScheme = "OBP",
      bankRoutingAddress = "rbs"
    )
    
  
}


