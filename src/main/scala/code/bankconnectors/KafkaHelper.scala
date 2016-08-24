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

import kafka.consumer.{Consumer, _}
import kafka.message._
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.utils.Json
import net.liftweb.common.Loggable
import net.liftweb.json
import net.liftweb.json._
import net.liftweb.util.Props


class KafkaConsumer(val zookeeper: String = Props.get("kafka.zookeeper_host").openOrThrowException("no kafka.zookeeper_host set"),
                    val topic: String     = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set"),
                    val delay: Long       = 0) extends Loggable {

  val zkProps = new Properties()
  zkProps.put("log4j.logger.org.apache.zookeeper", "ERROR")
  org.apache.log4j.PropertyConfigurator.configure(zkProps)

  def createConsumerConfig(zookeeper: String, groupId: String): ConsumerConfig = {
    val props = new Properties()
    props.put("zookeeper.connect", zookeeper)
    props.put("group.id", groupId)
    props.put("auto.offset.reset", "smallest")
    props.put("auto.commit.enable", "true")
    props.put("zookeeper.sync.time.ms", "2000")
    props.put("auto.commit.interval.ms", "1000")
    props.put("zookeeper.session.timeout.ms", "6000")
    props.put("zookeeper.connection.timeout.ms", "6000")
    props.put("consumer.timeout.ms", "20000")
    val config = new ConsumerConfig(props)
    config
  }

  def getResponse(reqId: String): json.JValue = {
    // create consumer with unique groupId in order to prevent race condition with kafka
    val config = createConsumerConfig(zookeeper, UUID.randomUUID.toString)
    val consumer = Consumer.create(config)
    // recreate stream for topic if not existing
    val consumerMap = consumer.createMessageStreams(Map(topic -> 1))

    val streams = consumerMap.get(topic).get
    // process streams
    for (stream <- streams) {
      val it = stream.iterator()
      try {
        // wait for message
        while (it.hasNext()) {
          val mIt = it.next()
          // skip null entries
          if (mIt != null && mIt.key != null && mIt.message != null) {
            val msg = new String(mIt.message(), "UTF8")
            val key = new String(mIt.key(), "UTF8")
            // check if the id matches
            if (key == reqId) {
              // Parse JSON message
              val j = json.parse(msg)
              // disconnect from Kafka
              consumer.shutdown()
              // return as JSON
              return j
            }
          } else {
            logger.warn("KafkaConsumer: Got null value/key from kafka. Might be south-side connector issue.")
          }
        }
        return json.parse("""{"error":"KafkaConsumer could not fetch response"}""") //TODO: replace with standard message
      }
      catch {
        case e:kafka.consumer.ConsumerTimeoutException =>
          logger.error("KafkaConsumer: timeout")
          return json.parse("""{"error":"KafkaConsumer timeout"}""") //TODO: replace with standard message
      }
    }
    // disconnect from kafka
    consumer.shutdown()
    logger.info("KafkaProducer: shutdown")
    return json.parse("""{"info":"KafkaConsumer shutdown"}""") //TODO: replace with standard message
  }
}


class KafkaProducer(
                          topic: String          = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set"),
                          brokerList: String     = Props.get("kafka.host")openOr("localhost:9092"),
                          clientId: String       = UUID.randomUUID().toString,
                          synchronously: Boolean = true,
                          compress: Boolean      = true,
                          batchSize: Integer     = 200,
                          messageSendMaxRetries: Integer = 3,
                          requestRequiredAcks: Integer   = -1
                          ) extends Loggable {


  // determine compression codec
  val codec = if (compress) DefaultCompressionCodec.codec else NoCompressionCodec.codec

  // configure producer
  val props = new Properties()
  props.put("compression.codec", codec.toString)
  props.put("producer.type", if (synchronously) "sync" else "async")
  props.put("metadata.broker.list", brokerList)
  props.put("batch.num.messages", batchSize.toString)
  props.put("message.send.max.retries", messageSendMaxRetries.toString)
  props.put("request.required.acks", requestRequiredAcks.toString)
  props.put("client.id", clientId.toString)

  // create producer
  val producer = new Producer[AnyRef, AnyRef](new ProducerConfig(props))

  // create keyed message since we will use the key as id for matching response to a request
  def kafkaMesssage(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): KeyedMessage[AnyRef, AnyRef] = {
    if (partition == null) {
      // no partiton specified
      new KeyedMessage(topic, key, message)
    } else {
      // specific partition 
      new KeyedMessage(topic, key, partition, message)
    }
  }

  implicit val formats = DefaultFormats

  def send(key: String, request: String, arguments: Map[String, String], partition: String = null): Boolean = {
    // create message using request and arguments strings
    val reqCommand   = Map(request -> arguments)
    val message      = Json.encode(reqCommand)
    // translate strings to utf8 before sending to kafka
    send(key.getBytes("UTF8"), message.getBytes("UTF8"), if (partition == null) null else partition.getBytes("UTF8"))
  }

  def send(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): Boolean = {
    try {
      // actually send the message to kafka
      producer.send(kafkaMesssage(key, message, partition))
    } catch {
      case e: kafka.common.FailedToSendMessageException =>
        logger.error("KafkaProducer: Failed to send message")
        return false
      case e: Throwable =>
        logger.error("KafkaProducer: Unknown error while trying to send message")
        e.printStackTrace()
        return false
    }
    true
  }

}
