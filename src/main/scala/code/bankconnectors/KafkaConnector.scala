package code.bankconnectors

/*
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany
*/


import java.util.{Calendar, UUID, Date}

import code.model._
import net.liftweb.common.{Loggable, Full, Box, Failure}
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util.{False, Props}

import scala.concurrent.ops._
import scala.concurrent.duration._

import kafka.utils.{ZkUtils, ZKStringSerializer}
import org.I0Itec.zkclient.ZkClient
import java.util.Properties
import kafka.consumer.Consumer
import kafka.consumer._
import kafka.consumer.KafkaStream
import kafka.message._
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}



object KafkaConnector extends Connector with Loggable {

  import ZooKeeperUtils._

  //gets banks handled by this connector
  override def getBanks: List[Bank] = {
    val brokerList: String = getBrokers("localhost:2181").mkString(",")
    val reqId: String = UUID.randomUUID().toString

    // Send request to kafka, mark it with reqId so we can fetch the corresponding answer
    val producer: KafkaProducer = new KafkaProducer("Request", brokerList)
    producer.send(reqId + "|getBanks", "1")

    // Request sent, now we wait for response with the same reqId
    val consumer = new KafkaConsumer("localhost:2181", "1", "Response", 0)
    println(consumer.getResponse(reqId))
    val res: List[Bank] = null
    res
  }

  //gets bank identified by id
  override def getBank(bankId: BankId): Bank = {
    val brokerList: String = getBrokers("localhost:2181").mkString(",")
    val reqId: String = UUID.randomUUID().toString

    // Send request to kafka, mark it with reqId so we can fetch the corresponding answer
    val producer: KafkaProducer = new KafkaProducer("Request", brokerList)
    producer.send(reqId + "|getBank:" + bankId.toString, "1")

    // Request sent, now we wait for response with the same reqId
    val consumer = new KafkaConsumer("localhost:2181", "1", "Response", 0)
    println(consumer.getResponse(reqId))
    val res: Bank = null
    res
  }

  def accountExists(bankId: code.model.BankId,accountNumber: String): Boolean = ???
  def addCashTransactionAndUpdateBalance(account: code.bankconnectors.KafkaConnector.AccountType,cashTransaction: code.tesobe.CashTransaction): Unit = ???
  def createBankAndAccount(bankName: String,bankNationalIdentifier: String,accountNumber: String,accountHolderName: String): (code.model.Bank, code.model.BankAccount) = ???
  def createImportedTransaction(transaction: code.management.ImporterAPI.ImporterTransaction): net.liftweb.common.Box[code.model.Transaction] = ???
  def createSandboxBankAccount(bankId: code.model.BankId,accountId: code.model.AccountId,accountNumber: String,currency: String,initialBalance: BigDecimal,accountHolderName: String): net.liftweb.common.Box[code.model.BankAccount] = ???
  protected def createTransactionRequestImpl(transactionRequestId: code.model.TransactionRequestId,transactionRequestType: code.model.TransactionRequestType,fromAccount: code.model.BankAccount,counterparty: code.model.BankAccount,body: code.transactionrequests.TransactionRequests.TransactionRequestBody,status: String): net.liftweb.common.Box[code.transactionrequests.TransactionRequests.TransactionRequest] = ???
  def getAccountByUUID(uuid: String): net.liftweb.common.Box[code.bankconnectors.KafkaConnector.AccountType] = ???
  def getAccountHolders(bankId: code.model.BankId,accountID: code.model.AccountId): Set[code.model.User] = ???
  protected def getBankAccountType(bankId: code.model.BankId,accountId: code.model.AccountId): net.liftweb.common.Box[code.bankconnectors.KafkaConnector.AccountType] = ???
  def getMatchingTransactionCount(bankNationalIdentifier: String,accountNumber: String,amount: String,completed: java.util.Date,otherAccountHolder: String): Int = ???
  def getOtherBankAccount(bankId: code.model.BankId,accountID: code.model.AccountId,otherAccountID: String): net.liftweb.common.Box[code.model.OtherBankAccount] = ???
  def getOtherBankAccounts(bankId: code.model.BankId,accountID: code.model.AccountId): List[code.model.OtherBankAccount] = ???
  def getPhysicalCards(user: code.model.User): Set[code.model.PhysicalCard] = ???
  def getPhysicalCardsForBank(bankId: code.model.BankId,user: code.model.User): Set[code.model.PhysicalCard] = ???
  def getTransaction(bankId: code.model.BankId,accountID: code.model.AccountId,transactionId: code.model.TransactionId): net.liftweb.common.Box[code.model.Transaction] = ???
  protected def getTransactionRequestImpl(transactionRequestId: code.model.TransactionRequestId): net.liftweb.common.Box[code.transactionrequests.TransactionRequests.TransactionRequest] = ???
  protected def getTransactionRequestTypesImpl(fromAccount: code.model.BankAccount): net.liftweb.common.Box[List[code.model.TransactionRequestType]] = ???
  protected def getTransactionRequestsImpl(fromAccount: code.model.BankAccount): net.liftweb.common.Box[List[code.transactionrequests.TransactionRequests.TransactionRequest]] = ???
  def getTransactions(bankId: code.model.BankId,accountID: code.model.AccountId,queryParams: code.bankconnectors.OBPQueryParam*): net.liftweb.common.Box[List[code.model.Transaction]] = ???
  protected def makePaymentImpl(fromAccount: code.bankconnectors.KafkaConnector.AccountType,toAccount: code.bankconnectors.KafkaConnector.AccountType,amt: BigDecimal,description: String): net.liftweb.common.Box[code.model.TransactionId] = ???
  def removeAccount(bankId: code.model.BankId,accountId: code.model.AccountId): Boolean = ???
  protected def saveTransactionRequestChallengeImpl(transactionRequestId: code.model.TransactionRequestId,challenge: code.transactionrequests.TransactionRequests.TransactionRequestChallenge): net.liftweb.common.Box[Boolean] = ???
  protected def saveTransactionRequestStatusImpl(transactionRequestId: code.model.TransactionRequestId,status: String): net.liftweb.common.Box[Boolean] = ???
  protected def saveTransactionRequestTransactionImpl(transactionRequestId: code.model.TransactionRequestId,transactionId: code.model.TransactionId): net.liftweb.common.Box[Boolean] = ???
  def setAccountHolder(bankAccountUID: code.model.BankAccountUID,user: code.model.User): Unit = ???
  def setBankAccountLastUpdated(bankNationalIdentifier: String,accountNumber: String,updateDate: java.util.Date): Boolean = ???
  def updateAccountBalance(bankId: code.model.BankId,accountId: code.model.AccountId,newBalance: BigDecimal): Boolean = ???
  def updateAccountLabel(bankId: code.model.BankId,accountId: code.model.AccountId,label: String): Boolean = ???
}


object ZooKeeperUtils {

  def getBrokers(zookeeper:String): List[String] = {
    val zkClient = new ZkClient(zookeeper, 30000, 30000, ZKStringSerializer)
    val brokers = for {broker <- ZkUtils.getAllBrokersInCluster(zkClient) } yield {
      broker.host +":"+ broker.port
    }
    zkClient.close()
    brokers.toList
  }

  def getTopics(zookeeper:String): List[String] = {
    val zkClient = new ZkClient(zookeeper, 30000, 30000, ZKStringSerializer)
    val res = ZkUtils.getAllTopics(zkClient).toList
    zkClient.close()
    res
  }

}

class KafkaConsumer(val zookeeper: String,
                    val groupId: String,
                    val topic: String,
                    val delay: Long) {

  val config = createConsumerConfig(zookeeper, groupId)
  val consumer = Consumer.create(config)

  def shutdown() = {
    if (consumer != null)
      consumer.shutdown()
  }
  def createConsumerConfig(zookeeper: String, groupId: String): ConsumerConfig = {
    val props = new Properties()
    props.put("zookeeper.connect", zookeeper)
    props.put("group.id", groupId)
    props.put("auto.offset.reset", "largest")
    props.put("zookeeper.session.timeout.ms", "400")
    props.put("zookeeper.sync.time.ms", "400")
    props.put("auto.commit.interval.ms", "800")
    val config = new ConsumerConfig(props)
    config
  }
  def getResponse(reqId: String): String = {
    val topicCountMap = Map(topic -> 1)
    val consumerMap = consumer.createMessageStreams(topicCountMap)
    val streams = consumerMap.get(topic).get
    val deadline = 5 seconds fromNow
    for (stream <- streams) {
      val it = stream.iterator()
      while ( deadline.hasTimeLeft ) {
        /* TODO
        val m = it.next()
        if (m != null ) {
          val msg = new String(m.message())
          val pattern = """^(.*?)|(.*)$""".r
          pattern.findAllIn(msg).matchData foreach { m =>
            shutdown()
            return m.group(2)
          }
        }
         */
      }
    }
    shutdown()
    return "timeout"
  }
}


case class KafkaProducer(
                          topic: String,
                          brokerList: String,
                          clientId: String = UUID.randomUUID().toString,
                          synchronously: Boolean = true,
                          compress: Boolean = true,
                          batchSize: Integer = 200,
                          messageSendMaxRetries: Integer = 3,
                          requestRequiredAcks: Integer = -1
                          ) {

  val props = new Properties()

  val codec = if (compress) DefaultCompressionCodec.codec else NoCompressionCodec.codec

  props.put("compression.codec", codec.toString)
  props.put("producer.type", if (synchronously) "sync" else "async")
  props.put("metadata.broker.list", brokerList)
  props.put("batch.num.messages", batchSize.toString)
  props.put("message.send.max.retries", messageSendMaxRetries.toString)
  props.put("request.required.acks", requestRequiredAcks.toString)
  props.put("client.id", clientId.toString)

  val producer = new Producer[AnyRef, AnyRef](new ProducerConfig(props))

  def kafkaMesssage(message: Array[Byte], partition: Array[Byte]): KeyedMessage[AnyRef, AnyRef] = {
    if (partition == null) {
      new KeyedMessage(topic, message)
    } else {
      new KeyedMessage(topic, partition, message)
    }
  }

  def send(message: String, partition: String = null): Unit = send(message.getBytes("UTF8"), if (partition == null) null else partition.getBytes("UTF8"))

  def send(message: Array[Byte], partition: Array[Byte]): Unit = {
    try {
      producer.send(kafkaMesssage(message, partition))
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }
}
