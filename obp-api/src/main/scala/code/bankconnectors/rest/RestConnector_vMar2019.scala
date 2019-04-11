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
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto.InboundGetBank
import com.openbankproject.commons.dto.rest._
import com.openbankproject.commons.model._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.runtime.universe._

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
  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "Gets the banks list on this OBP installation.",
    outboundTopic = Some(OutBoundGetBanksFuture.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InBoundGetBanksFuture.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutBoundGetBanksFuture(AuthInfoBasic())
    ),
    exampleInboundMessage = (
      InBoundGetBanksFuture(
        AuthInfoBasic(),
        List(BankCommons(
          bankId = BankId(bankIdExample.value),
          shortName = "The Royal Bank of Scotland",
          fullName = "The Royal Bank of Scotland",
          logoUrl = "http://www.red-bank-shoreditch.com/logo.gif",
          websiteUrl = "http://www.red-bank-shoreditch.com",
          bankRoutingScheme = "OBP",
          bankRoutingAddress = "rbs",
            swiftBic = "",
            nationalIdentifier =""
        )))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  // url example: /getBank/bankId/{bankId}
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
        val url = getUrl("getBank" , ("bankId", bankId))
        sendGetRequest[InBoundGetBankFuture](url, callContext) map { boxedResult =>
          boxedResult.map { result =>
            (result.data, buildCallContext(result.authInfo, callContext))
          }
        }
      }
    }
  }("getBank")

  // url example: /getBanks
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
        val url = getUrl("getBanks" )
        sendGetRequest[List[BankCommons]](url, callContext)
          .map(it => it.map((_ -> callContext)))
      }
    }
  }("getBanks")
    
  messageDocs += MessageDoc(
    process = "obp.get.getBankAccount",
    messageFormat = messageFormat,
    description = "Gets the Accounts.",
    exampleOutboundMessage = (
      OutBoundGetBankAccountFuture(
        AuthInfoBasic(),
        BankId(bankIdExample.value),
        AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
      InBoundGetBankAccountFuture(
        AuthInfoBasic(),
        BankAccountCommons(
          accountId  = AccountId(accountIdExample.value),
          accountType = accountTypeExample.value,
          balance = BigDecimal(balanceAmountExample.value),
          currency = currencyExample.value,
          name = "",
          label = labelExample.value,
          swift_bic = None,
          iban = Some(ibanExample.value),
          number = accountNumberExample.value,
          bankId = BankId(bankIdExample.value),
          lastUpdate = new Date(),
          branchId  = branchIdExample.value,
          accountRoutingScheme = accountRoutingSchemeExample.value,
          accountRoutingAddress = accountRoutingAddressExample.value,
          accountRoutings = List(AccountRouting("","")),
          accountRules = List(AccountRule("","")),
          accountHolder = ""
        )
    )),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  ) 
    
  // url example: /getBankAccount/bankId/{bankId}/accountId/{accountId}
  override def getBankAccountFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getBankAccount" , ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[InBoundGetBankAccountFuture](url, callContext)
          .map{ boxedResult =>
            boxedResult match {
              case Full(result) => (Full(result.data), buildCallContext(result.authInfo, callContext))
              case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
            }
          }
      }
    }
  }("getBankAccount")



  // url example: /checkBankAccountExists/bankId/{bankId}/accountId/{accountId}
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
        val url = getUrl("checkBankAccountExists" , ("bankId", bankId), ("accountId", accountId))
        sendGetRequest[BankAccountCommons](url, callContext)
          .map(it => it.map((_ -> callContext)))
      }
    }
  }("checkBankAccountExists")
    

  // url example: /getCounterparties/thisBankId/{thisBankId}/thisAccountId/{thisAccountId}/viewId/{viewId}
  override def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): OBPReturnType[Box[List[CounterpartyTrait]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getCounterparties" , ("thisBankId", thisBankId), ("thisAccountId", thisAccountId), ("viewId", viewId))
        sendGetRequest[InBoundGetCounterpartiesFuture](url, callContext)
          .map { boxedResult =>
            boxedResult match {
              case Full(result) => (Full(result.data), buildCallContext(result.authInfo, callContext))
              case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
            }
          }
      }
    }
  }("getCounterparties")
    

  // url example: /getTransactions/bankId/{bankId}/accountID/{accountID}/callContext/{callContext}
  override def getTransactionsFuture(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): OBPReturnType[Box[List[Transaction]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getTransactions" , ("bankId", bankId), ("accountID", accountID), ("callContext", callContext))
        sendGetRequest[List[TransactionCommons]](url, callContext)
          .map(it => (it -> callContext))
      }
    }
  }("getTransactions")
    

  // url example: /getTransaction/bankId/{bankId}/accountID/{accountID}/transactionId/{transactionId}
  override def getTransactionFuture(bankId: BankId, accountID: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getTransaction" , ("bankId", bankId), ("accountID", accountID), ("transactionId", transactionId))
        sendGetRequest[TransactionCommons](url, callContext)
          .map(it => (it -> callContext))
      }
    }
  }("getTransaction")


  // url example: /getCustomersByUserId/userId/{userId}
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
        val url = getUrl("getCustomersByUserId" , ("userId", userId))
        sendGetRequest[List[CustomerCommons]](url, callContext)
          .map(it => it.map((_ -> callContext)))
      }
    }
  }("getCustomersByUserId")
    

  //---------------- dynamic end ---------------------

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

  //TODO please modify this baseUrl to your remote api server base url of this connector
  private[this] val baseUrl = "http://127.0.0.1:9093"

  private[this] def getUrl(methodName: String, variables: (String, Any)*):String = {
    variables.foldLeft(s"$baseUrl/$methodName")((url, pair) => url.concat(s"/$pair._1/$pair._2"))
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
//        .map(it => fullBoxOrException(it ))
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

  private[this] def buildCallContext(authInfo: AuthInfoBasic, callContext: Option[CallContext]): Option[CallContext] = for(cc <- callContext) yield cc.copy(authInfo=Option(authInfo))

  private[this] def buildCallContext(boxedAuthInfo: Box[AuthInfoBasic], callContext: Option[CallContext]): Option[CallContext] = boxedAuthInfo match {
    case Full(authInfo) => buildCallContext(authInfo, callContext)
    case _ => callContext
  }
}


object RestConnector_vMar2019 extends RestConnector_vMar2019 {

}
