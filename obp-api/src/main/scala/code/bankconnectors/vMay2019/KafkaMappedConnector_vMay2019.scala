package code.bankconnectors.vMay2019

/*
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID.randomUUID

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util._
import code.api.v2_1_0.TransactionRequestBodyCommonJSON
import code.bankconnectors._
import code.bankconnectors.vJune2017.{InternalCustomer, JsonFactory_vJune2017}
import code.bankconnectors.vMar2017._
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import code.context.UserAuthContextProvider
import code.customer._
import code.kafka.{KafkaHelper, Topics}
import code.model._
import code.model.dataAccess._
import code.users.Users
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model.{AmountOfMoneyTrait, CounterpartyTrait, CreditRatingTrait, _}
import com.sksamuel.avro4s.SchemaFor
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb
import net.liftweb.common.{Box, _}
import net.liftweb.json.{MappingException, parse}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import com.github.dwickern.macros.NameOf.nameOf

trait KafkaMappedConnector_vMay2019 extends Connector with KafkaHelper with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{CustomerFaceImage, CreditLimit, CreditRating, AmountOfMoney}

  implicit override val nameOfConnector = KafkaMappedConnector_vMay2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound...) are defined below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "May2019"











//---------------- dynamic start -------------------please don't modify this line
// ---------- create on Mon Jun 03 19:58:31 CST 2019

  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getAdapterInfo _)}",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetAdapterInfo.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetAdapterInfo.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetAdapterInfo(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))))
    ),
    exampleInboundMessage = (
     InBoundGetAdapterInfo(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data= InboundAdapterInfoInternal(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string")),
      name="string",
      version="string",
      git_commit="string",
      date="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  override def getAdapterInfo(@CacheKeyOmit callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetAdapterInfo => OutBound, InBoundGetAdapterInfo => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get )
        logger.debug(s"Kafka getAdapterInfo Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getAdapterInfo")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getBank _)}",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBank.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBank.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetBank(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"))
    ),
    exampleInboundMessage = (
     InBoundGetBank(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data= BankCommons(bankId= BankId(value="string"),
      shortName="string",
      fullName="string",
      logoUrl="string",
      websiteUrl="string",
      bankRoutingScheme="string",
      bankRoutingAddress="string",
      swiftBic="string",
      nationalIdentifier="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("Bank", 1))
  )
  override def getBank(bankId: BankId, @CacheKeyOmit callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(bankTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetBank => OutBound, InBoundGetBank => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId)
        logger.debug(s"Kafka getBank Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getBank")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getBanks _)}",
    messageFormat = messageFormat,
    description = "Get Banks",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBanks.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBanks.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetBanks(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))))
    ),
    exampleInboundMessage = (
     InBoundGetBanks(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data=List( BankCommons(bankId= BankId(value="string"),
      shortName="string",
      fullName="string",
      logoUrl="string",
      websiteUrl="string",
      bankRoutingScheme="string",
      bankRoutingAddress="string",
      swiftBic="string",
      nationalIdentifier="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("Bank", 1))
  )
  override def getBanks(@CacheKeyOmit callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetBanks => OutBound, InBoundGetBanks => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get )
        logger.debug(s"Kafka getBanks Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getBanks")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getBankAccountsForUser _)}",
    messageFormat = messageFormat,
    description = "Get Bank Accounts For User",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsForUser.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccountsForUser.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetBankAccountsForUser(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      username="string")
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsForUser(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data=List( InboundAccountCommons(bankId="string",
      branchId="string",
      accountId="string",
      accountNumber="string",
      accountType="string",
      balanceAmount="string",
      balanceCurrency="string",
      owners=List("string"),
      viewsToGenerate=List("string"),
      bankRoutingScheme="string",
      bankRoutingAddress="string",
      branchRoutingScheme="string",
      branchRoutingAddress="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("Account", 1))
  )
  override def getBankAccountsForUser(username: String, @CacheKeyOmit callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccountsForUser => OutBound, InBoundGetBankAccountsForUser => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , username)
        logger.debug(s"Kafka getBankAccountsForUser Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getBankAccountsForUser")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getBankAccount _)}",
    messageFormat = messageFormat,
    description = "Get Bank Account",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccount.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetBankAccount.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetBankAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"),
      accountId= AccountId(value="string"))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data= BankAccountCommons(accountId= AccountId(value="string"),
      accountType="string",
      balance=BigDecimal("123.321"),
      currency="string",
      name="string",
      label="string",
      iban=Option("string"),
      number="string",
      bankId= BankId(value="string"),
      lastUpdate=new Date(),
      branchId="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      accountRules=List( AccountRule(scheme="string",
      value="string")),
      accountHolder="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("Account", 1))
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetBankAccount => OutBound, InBoundGetBankAccount => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, accountId)
        logger.debug(s"Kafka getBankAccount Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getBankAccount")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getCoreBankAccounts _)}",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCoreBankAccounts.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCoreBankAccounts.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetCoreBankAccounts(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankIdAccountIds=List( BankIdAccountId(bankId= BankId(value="string"),
      accountId= AccountId(value="string"))))
    ),
    exampleInboundMessage = (
     InBoundGetCoreBankAccounts(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data=List( CoreAccount(id="string",
      label="string",
      bankId="string",
      accountType="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")))))
    ),
    adapterImplementation = Some(AdapterImplementation("Account", 1))
  )
  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]): Future[Box[(List[CoreAccount], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetCoreBankAccounts => OutBound, InBoundGetCoreBankAccounts => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankIdAccountIds)
        logger.debug(s"Kafka getCoreBankAccounts Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getCoreBankAccounts")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(checkBankAccountExists _)}",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCheckBankAccountExists.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCheckBankAccountExists.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCheckBankAccountExists(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"),
      accountId= AccountId(value="string"))
    ),
    exampleInboundMessage = (
     InBoundCheckBankAccountExists(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data= BankAccountCommons(accountId= AccountId(value="string"),
      accountType="string",
      balance=BigDecimal("123.321"),
      currency="string",
      name="string",
      label="string",
      iban=Option("string"),
      number="string",
      bankId= BankId(value="string"),
      lastUpdate=new Date(),
      branchId="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      accountRules=List( AccountRule(scheme="string",
      value="string")),
      accountHolder="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("Account", 1))
  )
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundCheckBankAccountExists => OutBound, InBoundCheckBankAccountExists => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, accountId)
        logger.debug(s"Kafka checkBankAccountExists Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("checkBankAccountExists")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getTransactions _)}",
    messageFormat = messageFormat,
    description = "Get Transactions",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetTransactions.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetTransactions.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetTransactions(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"),
      accountId= AccountId(value="string"),
      limit=123,
      offset=123,
      fromDate="string",
      toDate="string")
    ),
    exampleInboundMessage = (
     InBoundGetTransactions(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data=List( TransactionCommons(uuid="string",
      id= TransactionId(value="string"),
      thisAccount= BankAccountCommons(accountId= AccountId(value="string"),
      accountType="string",
      balance=BigDecimal("123.321"),
      currency="string",
      name="string",
      label="string",
      iban=Option("string"),
      number="string",
      bankId= BankId(value="string"),
      lastUpdate=new Date(),
      branchId="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      accountRules=List( AccountRule(scheme="string",
      value="string")),
      accountHolder="string"),
      otherAccount= Counterparty(nationalIdentifier="string",
      kind="string",
      counterpartyId="string",
      counterpartyName="string",
      thisBankId= BankId(value="string"),
      thisAccountId= AccountId(value="string"),
      otherBankRoutingScheme="string",
      otherBankRoutingAddress=Option("string"),
      otherAccountRoutingScheme="string",
      otherAccountRoutingAddress=Option("string"),
      otherAccountProvider="string",
      isBeneficiary=true),
      transactionType="string",
      amount=BigDecimal("123.321"),
      currency="string",
      description=Option("string"),
      startDate=new Date(),
      finishDate=new Date(),
      balance=BigDecimal("123.321"))))
    ),
    adapterImplementation = Some(AdapterImplementation("Transaction", 1))
  )
  override def getTransactions(bankId: BankId, accountID: AccountId, @CacheKeyOmit callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[Box[List[Transaction]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(transactionsTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetTransactions => OutBound, InBoundGetTransactions => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, accountID, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        logger.debug(s"Kafka getTransactions Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getTransactions")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getTransaction _)}",
    messageFormat = messageFormat,
    description = "Get Transaction",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetTransaction.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetTransaction.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetTransaction(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"),
      accountId= AccountId(value="string"),
      transactionId= TransactionId(value="string"))
    ),
    exampleInboundMessage = (
     InBoundGetTransaction(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data= TransactionCommons(uuid="string",
      id= TransactionId(value="string"),
      thisAccount= BankAccountCommons(accountId= AccountId(value="string"),
      accountType="string",
      balance=BigDecimal("123.321"),
      currency="string",
      name="string",
      label="string",
      iban=Option("string"),
      number="string",
      bankId= BankId(value="string"),
      lastUpdate=new Date(),
      branchId="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      accountRules=List( AccountRule(scheme="string",
      value="string")),
      accountHolder="string"),
      otherAccount= Counterparty(nationalIdentifier="string",
      kind="string",
      counterpartyId="string",
      counterpartyName="string",
      thisBankId= BankId(value="string"),
      thisAccountId= AccountId(value="string"),
      otherBankRoutingScheme="string",
      otherBankRoutingAddress=Option("string"),
      otherAccountRoutingScheme="string",
      otherAccountRoutingAddress=Option("string"),
      otherAccountProvider="string",
      isBeneficiary=true),
      transactionType="string",
      amount=BigDecimal("123.321"),
      currency="string",
      description=Option("string"),
      startDate=new Date(),
      finishDate=new Date(),
      balance=BigDecimal("123.321")))
    ),
    adapterImplementation = Some(AdapterImplementation("Transaction", 1))
  )
  override def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(transactionTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetTransaction => OutBound, InBoundGetTransaction => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, accountID, transactionId)
        logger.debug(s"Kafka getTransaction Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getTransaction")
    
    
  messageDocs += MessageDoc(
    process = s"obp.${nameOf(getCustomersByUserId _)}",
    messageFormat = messageFormat,
    description = "Get Customers By User Id",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCustomersByUserId.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetCustomersByUserId.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetCustomersByUserId(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      userId="string")
    ),
    exampleInboundMessage = (
     InBoundGetCustomersByUserId(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="string",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="string",
      text="string"))),
      data=List( CustomerCommons(customerId="string",
      bankId="string",
      number="string",
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
      url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      creditRating= CreditRating(rating="string",
      source="string"),
      creditLimit= CreditLimit(currency="string",
      amount="string"),
      kycStatus=true,
      lastOkDate=new Date(),
      title="string",
      branchId="string",
      nameSuffix="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("Customer", 1))
  )
  override def getCustomersByUserId(userId: String, @CacheKeyOmit callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(customersByUserIdTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetCustomersByUserId => OutBound, InBoundGetCustomersByUserId => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , userId)
        logger.debug(s"Kafka getCustomersByUserId Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getCustomersByUserId")
    
    
//---------------- dynamic end ---------------------please don't modify this line
    
    
    

  //-----helper methods

  private[this] def convertToTuple[T](callContext: Option[CallContext]) (inbound: Box[InBoundTrait[T]]): (Box[T], Option[CallContext]) = {
    val boxedResult = inbound match {
      case Full(in) if (in.status.hasNoError) => Full(in.data)
      case Full(inbound) if (inbound.status.hasError) =>
        Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
      case failureOrEmpty: Failure => failureOrEmpty
    }
    (boxedResult, callContext)
  }

}
object KafkaMappedConnector_vMay2019 extends KafkaMappedConnector_vMay2019{

}





