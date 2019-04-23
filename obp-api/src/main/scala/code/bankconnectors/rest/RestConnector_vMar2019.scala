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

import java.util.Date
import java.util.UUID.randomUUID

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
import code.model.BankAccount
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
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


//---------------- dynamic start ---------------------


  // ---------- create on Wed Apr 17 20:27:50 CST 2019

  messageDocs += MessageDoc(
    process = "obp.get.InboundAdapterInfoInternal",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetAdapterInfo(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))))
      ),
    exampleInboundMessage = (
      InBoundGetAdapterInfo(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = InboundAdapterInfoInternal(errorCode = "string",
          backendMessages = List(InboundStatusMessage(source = "string",
            status = "string",
            errorCode = "string",
            text = "string")),
          name = "string",
          version = "string",
          git_commit = "string",
          date = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getAdapterInfo
  override def getAdapterInfo(callContext: Option[CallContext]): Box[(InboundAdapterInfoInternal, Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getAdapterInfo")
        sendGetRequest[InBoundGetAdapterInfo](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getAdapterInfo")

  messageDocs += MessageDoc(
    process = "obp.get.InboundAdapterInfoInternal",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetAdapterInfoFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))))
      ),
    exampleInboundMessage = (
      InBoundGetAdapterInfoFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = InboundAdapterInfoInternal(errorCode = "string",
          backendMessages = List(InboundStatusMessage(source = "string",
            status = "string",
            errorCode = "string",
            text = "string")),
          name = "string",
          version = "string",
          git_commit = "string",
          date = "string"))
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
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getAdapterInfoFuture")
        sendGetRequest[InBoundGetAdapterInfoFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getAdapterInfoFuture")

  messageDocs += MessageDoc(
    process = "obp.get.AmountOfMoney",
    messageFormat = messageFormat,
    description = "Get Challenge Threshold",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetChallengeThreshold(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = "string",
        accountId = "string",
        viewId = "string",
        transactionRequestType = "string",
        currency = "string",
        userId = "string",
        userName = "string")
      ),
    exampleInboundMessage = (
      InBoundGetChallengeThreshold(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = AmountOfMoney(currency = "string",
          amount = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getChallengeThreshold/bankId/{bankId}/accountId/{accountId}/viewId/{viewId}/transactionRequestType/{transactionRequestType}/currency/{currency}/userId/{userId}/userName/{userName}
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getChallengeThreshold", ("bankId", bankId), ("accountId", accountId), ("viewId", viewId), ("transactionRequestType", transactionRequestType), ("currency", currency), ("userId", userId), ("userName", userName))
        sendGetRequest[InBoundGetChallengeThreshold](url, callContext)
          .map { boxedResult =>
            boxedResult match {
              case Full(result) => (Full(result.data), buildCallContext(result.adapterCallContext, callContext))
              case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
            }

          }
      }
    }
  }("getChallengeThreshold")

  messageDocs += MessageDoc(
    process = "obp.post.Challenge",
    messageFormat = messageFormat,
    description = "Create Challenge",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundCreateChallenge(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        accountId = AccountId(value = "string"),
        userId = "string",
        transactionRequestType = TransactionRequestType(value = "string"),
        transactionRequestId = "string")
      ),
    exampleInboundMessage = (
      InBoundCreateChallenge(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = "string")
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /createChallenge
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]): OBPReturnType[Box[String]] = {
    val url = getUrl("createChallenge")
    val jsonStr = write(OutBoundCreateChallenge(buildAdapterCallContext(callContext), bankId, accountId, userId, transactionRequestType, transactionRequestId))
    sendPostRequest[InBoundCreateChallenge](url, callContext, jsonStr)
      .map { boxedResult =>
        boxedResult match {
          case Full(result) => (Full(result.data), buildCallContext(result.adapterCallContext, callContext))
          case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
        }

      }
  }

  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBank(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetBank(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = BankCommons(bankId = BankId(value = "string"),
          shortName = "string",
          fullName = "string",
          logoUrl = "string",
          websiteUrl = "string",
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          swiftBic = "string",
          nationalIdentifier = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBank/bankId/{bankId}
  override def getBank(bankId: BankId, callContext: Option[CallContext]): Box[(Bank, Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBank", ("bankId", bankId))
        sendGetRequest[InBoundGetBank](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBank")

  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBankFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetBankFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = BankCommons(bankId = BankId(value = "string"),
          shortName = "string",
          fullName = "string",
          logoUrl = "string",
          websiteUrl = "string",
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          swiftBic = "string",
          nationalIdentifier = "string"))
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
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBankFuture", ("bankId", bankId))
        sendGetRequest[InBoundGetBankFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
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
      OutBoundGetBanks(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))))
      ),
    exampleInboundMessage = (
      InBoundGetBanks(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(BankCommons(bankId = BankId(value = "string"),
          shortName = "string",
          fullName = "string",
          logoUrl = "string",
          websiteUrl = "string",
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          swiftBic = "string",
          nationalIdentifier = "string")))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBanks
  override def getBanks(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBanks")
        sendGetRequest[InBoundGetBanks](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBanks")

  messageDocs += MessageDoc(
    process = "obp.get.BankCommons",
    messageFormat = messageFormat,
    description = "Get Banks",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBanksFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))))
      ),
    exampleInboundMessage = (
      InBoundGetBanksFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(BankCommons(bankId = BankId(value = "string"),
          shortName = "string",
          fullName = "string",
          logoUrl = "string",
          websiteUrl = "string",
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          swiftBic = "string",
          nationalIdentifier = "string")))
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
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBanksFuture")
        sendGetRequest[InBoundGetBanksFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBanksFuture")

  messageDocs += MessageDoc(
    process = "obp.get.InboundAccountCommonCommons",
    messageFormat = messageFormat,
    description = "Get Bank Accounts By Username",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBankAccountsByUsername(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        username = "string")
      ),
    exampleInboundMessage = (
      InBoundGetBankAccountsByUsername(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(InboundAccountCommonCommons(errorCode = "string",
          bankId = "string",
          branchId = "string",
          accountId = "string",
          accountNumber = "string",
          accountType = "string",
          balanceAmount = "string",
          balanceCurrency = "string",
          owners = List("string"),
          viewsToGenerate = List("string"),
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          branchRoutingScheme = "string",
          branchRoutingAddress = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string")))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBankAccountsByUsername/username/{username}
  override def getBankAccountsByUsername(username: String, callContext: Option[CallContext]): Box[(List[InboundAccountCommon], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBankAccountsByUsername", ("username", username))
        sendGetRequest[InBoundGetBankAccountsByUsername](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBankAccountsByUsername")

  messageDocs += MessageDoc(
    process = "obp.get.InboundAccountCommonCommons",
    messageFormat = messageFormat,
    description = "Get Bank Accounts By Username",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBankAccountsByUsernameFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        username = "string")
      ),
    exampleInboundMessage = (
      InBoundGetBankAccountsByUsernameFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(InboundAccountCommonCommons(errorCode = "string",
          bankId = "string",
          branchId = "string",
          accountId = "string",
          accountNumber = "string",
          accountType = "string",
          balanceAmount = "string",
          balanceCurrency = "string",
          owners = List("string"),
          viewsToGenerate = List("string"),
          bankRoutingScheme = "string",
          bankRoutingAddress = "string",
          branchRoutingScheme = "string",
          branchRoutingAddress = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string")))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBankAccountsByUsernameFuture/username/{username}
  override def getBankAccountsByUsernameFuture(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccountCommon], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBankAccountsByUsernameFuture", ("username", username))
        sendGetRequest[InBoundGetBankAccountsByUsernameFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBankAccountsByUsernameFuture")

  messageDocs += MessageDoc(
    process = "obp.get.BankAccount",
    messageFormat = messageFormat,
    description = "Get Bank Account",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBankAccountFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        accountId = AccountId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetBankAccountFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = BankAccountCommons(accountId = AccountId(value = "string"),
          accountType = "string",
          balance = 123.123,
          currency = "string",
          name = "string",
          label = "string",
          swift_bic = Option("string"),
          iban = Option("string"),
          number = "string",
          bankId = BankId(value = "string"),
          lastUpdate = new Date(),
          branchId = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          accountRules = List(AccountRule(scheme = "string",
            value = "string")),
          accountHolder = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBankAccount/bankId/{bankId}/accountId/{accountId}
  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBankAccount", ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundGetBankAccountFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBankAccount")

  messageDocs += MessageDoc(
    process = "obp.get.CoreAccountCommons",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCoreBankAccounts(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankIdAccountIds = List(BankIdAccountId(bankId = BankId(value = "string"),
          accountId = AccountId(value = "string"))))
      ),
    exampleInboundMessage = (
      InBoundGetCoreBankAccounts(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(CoreAccount(id = "string",
          label = "string",
          bankId = "string",
          accountType = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getCoreBankAccounts/bankIdAccountIds/{bankIdAccountIds}
  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[(List[CoreAccount], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCoreBankAccounts", ("bankIdAccountIds", bankIdAccountIds))
        sendGetRequest[InBoundGetCoreBankAccounts](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getCoreBankAccounts")

  messageDocs += MessageDoc(
    process = "obp.get.CoreAccountCommons",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCoreBankAccountsFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankIdAccountIds = List(BankIdAccountId(bankId = BankId(value = "string"),
          accountId = AccountId(value = "string"))))
      ),
    exampleInboundMessage = (
      InBoundGetCoreBankAccountsFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(CoreAccount(id = "string",
          label = "string",
          bankId = "string",
          accountType = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")))))
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
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCoreBankAccountsFuture", ("bankIdAccountIds", bankIdAccountIds))
        sendGetRequest[InBoundGetCoreBankAccountsFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
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
      OutBoundCheckBankAccountExists(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        accountId = AccountId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundCheckBankAccountExists(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = BankAccountCommons(accountId = AccountId(value = "string"),
          accountType = "string",
          balance = 123.123,
          currency = "string",
          name = "string",
          label = "string",
          swift_bic = Option("string"),
          iban = Option("string"),
          number = "string",
          bankId = BankId(value = "string"),
          lastUpdate = new Date(),
          branchId = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          accountRules = List(AccountRule(scheme = "string",
            value = "string")),
          accountHolder = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /checkBankAccountExists/bankId/{bankId}/accountId/{accountId}
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("checkBankAccountExists", ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundCheckBankAccountExists](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("checkBankAccountExists")

  messageDocs += MessageDoc(
    process = "obp.get.BankAccount",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundCheckBankAccountExistsFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        accountId = AccountId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundCheckBankAccountExistsFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = BankAccountCommons(accountId = AccountId(value = "string"),
          accountType = "string",
          balance = 123.123,
          currency = "string",
          name = "string",
          label = "string",
          swift_bic = Option("string"),
          iban = Option("string"),
          number = "string",
          bankId = BankId(value = "string"),
          lastUpdate = new Date(),
          branchId = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          accountRules = List(AccountRule(scheme = "string",
            value = "string")),
          accountHolder = "string"))
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
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("checkBankAccountExistsFuture", ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundCheckBankAccountExistsFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("checkBankAccountExistsFuture")

  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyTrait",
    messageFormat = messageFormat,
    description = "Get Counterparty Trait",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCounterpartyTrait(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        accountId = AccountId(value = "string"),
        couterpartyId = "string")
      ),
    exampleInboundMessage = (
      InBoundGetCounterpartyTrait(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = CounterpartyTraitCommons(createdByUserId = "string",
          name = "string",
          description = "string",
          thisBankId = "string",
          thisAccountId = "string",
          thisViewId = "string",
          counterpartyId = "string",
          otherAccountRoutingScheme = "string",
          otherAccountRoutingAddress = "string",
          otherAccountSecondaryRoutingScheme = "string",
          otherAccountSecondaryRoutingAddress = "string",
          otherBankRoutingScheme = "string",
          otherBankRoutingAddress = "string",
          otherBranchRoutingScheme = "string",
          otherBranchRoutingAddress = "string",
          isBeneficiary = true,
          bespoke = List(CounterpartyBespoke(key = "string",
            value = "string"))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getCounterpartyTrait/bankId/{bankId}/accountId/{accountId}/couterpartyId/{couterpartyId}
  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCounterpartyTrait", ("bankId", bankId), ("accountId", accountId), ("couterpartyId", couterpartyId))
        sendGetRequest[InBoundGetCounterpartyTrait](url, callContext)
          .map { boxedResult =>
            boxedResult match {
              case Full(result) => (Full(result.data), buildCallContext(result.adapterCallContext, callContext))
              case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
            }

          }
      }
    }
  }("getCounterpartyTrait")

  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyTrait",
    messageFormat = messageFormat,
    description = "Get Counterparty By Counterparty Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCounterpartyByCounterpartyIdFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        counterpartyId = CounterpartyId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetCounterpartyByCounterpartyIdFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = CounterpartyTraitCommons(createdByUserId = "string",
          name = "string",
          description = "string",
          thisBankId = "string",
          thisAccountId = "string",
          thisViewId = "string",
          counterpartyId = "string",
          otherAccountRoutingScheme = "string",
          otherAccountRoutingAddress = "string",
          otherAccountSecondaryRoutingScheme = "string",
          otherAccountSecondaryRoutingAddress = "string",
          otherBankRoutingScheme = "string",
          otherBankRoutingAddress = "string",
          otherBranchRoutingScheme = "string",
          otherBranchRoutingAddress = "string",
          isBeneficiary = true,
          bespoke = List(CounterpartyBespoke(key = "string",
            value = "string"))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getCounterpartyByCounterpartyIdFuture/counterpartyId/{counterpartyId}
  override def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCounterpartyByCounterpartyIdFuture", ("counterpartyId", counterpartyId))
        sendGetRequest[InBoundGetCounterpartyByCounterpartyIdFuture](url, callContext)
          .map { boxedResult =>
            boxedResult match {
              case Full(result) => (Full(result.data), buildCallContext(result.adapterCallContext, callContext))
              case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
            }

          }
      }
    }
  }("getCounterpartyByCounterpartyIdFuture")

  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyTraitCommons",
    messageFormat = messageFormat,
    description = "Get Counterparties",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCounterparties(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        thisBankId = BankId(value = "string"),
        thisAccountId = AccountId(value = "string"),
        viewId = ViewId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetCounterparties(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(CounterpartyTraitCommons(createdByUserId = "string",
          name = "string",
          description = "string",
          thisBankId = "string",
          thisAccountId = "string",
          thisViewId = "string",
          counterpartyId = "string",
          otherAccountRoutingScheme = "string",
          otherAccountRoutingAddress = "string",
          otherAccountSecondaryRoutingScheme = "string",
          otherAccountSecondaryRoutingAddress = "string",
          otherBankRoutingScheme = "string",
          otherBankRoutingAddress = "string",
          otherBranchRoutingScheme = "string",
          otherBranchRoutingAddress = "string",
          isBeneficiary = true,
          bespoke = List(CounterpartyBespoke(key = "string",
            value = "string")))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getCounterparties/thisBankId/{thisBankId}/thisAccountId/{thisAccountId}/viewId/{viewId}
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): Box[(List[CounterpartyTrait], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCounterparties", ("thisBankId", thisBankId), ("thisAccountId", thisAccountId), ("viewId", viewId))
        sendGetRequest[InBoundGetCounterparties](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getCounterparties")

  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyTraitCommons",
    messageFormat = messageFormat,
    description = "Get Counterparties",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCounterpartiesFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        thisBankId = BankId(value = "string"),
        thisAccountId = AccountId(value = "string"),
        viewId = ViewId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetCounterpartiesFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(CounterpartyTraitCommons(createdByUserId = "string",
          name = "string",
          description = "string",
          thisBankId = "string",
          thisAccountId = "string",
          thisViewId = "string",
          counterpartyId = "string",
          otherAccountRoutingScheme = "string",
          otherAccountRoutingAddress = "string",
          otherAccountSecondaryRoutingScheme = "string",
          otherAccountSecondaryRoutingAddress = "string",
          otherBankRoutingScheme = "string",
          otherBankRoutingAddress = "string",
          otherBranchRoutingScheme = "string",
          otherBranchRoutingAddress = "string",
          isBeneficiary = true,
          bespoke = List(CounterpartyBespoke(key = "string",
            value = "string")))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getCounterpartiesFuture/thisBankId/{thisBankId}/thisAccountId/{thisAccountId}/viewId/{viewId}
  override def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): OBPReturnType[Box[List[CounterpartyTrait]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCounterpartiesFuture", ("thisBankId", thisBankId), ("thisAccountId", thisAccountId), ("viewId", viewId))
        sendGetRequest[InBoundGetCounterpartiesFuture](url, callContext)
          .map { boxedResult =>
            boxedResult match {
              case Full(result) => (Full(result.data), buildCallContext(result.adapterCallContext, callContext))
              case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
            }

          }
      }
    }
  }("getCounterpartiesFuture")

  messageDocs += MessageDoc(
    process = "obp.get.TransactionCommons",
    messageFormat = messageFormat,
    description = "Get Transactions",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetTransactions(bankId = BankId(value = "string"),
        accountID = AccountId(value = "string"),
        limit = 123,
        offset = 123,
        fromDate = "string",
        toDate = "string")
      ),
    exampleInboundMessage = (
      InBoundGetTransactions(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(new Transaction(uuid = "string",
          id = TransactionId(value = "string"),
          thisAccount = BankAccountCommons(accountId = AccountId(value = "string"),
            accountType = "string",
            balance = 123.123,
            currency = "string",
            name = "string",
            label = "string",
            swift_bic = Option("string"),
            iban = Option("string"),
            number = "string",
            bankId = BankId(value = "string"),
            lastUpdate = new Date(),
            branchId = "string",
            accountRoutingScheme = "string",
            accountRoutingAddress = "string",
            accountRoutings = List(AccountRouting(scheme = "string",
              address = "string")),
            accountRules = List(AccountRule(scheme = "string",
              value = "string")),
            accountHolder = "string"),
          otherAccount = Counterparty(nationalIdentifier = "string",
            kind = "string",
            counterpartyId = "string",
            counterpartyName = "string",
            thisBankId = BankId(value = "string"),
            thisAccountId = AccountId(value = "string"),
            otherBankRoutingScheme = "string",
            otherBankRoutingAddress = Option("string"),
            otherAccountRoutingScheme = "string",
            otherAccountRoutingAddress = Option("string"),
            otherAccountProvider = "string",
            isBeneficiary = true),
          transactionType = "string",
          amount = 123.123,
          currency = "string",
          description = Option("string"),
          startDate = new Date(),
          finishDate = new Date(),
          balance = 123.123)))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getTransactions/bankId/{bankId}/accountID/{accountID}/queryParams/{queryParams}
  override def getTransactions(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Box[(List[Transaction], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getTransactions", ("bankId", bankId), ("accountID", accountID), ("queryParams", queryParams))
        sendGetRequest[InBoundGetTransactions](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getTransactions")

  messageDocs += MessageDoc(
    process = "obp.get.TransactionCoreCommons",
    messageFormat = messageFormat,
    description = "Get Transactions Core",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetTransactionsCore(bankId = BankId(value = "string"),
        accountID = AccountId(value = "string"),
        limit = 123,
        offset = 123,
        fromDate = "string",
        toDate = "string")
      ),
    exampleInboundMessage = (
      InBoundGetTransactionsCore(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(TransactionCore(id = TransactionId(value = "string"),
          thisAccount = BankAccountCommons(accountId = AccountId(value = "string"),
            accountType = "string",
            balance = 123.123,
            currency = "string",
            name = "string",
            label = "string",
            swift_bic = Option("string"),
            iban = Option("string"),
            number = "string",
            bankId = BankId(value = "string"),
            lastUpdate = new Date(),
            branchId = "string",
            accountRoutingScheme = "string",
            accountRoutingAddress = "string",
            accountRoutings = List(AccountRouting(scheme = "string",
              address = "string")),
            accountRules = List(AccountRule(scheme = "string",
              value = "string")),
            accountHolder = "string"),
          otherAccount = CounterpartyCore(kind = "string",
            counterpartyId = "string",
            counterpartyName = "string",
            thisBankId = BankId(value = "string"),
            thisAccountId = AccountId(value = "string"),
            otherBankRoutingScheme = "string",
            otherBankRoutingAddress = Option("string"),
            otherAccountRoutingScheme = "string",
            otherAccountRoutingAddress = Option("string"),
            otherAccountProvider = "string",
            isBeneficiary = true),
          transactionType = "string",
          amount = 123.123,
          currency = "string",
          description = Option("string"),
          startDate = new Date(),
          finishDate = new Date(),
          balance = 123.123)))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getTransactionsCore/bankId/{bankId}/accountID/{accountID}/queryParams/{queryParams}
  override def getTransactionsCore(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Box[(List[TransactionCore], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getTransactionsCore", ("bankId", bankId), ("accountID", accountID), ("queryParams", queryParams))
        sendGetRequest[InBoundGetTransactionsCore](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getTransactionsCore")

  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "Get Transaction",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetTransaction(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        accountID = AccountId(value = "string"),
        transactionId = TransactionId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetTransaction(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = TransactionCommons(uuid = "string",
          id = TransactionId(value = "string"),
          thisAccount = BankAccountCommons(accountId = AccountId(value = "string"),
            accountType = "string",
            balance = 123.123,
            currency = "string",
            name = "string",
            label = "string",
            swift_bic = Option("string"),
            iban = Option("string"),
            number = "string",
            bankId = BankId(value = "string"),
            lastUpdate = new Date(),
            branchId = "string",
            accountRoutingScheme = "string",
            accountRoutingAddress = "string",
            accountRoutings = List(AccountRouting(scheme = "string",
              address = "string")),
            accountRules = List(AccountRule(scheme = "string",
              value = "string")),
            accountHolder = "string"),
          otherAccount = Counterparty(nationalIdentifier = "string",
            kind = "string",
            counterpartyId = "string",
            counterpartyName = "string",
            thisBankId = BankId(value = "string"),
            thisAccountId = AccountId(value = "string"),
            otherBankRoutingScheme = "string",
            otherBankRoutingAddress = Option("string"),
            otherAccountRoutingScheme = "string",
            otherAccountRoutingAddress = Option("string"),
            otherAccountProvider = "string",
            isBeneficiary = true),
          transactionType = "string",
          amount = 123.123,
          currency = "string",
          description = Option("string"),
          startDate = new Date(),
          finishDate = new Date(),
          balance = 123.123))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getTransaction/bankId/{bankId}/accountID/{accountID}/transactionId/{transactionId}
  override def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): Box[(Transaction, Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getTransaction", ("bankId", bankId), ("accountID", accountID), ("transactionId", transactionId))
        sendGetRequest[InBoundGetTransaction](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getTransaction")

  messageDocs += MessageDoc(
    process = "obp.post.Paymentv210",
    messageFormat = messageFormat,
    description = "Make Paymentv210",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundMakePaymentv210(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        fromAccount = BankAccountCommons(accountId = AccountId(value = "string"),
          accountType = "string",
          balance = 123.123,
          currency = "string",
          name = "string",
          label = "string",
          swift_bic = Option("string"),
          iban = Option("string"),
          number = "string",
          bankId = BankId(value = "string"),
          lastUpdate = new Date(),
          branchId = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          accountRules = List(AccountRule(scheme = "string",
            value = "string")),
          accountHolder = "string"),
        toAccount = BankAccountCommons(accountId = AccountId(value = "string"),
          accountType = "string",
          balance = 123.123,
          currency = "string",
          name = "string",
          label = "string",
          swift_bic = Option("string"),
          iban = Option("string"),
          number = "string",
          bankId = BankId(value = "string"),
          lastUpdate = new Date(),
          branchId = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          accountRules = List(AccountRule(scheme = "string",
            value = "string")),
          accountHolder = "string"),
        transactionRequestCommonBody = TransactionRequestCommonBodyJSONCommons(value = AmountOfMoneyJsonV121(currency = "string",
          amount = "string"),
          description = "string"),
        amount = 123.123,
        description = "string",
        transactionRequestType = TransactionRequestType(value = "string"),
        chargePolicy = "string")
      ),
    exampleInboundMessage = (
      InBoundMakePaymentv210(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = TransactionId(value = "string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /makePaymentv210
  override def makePaymentv210(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amount: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
    val url = getUrl("makePaymentv210")
    val jsonStr = write(OutBoundMakePaymentv210(buildAdapterCallContext(callContext), fromAccount, toAccount, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy))
    sendPostRequest[InBoundMakePaymentv210](url, callContext, jsonStr)
      .map { boxedResult =>
        boxedResult match {
          case Full(result) => (Full(result.data), buildCallContext(result.adapterCallContext, callContext))
          case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
        }

      }
  }

  messageDocs += MessageDoc(
    process = "obp.get.TransactionRequestCommons",
    messageFormat = messageFormat,
    description = "Get Transaction Requests210",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetTransactionRequests210(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        initiator = UserCommons(userPrimaryKey = UserPrimaryKey(value = 123),
          userId = "string",
          idGivenByProvider = "string",
          provider = "string",
          emailAddress = "string",
          name = "string"),
        fromAccount = BankAccountCommons(accountId = AccountId(value = "string"),
          accountType = "string",
          balance = 123.123,
          currency = "string",
          name = "string",
          label = "string",
          swift_bic = Option("string"),
          iban = Option("string"),
          number = "string",
          bankId = BankId(value = "string"),
          lastUpdate = new Date(),
          branchId = "string",
          accountRoutingScheme = "string",
          accountRoutingAddress = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          accountRules = List(AccountRule(scheme = "string",
            value = "string")),
          accountHolder = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetTransactionRequests210(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(TransactionRequest(id = TransactionRequestId(value = "string"),
          `type` = "string",
          from = TransactionRequestAccount(bank_id = "string",
            account_id = "string"),
          body = TransactionRequestBodyAllTypes(to_sandbox_tan = Option(TransactionRequestAccount(bank_id = "string",
            account_id = "string")),
            to_sepa = Option(TransactionRequestIban(iban = "string")),
            to_counterparty = Option(TransactionRequestCounterpartyId(counterparty_id = "string")),
            to_transfer_to_phone = Option(TransactionRequestTransferToPhone(value = AmountOfMoneyJsonV121(currency = "string",
              amount = "string"),
              description = "string",
              message = "string",
              from = FromAccountTransfer(mobile_phone_number = "string",
                nickname = "string"),
              to = ToAccountTransferToPhone(mobile_phone_number = "string"))),
            to_transfer_to_atm = Option(TransactionRequestTransferToAtm(value = AmountOfMoneyJsonV121(currency = "string",
              amount = "string"),
              description = "string",
              message = "string",
              from = FromAccountTransfer(mobile_phone_number = "string",
                nickname = "string"),
              to = ToAccountTransferToAtm(legal_name = "string",
                date_of_birth = "string",
                mobile_phone_number = "string",
                kyc_document = ToAccountTransferToAtmKycDocument(`type` = "string",
                  number = "string")))),
            to_transfer_to_account = Option(TransactionRequestTransferToAccount(value = AmountOfMoneyJsonV121(currency = "string",
              amount = "string"),
              description = "string",
              transfer_type = "string",
              future_date = "string",
              to = ToAccountTransferToAccount(name = "string",
                bank_code = "string",
                branch_number = "string",
                account = ToAccountTransferToAccountAccount(number = "string",
                  iban = "string")))),
            value = AmountOfMoney(currency = "string",
              amount = "string"),
            description = "string"),
          transaction_ids = "string",
          status = "string",
          start_date = new Date(),
          end_date = new Date(),
          challenge = TransactionRequestChallenge(id = "string",
            allowed_attempts = 123,
            challenge_type = "string"),
          charge = TransactionRequestCharge(summary = "string",
            value = AmountOfMoney(currency = "string",
              amount = "string")),
          charge_policy = "string",
          counterparty_id = CounterpartyId(value = "string"),
          name = "string",
          this_bank_id = BankId(value = "string"),
          this_account_id = AccountId(value = "string"),
          this_view_id = ViewId(value = "string"),
          other_account_routing_scheme = "string",
          other_account_routing_address = "string",
          other_bank_routing_scheme = "string",
          other_bank_routing_address = "string",
          is_beneficiary = true,
          future_date = Option("string"))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getTransactionRequests210/initiator/{initiator}/fromAccount/{fromAccount}
  override def getTransactionRequests210(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext]): Box[(List[TransactionRequest], Option[CallContext])] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getTransactionRequests210", ("initiator", initiator), ("fromAccount", fromAccount))
        sendGetRequest[InBoundGetTransactionRequests210](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getTransactionRequests210")

  messageDocs += MessageDoc(
    process = "obp.get.BranchT",
    messageFormat = messageFormat,
    description = "Get Branch",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBranchFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        branchId = BranchId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetBranchFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = BranchTCommons(branchId = BranchId(value = "string"),
          bankId = BankId(value = "string"),
          name = "string",
          address = Address(line1 = "string",
            line2 = "string",
            line3 = "string",
            city = "string",
            county = Option("string"),
            state = "string",
            postCode = "string",
            countryCode = "string"),
          location = Location(latitude = 123.123,
            longitude = 123.123,
            date = Option(new Date()),
            user = Option(BasicResourceUser(userId = "string",
              provider = "string",
              username = "string"))),
          lobbyString = Option(LobbyString(hours = "string")),
          driveUpString = Option(DriveUpString(hours = "string")),
          meta = Meta(license = License(id = "string",
            name = "string")),
          branchRouting = Option(Routing(scheme = "string",
            address = "string")),
          lobby = Option(Lobby(monday = List(OpeningTimes(openingTime = "string",
            closingTime = "string")),
            tuesday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            wednesday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            thursday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            friday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            saturday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            sunday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")))),
          driveUp = Option(DriveUp(monday = OpeningTimes(openingTime = "string",
            closingTime = "string"),
            tuesday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            wednesday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            thursday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            friday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            saturday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            sunday = OpeningTimes(openingTime = "string",
              closingTime = "string"))),
          isAccessible = Option(true),
          accessibleFeatures = Option("string"),
          branchType = Option("string"),
          moreInfo = Option("string"),
          phoneNumber = Option("string"),
          isDeleted = Option(true)))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBranchFuture/bankId/{bankId}/branchId/{branchId}
  override def getBranchFuture(bankId: BankId, branchId: BranchId, callContext: Option[CallContext]): Future[Box[(BranchT, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBranchFuture", ("bankId", bankId), ("branchId", branchId))
        sendGetRequest[InBoundGetBranchFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBranchFuture")

  messageDocs += MessageDoc(
    process = "obp.get.BranchTCommons",
    messageFormat = messageFormat,
    description = "Get Branches",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBranchesFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        limit = 123,
        offset = 123,
        fromDate = "string",
        toDate = "string")
      ),
    exampleInboundMessage = (
      InBoundGetBranchesFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(BranchTCommons(branchId = BranchId(value = "string"),
          bankId = BankId(value = "string"),
          name = "string",
          address = Address(line1 = "string",
            line2 = "string",
            line3 = "string",
            city = "string",
            county = Option("string"),
            state = "string",
            postCode = "string",
            countryCode = "string"),
          location = Location(latitude = 123.123,
            longitude = 123.123,
            date = Option(new Date()),
            user = Option(BasicResourceUser(userId = "string",
              provider = "string",
              username = "string"))),
          lobbyString = Option(LobbyString(hours = "string")),
          driveUpString = Option(DriveUpString(hours = "string")),
          meta = Meta(license = License(id = "string",
            name = "string")),
          branchRouting = Option(Routing(scheme = "string",
            address = "string")),
          lobby = Option(Lobby(monday = List(OpeningTimes(openingTime = "string",
            closingTime = "string")),
            tuesday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            wednesday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            thursday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            friday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            saturday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")),
            sunday = List(OpeningTimes(openingTime = "string",
              closingTime = "string")))),
          driveUp = Option(DriveUp(monday = OpeningTimes(openingTime = "string",
            closingTime = "string"),
            tuesday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            wednesday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            thursday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            friday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            saturday = OpeningTimes(openingTime = "string",
              closingTime = "string"),
            sunday = OpeningTimes(openingTime = "string",
              closingTime = "string"))),
          isAccessible = Option(true),
          accessibleFeatures = Option("string"),
          branchType = Option("string"),
          moreInfo = Option("string"),
          phoneNumber = Option("string"),
          isDeleted = Option(true))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getBranchesFuture/bankId/{bankId}/queryParams/{queryParams}
  override def getBranchesFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Future[Box[(List[BranchT], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getBranchesFuture", ("bankId", bankId), ("queryParams", queryParams))
        sendGetRequest[InBoundGetBranchesFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getBranchesFuture")

  messageDocs += MessageDoc(
    process = "obp.get.AtmT",
    messageFormat = messageFormat,
    description = "Get Atm",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetAtmFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        atmId = AtmId(value = "string"))
      ),
    exampleInboundMessage = (
      InBoundGetAtmFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = AtmTCommons(atmId = AtmId(value = "string"),
          bankId = BankId(value = "string"),
          name = "string",
          address = Address(line1 = "string",
            line2 = "string",
            line3 = "string",
            city = "string",
            county = Option("string"),
            state = "string",
            postCode = "string",
            countryCode = "string"),
          location = Location(latitude = 123.123,
            longitude = 123.123,
            date = Option(new Date()),
            user = Option(BasicResourceUser(userId = "string",
              provider = "string",
              username = "string"))),
          meta = Meta(license = License(id = "string",
            name = "string")),
          OpeningTimeOnMonday = Option("string"),
          ClosingTimeOnMonday = Option("string"),
          OpeningTimeOnTuesday = Option("string"),
          ClosingTimeOnTuesday = Option("string"),
          OpeningTimeOnWednesday = Option("string"),
          ClosingTimeOnWednesday = Option("string"),
          OpeningTimeOnThursday = Option("string"),
          ClosingTimeOnThursday = Option("string"),
          OpeningTimeOnFriday = Option("string"),
          ClosingTimeOnFriday = Option("string"),
          OpeningTimeOnSaturday = Option("string"),
          ClosingTimeOnSaturday = Option("string"),
          OpeningTimeOnSunday = Option("string"),
          ClosingTimeOnSunday = Option("string"),
          isAccessible = Option(true),
          locatedAt = Option("string"),
          moreInfo = Option("string"),
          hasDepositCapability = Option(true)))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getAtmFuture/bankId/{bankId}/atmId/{atmId}
  override def getAtmFuture(bankId: BankId, atmId: AtmId, callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getAtmFuture", ("bankId", bankId), ("atmId", atmId))
        sendGetRequest[InBoundGetAtmFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getAtmFuture")

  messageDocs += MessageDoc(
    process = "obp.get.AtmTCommons",
    messageFormat = messageFormat,
    description = "Get Atms",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetAtmsFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = BankId(value = "string"),
        limit = 123,
        offset = 123,
        fromDate = "string",
        toDate = "string")
      ),
    exampleInboundMessage = (
      InBoundGetAtmsFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(AtmTCommons(atmId = AtmId(value = "string"),
          bankId = BankId(value = "string"),
          name = "string",
          address = Address(line1 = "string",
            line2 = "string",
            line3 = "string",
            city = "string",
            county = Option("string"),
            state = "string",
            postCode = "string",
            countryCode = "string"),
          location = Location(latitude = 123.123,
            longitude = 123.123,
            date = Option(new Date()),
            user = Option(BasicResourceUser(userId = "string",
              provider = "string",
              username = "string"))),
          meta = Meta(license = License(id = "string",
            name = "string")),
          OpeningTimeOnMonday = Option("string"),
          ClosingTimeOnMonday = Option("string"),
          OpeningTimeOnTuesday = Option("string"),
          ClosingTimeOnTuesday = Option("string"),
          OpeningTimeOnWednesday = Option("string"),
          ClosingTimeOnWednesday = Option("string"),
          OpeningTimeOnThursday = Option("string"),
          ClosingTimeOnThursday = Option("string"),
          OpeningTimeOnFriday = Option("string"),
          ClosingTimeOnFriday = Option("string"),
          OpeningTimeOnSaturday = Option("string"),
          ClosingTimeOnSaturday = Option("string"),
          OpeningTimeOnSunday = Option("string"),
          ClosingTimeOnSunday = Option("string"),
          isAccessible = Option(true),
          locatedAt = Option("string"),
          moreInfo = Option("string"),
          hasDepositCapability = Option(true))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getAtmsFuture/bankId/{bankId}/queryParams/{queryParams}
  override def getAtmsFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Future[Box[(List[AtmT], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getAtmsFuture", ("bankId", bankId), ("queryParams", queryParams))
        sendGetRequest[InBoundGetAtmsFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getAtmsFuture")

  messageDocs += MessageDoc(
    process = "obp.post.Counterparty",
    messageFormat = messageFormat,
    description = "Create Counterparty",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundCreateCounterparty(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        name = "string",
        description = "string",
        createdByUserId = "string",
        thisBankId = "string",
        thisAccountId = "string",
        thisViewId = "string",
        otherAccountRoutingScheme = "string",
        otherAccountRoutingAddress = "string",
        otherAccountSecondaryRoutingScheme = "string",
        otherAccountSecondaryRoutingAddress = "string",
        otherBankRoutingScheme = "string",
        otherBankRoutingAddress = "string",
        otherBranchRoutingScheme = "string",
        otherBranchRoutingAddress = "string",
        isBeneficiary = true,
        bespoke = List(CounterpartyBespoke(key = "string",
          value = "string")))
      ),
    exampleInboundMessage = (
      InBoundCreateCounterparty(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = CounterpartyTraitCommons(createdByUserId = "string",
          name = "string",
          description = "string",
          thisBankId = "string",
          thisAccountId = "string",
          thisViewId = "string",
          counterpartyId = "string",
          otherAccountRoutingScheme = "string",
          otherAccountRoutingAddress = "string",
          otherAccountSecondaryRoutingScheme = "string",
          otherAccountSecondaryRoutingAddress = "string",
          otherBankRoutingScheme = "string",
          otherBankRoutingAddress = "string",
          otherBranchRoutingScheme = "string",
          otherBranchRoutingAddress = "string",
          isBeneficiary = true,
          bespoke = List(CounterpartyBespoke(key = "string",
            value = "string"))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /createCounterparty
  override def createCounterparty(name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke], callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
    val url = getUrl("createCounterparty")
    val jsonStr = write(OutBoundCreateCounterparty(buildAdapterCallContext(callContext), name, description, createdByUserId, thisBankId, thisAccountId, thisViewId, otherAccountRoutingScheme, otherAccountRoutingAddress, otherAccountSecondaryRoutingScheme, otherAccountSecondaryRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress, otherBranchRoutingScheme, otherBranchRoutingAddress, isBeneficiary, bespoke))
    sendPostRequest[InBoundCreateCounterparty](url, callContext, jsonStr)
      .map { boxedResult =>
        boxedResult.map { result =>
          (result.data, buildCallContext(result.adapterCallContext, callContext))
        }

      }
  }

  messageDocs += MessageDoc(
    process = "obp.get.CustomerCommons",
    messageFormat = messageFormat,
    description = "Get Customers By User Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCustomersByUserIdFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        userId = "string")
      ),
    exampleInboundMessage = (
      InBoundGetCustomersByUserIdFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(CustomerCommons(customerId = "string",
          bankId = "string",
          number = "string",
          legalName = "string",
          mobileNumber = "string",
          email = "string",
          faceImage = CustomerFaceImage(date = new Date(),
            url = "string"),
          dateOfBirth = new Date(),
          relationshipStatus = "string",
          dependents = 123,
          dobOfDependents = List(new Date()),
          highestEducationAttained = "string",
          employmentStatus = "string",
          creditRating = CreditRating(rating = "string",
            source = "string"),
          creditLimit = CreditLimit(currency = "string",
            amount = "string"),
          kycStatus = true,
          lastOkDate = new Date(),
          title = "string",
          branchId = "string",
          nameSuffix = "string")))
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
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCustomersByUserIdFuture", ("userId", userId))
        sendGetRequest[InBoundGetCustomersByUserIdFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getCustomersByUserIdFuture")

  messageDocs += MessageDoc(
    process = "obp.get.CheckbookOrdersJson",
    messageFormat = messageFormat,
    description = "Get Checkbook Orders",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetCheckbookOrdersFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = "string",
        accountId = "string")
      ),
    exampleInboundMessage = (
      InBoundGetCheckbookOrdersFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = CheckbookOrdersJson(account = AccountV310Json(bank_id = "string",
          account_id = "string",
          account_type = "string",
          account_routings = List(AccountRoutingJsonV121(scheme = "string",
            address = "string")),
          branch_routings = List(BranchRoutingJsonV141(scheme = "string",
            address = "string"))),
          orders = List(OrderJson(order = OrderObjectJson(order_id = "string",
            order_date = "string",
            number_of_checkbooks = "string",
            distribution_channel = "string",
            status = "string",
            first_check_number = "string",
            shipping_code = "string")))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getCheckbookOrdersFuture/bankId/{bankId}/accountId/{accountId}
  override def getCheckbookOrdersFuture(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getCheckbookOrdersFuture", ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundGetCheckbookOrdersFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getCheckbookOrdersFuture")

  messageDocs += MessageDoc(
    process = "obp.get.CardObjectJsonCommons",
    messageFormat = messageFormat,
    description = "Get Status Of Credit Card Order",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetStatusOfCreditCardOrderFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        bankId = "string",
        accountId = "string")
      ),
    exampleInboundMessage = (
      InBoundGetStatusOfCreditCardOrderFuture(adapterCallContext = AdapterCallContext(correlationId = "string",
        sessionId = Option("string"),
        adapterAuthInfo = Option(AdapterAuthInfo(userId = "string",
          username = "string",
          linkedCustomers = Option(List(BasicLindedCustomer(customerId = "string",
            customerNumber = "string",
            legalName = "string"))),
          userAuthContexts = Option(List(BasicUserAuthContext(key = "string",
            value = "string"))),
          userCbsContexts = Option(List(BasicUserCbsContext(key = "string",
            value = "string"))),
          authViews = Option(List(AuthView(view = ViewBasic(id = "string",
            name = "string",
            description = "string"),
            account = AccountBasic(id = "string",
              accountRoutings = List(AccountRouting(scheme = "string",
                address = "string")),
              customerOwners = List(InternalBasicCustomer(bankId = "string",
                customerId = "string",
                customerNumber = "string",
                legalName = "string",
                dateOfBirth = new Date())),
              userOwners = List(InternalBasicUser(userId = "string",
                emailAddress = "string",
                name = "string"))))))))),
        data = List(CardObjectJson(card_type = "string",
          card_description = "string",
          use_type = "string")))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  // url example: /getStatusOfCreditCardOrderFuture/bankId/{bankId}/accountId/{accountId}
  override def getStatusOfCreditCardOrderFuture(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(List[CardObjectJson], Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second) {
        val url = getUrl("getStatusOfCreditCardOrderFuture", ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundGetStatusOfCreditCardOrderFuture](url, callContext)
          .map { boxedResult =>
            boxedResult.map { result =>
              (result.data, buildCallContext(result.adapterCallContext, callContext))
            }

          }
      }
    }
  }("getStatusOfCreditCardOrderFuture")
    
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

  private[this] def buildAdapterCallContext(callContext: Option[CallContext]): AdapterCallContext = callContext.map(_.toAdapterCallContext).orNull

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
    variables.foldLeft(s"$baseUrl/$methodName")((url, pair) => url.concat(s"/${pair._1}/${pair._2}"))
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
        .map(it => {
          tryo {
            parse(it).extract[T]
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

  //TODO hongwei confirm the third valu: AdapterCallContext#adapterAuthInfo
  private[this] def buildCallContext(adapterCallContext: AdapterCallContext, callContext: Option[CallContext]): Option[CallContext] =
    for (cc <- callContext)
      yield cc.copy(correlationId = adapterCallContext.correlationId, sessionId = adapterCallContext.sessionId)

  private[this] def buildCallContext(boxedAdapterCallContext: Box[AdapterCallContext], callContext: Option[CallContext]): Option[CallContext] = boxedAdapterCallContext match {
    case Full(adapterCallContext) => buildCallContext(adapterCallContext, callContext)
    case _ => callContext
  }
}


object RestConnector_vMar2019 extends RestConnector_vMar2019 {

}
