package code.bankconnectors.akka

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpLookupSystem
import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.{bankAccountCommons, bankCommons, transactionCommons, _}
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType}
import code.api.util.ExampleValue._
import code.api.util._
import code.bankconnectors._
import code.bankconnectors.akka.actor.{AkkaConnectorActorInit, AkkaConnectorHelperActor}
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.sksamuel.avro4s.SchemaFor
import net.liftweb.common.{Box, Full}
import net.liftweb.json.parse

import scala.collection.immutable.{List, Nil}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AkkaConnector_vDec2018 extends Connector with AkkaConnectorActorInit {

  implicit override val nameOfConnector = AkkaConnector_vDec2018.toString
  val messageFormat: String = "Dec2018"
  
  lazy val southSideActor = ObpLookupSystem.getAkkaConnectorActor(AkkaConnectorHelperActor.actorName)

  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "Gets information about the active general (non bank specific) Adapter that is responding to messages sent by OBP.",
    outboundTopic = Some(OutBoundGetAdapterInfoFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetAdapterInfoFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetAdapterInfoFuture(
        outboundAdapterCallContext
    )),
    exampleInboundMessage = (
      InBoundGetAdapterInfoFuture(
        inboundAdapterCallContext,
        inboundAdapterInfoInternal)
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutBoundGetAdapterInfoFuture]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InBoundGetAdapterInfoFuture]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  override def getAdapterInfoFuture(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
    val req = OutBoundGetAdapterInfoFuture(callContext.map(_.toOutboundAdapterCallContext).get)
    val response = (southSideActor ? req).mapTo[InBoundGetAdapterInfoFuture]
    response.map(r => Full(r.data, callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "Gets the banks list on this OBP installation.",
    outboundTopic = Some(OutBoundGetBanksFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetBanksFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetBanksFuture(outboundAdapterCallContext)
    ),
    exampleInboundMessage = (
      InBoundGetBanksFuture(
        inboundAdapterCallContext,
        List(bankCommons)
        )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutBoundGetBanksFuture]().toString(true))),
    inboundAvroSchema =  Some(parse(SchemaFor[InBoundGetBanksFuture]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 2))
  )
  
  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = {
    val req = OutBoundGetBanksFuture(callContext.map(_.toOutboundAdapterCallContext).get)
    val response: Future[InBoundGetBanksFuture] = (southSideActor ? req).mapTo[InBoundGetBanksFuture]
    response.map(r => Full(r.data, callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "Get a specific Bank as specified by bankId",
    outboundTopic = Some(OutBoundGetBankFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetBankFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetBankFuture(
        outboundAdapterCallContext,
        BankId(bankIdExample.value))
    ),
    exampleInboundMessage = (
      InBoundGetBankFuture(
        inboundAdapterCallContext,
        bankCommons
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutBoundGetBankFuture]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InBoundGetBankFuture]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 5))
  )
  override def getBankFuture(bankId : BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = {
    val req = OutBoundGetBankFuture(callContext.map(_.toOutboundAdapterCallContext).get, bankId)
    val response: Future[InBoundGetBankFuture] = (southSideActor ? req).mapTo[InBoundGetBankFuture]
    response.map(r => Full(r.data, callContext))
  }

   messageDocs += MessageDoc(
    process = "obp.get.BankAccountsForUser",
    messageFormat = messageFormat,
    description = "Gets the list of accounts available to the User. This call sends authInfo including username.",
    outboundTopic = Some(OutBoundGetBankAccountsForUserFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetBankAccountsForUserFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetBankAccountsForUserFuture(
        outboundAdapterCallContext,
        usernameExample.value)
    ),
    exampleInboundMessage = (
      InBoundGetBankAccountsForUserFuture(
        inboundAdapterCallContext,
        List(inboundAccountCommons)
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Accounts", 5))
  )
  override def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = {
    val req = OutBoundGetBankAccountsForUserFuture(callContext.map(_.toOutboundAdapterCallContext).get, username)
    val response = (southSideActor ? req).mapTo[InBoundGetBankAccountsForUserFuture]
    response.map(a =>(Full(a.data, callContext)))
  }
  
  messageDocs += MessageDoc(
    process = "obp.check.BankAccountExists",
    messageFormat = messageFormat,
    description = "Check a bank Account exists - as specified by bankId and accountId.",
    outboundTopic = Some(OutBoundCheckBankAccountExistsFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundCheckBankAccountExistsFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundCheckBankAccountExistsFuture(
        outboundAdapterCallContext,
        BankId(bankIdExample.value),
        AccountId(accountIdExample.value)
      )
    ),
    exampleInboundMessage = (
      InBoundCheckBankAccountExistsFuture(
        inboundAdapterCallContext,
        bankAccountCommons
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Accounts", 4))
  )
  override def checkBankAccountExistsFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None): Future[Box[(BankAccount, Option[CallContext])]] = {
    val req = OutBoundCheckBankAccountExistsFuture(callContext.map(_.toOutboundAdapterCallContext).get, bankId, accountId)
    val response: Future[InBoundCheckBankAccountExistsFuture] = (southSideActor ? req).mapTo[InBoundCheckBankAccountExistsFuture]
    response.map(a =>(Full(a.data, callContext)))
    
  }

  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "Get a single Account as specified by the bankId and accountId.",
    outboundTopic = Some(OutBoundGetBankAccountFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetBankAccountFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetBankAccountFuture(
        outboundAdapterCallContext,
        BankId(bankIdExample.value),
        AccountId(accountIdExample.value),
      )
    ),
    exampleInboundMessage = (
      InBoundGetBankAccountFuture(
        inboundAdapterCallContext,
        bankAccountCommons
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Accounts", 7))
  )
  override def getBankAccountFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
    val req = OutBoundGetBankAccountFuture(callContext.map(_.toOutboundAdapterCallContext).get, bankId, accountId)
    val response = (southSideActor ? req).mapTo[InBoundGetBankAccountFuture]
    response.map(a =>(Full(a.data), callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.coreBankAccounts",
    messageFormat = messageFormat,
    description = "Get bank Accounts available to the User (without Metadata)",
    outboundTopic = Some(OutBoundGetCoreBankAccountsFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetCoreBankAccountsFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetCoreBankAccountsFuture(
        outboundAdapterCallContext,
        List(BankIdAccountId(BankId(bankIdExample.value), AccountId(accountIdExample.value)))
      )
    ),
    exampleInboundMessage = (
      InBoundGetCoreBankAccountsFuture(
        inboundAdapterCallContext,
        List(
          CoreAccount(
            accountIdExample.value, 
            "My private account for Uber", 
            bankIdExample.value, 
            accountTypeExample.value, 
            List(AccountRouting(accountRoutingSchemeExample.value, accountRoutingAddressExample.value)
            )
          )
        )
      )),
    adapterImplementation = Some(AdapterImplementation("Accounts", 1))
  )
  override def getCoreBankAccountsFuture(BankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = {
    val req = OutBoundGetCoreBankAccountsFuture(callContext.map(_.toOutboundAdapterCallContext).get, BankIdAccountIds) 
    val response = (southSideActor ? req).mapTo[InBoundGetCoreBankAccountsFuture]
    response.map(a =>(Full(a.data, callContext)))
  }



  messageDocs += MessageDoc(
    process = "obp.get.CustomersByUserId",
    messageFormat = messageFormat,
    description = "Get Customers represented by the User.",
    outboundTopic = Some(OutBoundGetCustomersByUserIdFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetCustomersByUserIdFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetCustomersByUserIdFuture(
        outboundAdapterCallContext,
        userIdExample.value
      )
    ),
    exampleInboundMessage = (
      InBoundGetCustomersByUserIdFuture(
        inboundAdapterCallContext,
        customerCommons:: Nil,
      )
    ),
    outboundAvroSchema = None,
    inboundAvroSchema = None,
    adapterImplementation = Some(AdapterImplementation("Accounts", 0))
  )
  override def getCustomersByUserIdFuture(userId: String , callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = {
    val req = OutBoundGetCustomersByUserIdFuture(callContext.map(_.toOutboundAdapterCallContext).get, userId)
    val response= (southSideActor ? req).mapTo[InBoundGetCustomersByUserIdFuture]
    response.map(a =>(Full(a.data, callContext)))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "Get Transactions for an Account specified by bankId and accountId. Pagination is achieved with limit, fromDate and toDate.",
    outboundTopic = Some(OutBoundGetTransactionsFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetTransactionsFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetTransactionsFuture(
        outboundAdapterCallContext,
        bankId = BankId(bankIdExample.value),
        accountId = AccountId(accountIdExample.value),
        limit = limitExample.value.toInt,     
        fromDate = APIUtil.DateWithDayExampleString, 
        toDate = APIUtil.DateWithDayExampleString) 
    ),
    exampleInboundMessage = (
      InBoundGetTransactionsFuture(
        inboundAdapterCallContext,
        List(transactionCommons)
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Transactions", 10))
  )
  override def getTransactionsFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): OBPReturnType[Box[List[Transaction]]] = {
    val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => APIUtil.DateWithMsFormat.format(date) }.headOption.getOrElse(APIUtil.DefaultFromDate.toString)
    val toDate = queryParams.collect { case OBPToDate(date) => APIUtil.DateWithMsFormat.format(date) }.headOption.getOrElse(APIUtil.DefaultToDate.toString)

    val req = OutBoundGetTransactionsFuture(callContext.map(_.toOutboundAdapterCallContext).get, bankId, accountId, limit, fromDate, toDate)
    val response: Future[InBoundGetTransactionsFuture] = (southSideActor ? req).mapTo[InBoundGetTransactionsFuture]
    response.map(a =>(Full(a.data), callContext))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "Get a single Transaction specified by bankId, accountId and transactionId",
    outboundTopic = Some(OutBoundGetTransactionFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetTransactionFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetTransactionFuture(
        outboundAdapterCallContext,
        bankId = BankId(bankIdExample.value),
        accountId = AccountId(accountIdExample.value),
        transactionId = TransactionId(transactionIdExample.value)
      )
    ),
    exampleInboundMessage = (
      InBoundGetTransactionFuture(
        inboundAdapterCallContext,
        transactionCommons
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Transactions", 11))
  )
  override def getTransactionFuture(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = {
    val req = OutBoundGetTransactionFuture(callContext.map(_.toOutboundAdapterCallContext).get, bankId, accountId, transactionId)
    val response= (southSideActor ? req).mapTo[InBoundGetTransactionFuture]
    response.map(a =>(Full(a.data), callContext))
  }
 
}
