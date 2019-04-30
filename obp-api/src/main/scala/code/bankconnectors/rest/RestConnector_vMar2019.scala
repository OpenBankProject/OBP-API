package code.bankconnectors.rest

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE Ltd

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

import java.net.URLEncoder
import java.util.UUID.randomUUID
import java.util.Date

import akka.http.scaladsl.model.{HttpProtocol, _}
import akka.util.ByteString
import code.api.APIFailureNewStyle
import code.api.cache.Caching
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.{CallContext, OBPQueryParam}
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.kafka.KafkaHelper
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import net.liftweb.json.Serialization.write
import org.apache.commons.lang3.StringUtils

trait RestConnector_vMar2019 extends Connector with KafkaHelper with MdcLoggable {

  implicit override val nameOfConnector = RestConnector_vMar2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "March2019"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."



//---------------- dynamic start -------------------please don't modify this line
// ---------- create on Mon Apr 29 14:43:32 CEST 2019

messageDocs += MessageDoc(
    process = "obp.get.InboundAdapterInfoInternal",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundGetAdapterInfoFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundGetAdapterInfoFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
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
  // url example: /getAdapterInfoFuture
  override def getAdapterInfoFuture(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getAdapterInfoFuture" )
        sendGetRequest[InBoundGetAdapterInfoFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("getAdapterInfoFuture")
    
messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundGetBankFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundGetBankFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
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
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankFuture/bankId/{bankId}
  override def getBankFuture(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getBankFuture" , ("bankId", bankId))
        sendGetRequest[InBoundGetBankFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("getBankFuture")
    
messageDocs += MessageDoc(
    process = "obp.get.BankCommons",
    messageFormat = messageFormat,
    description = "Get Banks",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundGetBanksFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundGetBanksFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
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
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBanksFuture
  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getBanksFuture" )
        sendGetRequest[InBoundGetBanksFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("getBanksFuture")
    
messageDocs += MessageDoc(
    process = "obp.get.InboundAccountCommons",
    messageFormat = messageFormat,
    description = "Get Bank Accounts For User",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundGetBankAccountsForUserFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundGetBankAccountsForUserFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
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
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBankAccountsForUserFuture/username/{username}
  override def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getBankAccountsForUserFuture" , ("username", username))
        sendGetRequest[InBoundGetBankAccountsForUserFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("getBankAccountsForUserFuture")
    
messageDocs += MessageDoc(
    process = "obp.get.CoreAccountCommons",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundGetCoreBankAccountsFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundGetCoreBankAccountsFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
data=List( CoreAccount(id="string",
label="string",
bankId="string",
accountType="string",
accountRoutings=List( AccountRouting(scheme="string",
address="string")))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getCoreBankAccountsFuture/bankIdAccountIds/{bankIdAccountIds}
  override def getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Future[Box[(List[CoreAccount], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getCoreBankAccountsFuture" , ("bankIdAccountIds", bankIdAccountIds))
        sendGetRequest[InBoundGetCoreBankAccountsFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("getCoreBankAccountsFuture")
    
messageDocs += MessageDoc(
    process = "obp.get.BankAccount",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundCheckBankAccountExistsFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundCheckBankAccountExistsFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
data= BankAccountCommons(accountId= AccountId(value="string"),
accountType="string",
balance=123.123,
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
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /checkBankAccountExistsFuture/bankId/{bankId}/accountId/{accountId}
  override def checkBankAccountExistsFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Future[Box[(BankAccount, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("checkBankAccountExistsFuture" , ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundCheckBankAccountExistsFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("checkBankAccountExistsFuture")
    
messageDocs += MessageDoc(
    process = "obp.get.CustomerCommons",
    messageFormat = messageFormat,
    description = "Get Customers By User Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
       OutBoundGetCustomersByUserIdFuture(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
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
       InBoundGetCustomersByUserIdFuture(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
sessionId=Option("string"),
generalContext=Option(List( BasicGeneralContext(key="string",
value="string")))),
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
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getCustomersByUserIdFuture/userId/{userId}
  override def getCustomersByUserIdFuture(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getCustomersByUserIdFuture" , ("userId", userId))
        sendGetRequest[InBoundGetCustomersByUserIdFuture](url, callContext)
          .map { boxedResult =>
                                 boxedResult.map { result =>
                         (result.data, buildCallContext(result.inboundAdapterCallContext, callContext))
                    }
    
          }
      }
    }
  }("getCustomersByUserIdFuture")
    
//---------------- dynamic end ---------------------please don't modify this line
    
    

  private[this] def sendGetRequest[T : TypeTag: Manifest](url: String, callContext: Option[CallContext]) =
    sendRequest[T](url, callContext, HttpMethods.GET)

  private[this] def sendPostRequest[T : TypeTag: Manifest](url: String, callContext: Option[CallContext], entityJsonString: String) =
    sendRequest[T](url, callContext, HttpMethods.POST)

  private[this] def sendPutRequest[T : TypeTag: Manifest](url: String, callContext: Option[CallContext], entityJsonString: String) =
    sendRequest[T](url, callContext, HttpMethods.PUT)

  private[this] def sendDelteRequest[T : TypeTag: Manifest](url: String, callContext: Option[CallContext]) =
    sendRequest[T](url, callContext, HttpMethods.DELETE)

  //TODO every connector should implement this method to build authorization headers with callContext
  private[this] implicit def buildHeaders(callContext: Option[CallContext]): List[HttpHeader] = Nil

  private[this] def buildAdapterCallContext(callContext: Option[CallContext]): OutboundAdapterCallContext = callContext.map(_.toOutboundAdapterCallContext).orNull

  /**
    * some methods return type is not future, this implicit method make these method have the same body, it facilitate to generate code.
    * @param future
    * @tparam T
    * @return
    */
  private[this] implicit def convertFuture[T](future: Future[T]): T = Await.result(future, 1.minute)

  //TODO please modify this baseUrl to your remote api server base url of this connector
  private[this] val baseUrl = "http://localhost:8080/restConnector"

  private[this] def getUrl(methodName: String, variables: (String, Any)*):String = {
    // convert any type value to string, to fill in the url
    val urlValueConverter = (obj: Any) => {
      val value = obj match {
        case null => ""
        case seq: Seq[_] => seq.map(_.toString.replaceFirst("^\\w+\\((.*)\\)$", "$1")).mkString(";")
        case seq: Array[_] => seq.map(_.toString.replaceFirst("^\\w+\\((.*)\\)$", "$1")).mkString(";")
        case other => other.toString
      }
      URLEncoder.encode(value, "UTF-8")
    }

    variables.foldLeft(s"$baseUrl/$methodName")((url, pair) => url.concat(s"/${pair._1}/${urlValueConverter(pair._2)}"))
  }

  private[this] def sendRequest[T : TypeTag: Manifest](url: String, callContext: Option[CallContext], method: HttpMethod, entityJsonString: String = "") :Future[Box[T]] = {
    val request = prepareHttpRequest(url, method, HttpProtocol("HTTP/1.1"), entityJsonString).withHeaders(callContext)
    val responseFuture = makeHttpRequest(request)
    val jsonType = typeOf[T]
    responseFuture.map {
      case response @ HttpResponse(status, _, entity @ _, _) => (status, entity)
    }.flatMap {
      case (status, entity) if status.isSuccess() => extractEntity[T](entity, callContext)
      case (status, entity) => extractBody(entity) map {msg => {
        Empty ~> APIFailureNewStyle(msg, status.intValue(), callContext.map(_.toLight))
      }}
    }
  }

  private[this] def extractBody(responseEntity: ResponseEntity): Future[String] = responseEntity.toStrict(2.seconds) flatMap { e =>
    e.dataBytes
      .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
      .map(_.utf8String)
  }

  private[this] def extractEntity[T: Manifest](responseEntity: ResponseEntity, callContext: Option[CallContext], failCode: Int = 400): Future[Box[T]] = {
    this.extractBody(responseEntity)
      .map({
        case null => Empty
        case str => tryo {
          parse(str).extract[T]
        } ~> APIFailureNewStyle(s"$InvalidJsonFormat The Json body should be the ${manifest[T]} ", failCode, callContext.map(_.toLight))
      })
  }

  /**
    * interpolate url, bind variable
    * e.g: interpolateUrl("http://127.0.0.1:9093/:id/bank/:bank_id", Map("bank_id" -> "myId", "id"-> 123)):
    * result: http://127.0.0.1:9093/123/bank/myId
    * @param urlTemplate url template
    * @param variables key values
    * @return bind key and value url
    */
  def interpolateUrl(urlTemplate: String, variables: Map[String, Any]) = {
    variables.foldLeft(urlTemplate)((url, pair) => {
      val (key, value) = pair
      url
        // fill this format variables: http://rootpath/banks/{bank-id}
        .replace(s"{$key}", String.valueOf(value))
      // fill this format variables: http://rootpath/banks/:bank-id
      // url.replace(s":${key}", String.valueOf(value))
      // fill this format variables: http://rootpath/banks/:{bank-id}
      //.replaceAll(s":\\{\\s*$key\\s*\\}", String.valueOf(value))
    })
  }

  //TODO hongwei confirm the third valu: OutboundAdapterCallContext#adapterAuthInfo
  private[this] def buildCallContext(inboundAdapterCallContext: InboundAdapterCallContext, callContext: Option[CallContext]): Option[CallContext] =
    for (cc <- callContext)
      yield cc.copy(correlationId = inboundAdapterCallContext.correlationId, sessionId = inboundAdapterCallContext.sessionId)

  private[this] def buildCallContext(boxedInboundAdapterCallContext: Box[InboundAdapterCallContext], callContext: Option[CallContext]): Option[CallContext] = boxedInboundAdapterCallContext match {
    case Full(inboundAdapterCallContext) => buildCallContext(inboundAdapterCallContext, callContext)
    case _ => callContext
  }
}


object RestConnector_vMar2019 extends RestConnector_vMar2019 {

}
