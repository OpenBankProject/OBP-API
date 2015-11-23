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

import java.util.{Properties, UUID}

import scala.concurrent.ops._
import scala.concurrent.duration._

import kafka.utils.{ZkUtils, ZKStringSerializer}
import org.I0Itec.zkclient.ZkClient
import kafka.consumer.Consumer
import kafka.consumer._
import kafka.consumer.KafkaStream
import kafka.message._
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}


object ZooKeeperUtils {
  // gets brokers tracked by zookeeper
  def getBrokers(zookeeper:String): List[String] = {
    val zkClient = new ZkClient(zookeeper, 30000, 30000, ZKStringSerializer)
    val brokers = for {broker <- ZkUtils.getAllBrokersInCluster(zkClient) } yield {
      broker.host +":"+ broker.port
    }
    zkClient.close()
    brokers.toList
  }
  // gets all topics tracked by zookeeper
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
    props.put("consumer.timeout.ms", "5000")
    val config = new ConsumerConfig(props)
    config
  }
  def getResponse(reqId: String): List[Map[String, String]] = {
    val topicCountMap = Map(topic -> 1)
    val consumerMap = consumer.createMessageStreams(topicCountMap)
    val streams = consumerMap.get(topic).get
    for (stream <- streams) {
      val it = stream.iterator()
      try {
        while (it.hasNext()) {
          val m = it.next()
          val msg = new String(m.message(), "UTF8")
          val key = new String(m.key(), "UTF8")
          if (key == reqId) {
            shutdown()
            //val s = """([a-zA-Z0-9_-]*?):"(.*?)"""".r
            val p = """([a-zA-Z0-9_-]*?):"(.*?)"""".r
            val r = (for( p(k, v) <- p.findAllIn(msg) ) yield  (k -> v)).toMap[String, String]
            return List(r);
          }
        }
      }
      catch {
        case e:kafka.consumer.ConsumerTimeoutException => println("Exception: " + e.toString())
        shutdown()
        return List(Map("error" -> "timeout"))
      }
    }
    shutdown()
    return List(Map("" -> ""))
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

  def kafkaMesssage(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): KeyedMessage[AnyRef, AnyRef] = {
    if (partition == null) {
      new KeyedMessage(topic, key, message)
    } else {
      new KeyedMessage(topic, partition, key, message)
    }
  }

  def send(key: String, request: String, arguments: Map[String, String], partition: String = null): Unit = {
    val args = (for ( (k,v) <- arguments ) yield { s"""$k:"$v",""" }).mkString.replaceAll(",$", "")
    val message = s"$request:{$args}"
    send(key.getBytes("UTF8"), message.getBytes("UTF8"), if (partition == null) null else partition.getBytes("UTF8"))
  }

  def send(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): Unit = {
    try {
      producer.send(kafkaMesssage(key, message, partition))
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }
}

