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

import java.util.UUID.randomUUID

import akka.http.scaladsl.model.{HttpProtocol, _}
import akka.util.ByteString
import code.api.APIFailureNewStyle
import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, OBPReturnType, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, OBPQueryParam}
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.kafka.KafkaHelper
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, _}
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.runtime.universe._

case class BankCommons (
                         bankId: BankId,
                         shortName: String,
                         fullName: String,
                         logoUrl: String,
                         websiteUrl: String,
                         bankRoutingScheme: String,
                         bankRoutingAddress: String,
                         swiftBic: String,
                         nationalIdentifier: String) extends Bank

trait RestConnector_vMar2019 extends Connector with KafkaHelper with MdcLoggable {

  implicit override val nameOfConnector = RestConnector_vMar2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Mar2019"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."


  //---------------- dynamic start ---------------------

  //TODO 1 -- Need to be generated automatically
  override def getAdapterInfoFuture(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = ???

  //TODO 2-- Need to be generated automatically
  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[BankConnector], Option[CallContext])]] = ???

  //TODO 3-- Need to be generated automatically
  //override def getBankFuture(bankId: BankId, callContext: Option[CallContext]): Future[Box[(BankConnector, Option[CallContext])]] = ???

  //TODO 4-- Need to be generated automatically
  override def checkBankAccountExistsFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None): Future[Box[(BankAccountInMemory, Option[CallContext])]] = ???

  //TODO 5-- Need to be generated automatically
  override def getBankAccountFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccountInMemory]] = ???

  //TODO 6-- Need to be generated automatically
  override def getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = ???

  //TODO 7-- Need to be generated automatically
  override def getCustomersByUserIdFuture(userId: String , callContext: Option[CallContext]): Future[Box[(List[CustomerConnector], Option[CallContext])]] = ???

  //TODO 8-- Need to be generated automatically
  override def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyConnector]]] = ???

  //TODO 9-- Need to be generated automatically
  override def getTransactionsFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): OBPReturnType[Box[List[Transaction]]] = ???

  //TODO 10-- Need to be generated automatically
  //override def getTransactionFuture(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = ???


  //TODO 3-- Need to be generated automatically
  override def getBankFuture(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = saveConnectorMetric {
    /**
      * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getBank", ("bankId" -> bankId))
        sendGetRequest[BankCommons](url, callContext)
          .map(it => it.map((_ -> callContext)))
      }
    }
  }("getBank")
  //TODO 10-- Need to be generated automatically
  override def getTransactionFuture(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = saveConnectorMetric {
    /**
      * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val url = getUrl("getTransaction", ("bankId", bankId), ("accountId", accountId), ("transactionId", transactionId))
        sendGetRequest[Transaction](url, callContext)
          .map(it => (it -> callContext))
      }
    }
  }("getTransaction")

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
}


object RestConnector_vMar2019 extends RestConnector_vMar2019 with App{
  import scala.reflect.runtime.{universe => ru}
//  val value  = this.getBanksFuture(None)
//  val value2  = this.getBankFuture(BankId("hello-bank-id"), None)
//  Thread.sleep(10000)
  val genMethodNames = List("getAdapterInfoFuture", "getBanksFuture", "getBankFuture", "checkBankAccountExistsFuture", "getBankAccountFuture", "getCoreBankAccountsFuture", "getCustomersByUserIdFuture",
"getCounterpartiesFuture", "getTransactionsFuture", "getTransactionFuture", "getAdapterInfoFuture"
)

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  private val nameSignature = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
    .map(it => Generator(it.name.toString, it.typeSignature))


//  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
//  println(symbols)
  println("-------------------")
  nameSignature.map(_.methodCode).foreach(println(_))
  println("===================")
}
case class Generator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")
  private[this] val params = tp.paramLists(0).dropRight(1).map(_.name.toString)
  private[this] val name = methodName.replaceFirst("Future$", "")
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  val signature = s"$methodName$paramAnResult"
  val pathVariables = params.map(it => s""", ("$it", $it)""").mkString
  val urlDemo = s"/$name" + params.map(it => s"/$it/{$it}").mkString
  val jsonType = if(resultType.startsWith("Future[Box[")) {
    resultType.replaceFirst("""Future\[Box\[\((.+), Option\[CallContext\]\)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  } else {
    resultType.replaceFirst("""OBPReturnType\[Box\[(.+)\]\]""", "$1").replaceFirst("(\\])|$", "Commons$1")
  }
  val lastMapStatement = if(resultType.startsWith("Future[Box[")) {
    "it.map((_ -> callContext))"
  } else {
    "(it -> callContext)"
  }
  val methodCode =
    s"""
      |  // url example: $urlDemo
      |  override def $signature = saveConnectorMetric {
      |    /**
      |      * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      |      * The real value will be assigned by Macro during compile time at this line of a code:
      |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      |      */
      |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      |    CacheKeyFromArguments.buildCacheKey {
      |      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
      |        val url = getUrl("$name" $pathVariables)
      |        sendGetRequest[$jsonType](url, callContext)
      |          .map(it => $lastMapStatement)
      |      }
      |    }
      |  }("$name")
    """.stripMargin
}