package code.bankconnectors

import java.util
import java.util.{Collection, Properties, UUID}

import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.connect.json.JsonConverter

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionException, Future}

object KafkaHelper extends KafkaHelper {


}

class KafkaHelper extends MdcLoggable {

  val requestTopic = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set")
  val responseTopic = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set")

  val producerProps = new Properties()
  producerProps.put("bootstrap.servers", Props.get("kafka.host")openOr("localhost:9092"))
  producerProps.put("acks", "all")
  producerProps.put("retries", "0")
  producerProps.put("batch.size", "16384")
  producerProps.put("linger.ms", "1")
  producerProps.put("buffer.memory", "33554432")
  producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
  producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

  val consumerProps = new Properties()
  consumerProps.put("bootstrap.servers", Props.get("kafka.host")openOr("localhost:9092"))
  consumerProps.put("group.id", UUID.randomUUID.toString)
  consumerProps.put("enable.auto.commit", "false")
  consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

  var producer = new KafkaProducer[String, String](producerProps)
  var consumer = new KafkaConsumer[String, String](consumerProps)
  consumer.subscribe(util.Arrays.asList(responseTopic))

  implicit val formats = DefaultFormats

  def getResponse(reqId: String): json.JValue = {
    println("RECEIVING...")
    val response: Box[String] = for {
      consumerMap <- tryo{ consumer.poll(100) }
      record: ConsumerRecord[String, String]  <- consumerMap.records(responseTopic)
      valid <- tryo[Boolean](record.key == reqId)
      if valid
    } yield
      record.value

    response match {
      case Full(res) =>
        val j = json.parse(res)
        println("RECEIVED: " + j.toString)
        j \\ "data"
      case Empty =>
        json.parse("""{"error":"KafkaConsumer could not fetch response"}""")
    }

  }
//          } else {
//            logger.warn("KafkaConsumer: Got null value/key from kafka. Might be south-side connector issue.")
//          }
//        }
//        return json.parse("""{"error":"KafkaConsumer could not fetch response"}""") //TODO: replace with standard message
//      }
//    catch {
//      case e: TimeoutException =>
//        logger.error("KafkaConsumer: timeout")
//        return json.parse("""{"error":"KafkaConsumer timeout"}""") //TODO: replace with standard message
//    }
//    // disconnect from kafka
//    consumer.close
//    logger.info("KafkaProducer: shutdown")
//    return json.parse("""{"info":"KafkaConsumer shutdown"}""") //TODO: replace with standard message
//  }

  /**
    * Have this function just to keep compatibility for KafkaMappedConnector_vMar2017 and  KafkaMappedConnector.scala
    * In KafkaMappedConnector.scala, we use Map[String, String]. Now we change to case class
    * eg: case class Company(name: String, address: String) -->
    * Company("TESOBE","Berlin")
    * Map(name->"TESOBE", address->"2")
    *
    * @param caseClassObject
    * @return Map[String, String]
    */
  def stransferCaseClassToMap(caseClassObject: scala.Product) =
    caseClassObject.getClass.getDeclaredFields.map(_.getName) // all field names
    .zip(caseClassObject.productIterator.to).toMap.asInstanceOf[Map[String, String]] // zipped with all values


  def processRequest(jsonRequest: JValue, reqId: String): JValue = {
    if (producer == null )
      producer = new KafkaProducer[String, String](producerProps)
    if (producer == null )
      return json.parse("""{"error":"kafka producer unavailable"}""")
    try {
      val record = new ProducerRecord(requestTopic, reqId, json.compactRender(jsonRequest))
      producer.send(record).get
    } catch {
      case ex: InterruptedException => return json.parse("""{"error":"sending message to kafka interrupted"}""")
      case ex: ExecutionException => return json.parse("""{"error":"could not send message to kafka"}""")
      case _ =>
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    val futureResponse = Future { getResponse(reqId) }
    Await.result(futureResponse, Duration.Inf)

  }

  def process(request: scala.Product): JValue = {
    val reqId = UUID.randomUUID().toString
    val mapRequest= stransferCaseClassToMap(request)
    val jsonRequest = Extraction.decompose(mapRequest)
    processRequest(jsonRequest, reqId)
  }

  def process(request: Map[String,String]): JValue = {
    val reqId = UUID.randomUUID().toString
    val jsonRequest = Extraction.decompose(request)
    processRequest(jsonRequest, reqId)
  }
}






/*
class Consumer(val zookeeper: String = Props.get("kafka.zookeeper_host").openOrThrowException("no kafka.zookeeper_host set"),
                    val topic: String     = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set"),
                    val delay: Long       = 0) extends MdcLoggable {

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
              return j \\ "data"
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

*/

/*
class requestProducer(
                     topic: String          = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set"),
                     brokerList: String     = Props.get("kafka.host")openOr("localhost:9092"),
                     clientId: String       = UUID.randomUUID().toString,
                     synchronously: Boolean = true,
                     compress: Boolean      = true,
                     batchSize: Integer     = 200,
                     messageSendMaxRetries: Integer = 3,
                     requestRequiredAcks: Integer   = -1
                   ) extends MdcLoggable {


  // determine compression codec
  //val codec = if (compress) DefaultCompressionCodec.codec else NoCompressionCodec.codec

  // configure producer
  /*val props = new Properties()
  props.put("compression.codec", codec.toString)
  props.put("producer.type", if (synchronously) "sync" else "async")
  props.put("metadata.broker.list", brokerList)
  props.put("batch.num.messages", batchSize.toString)
  props.put("message.send.max.retries", messageSendMaxRetries.toString)
  props.put("request.required.acks", requestRequiredAcks.toString)
  props.put("client.id", clientId.toString)*/

  val props = new Properties()
  props.put("bootstrap.servers", "localhost:9092")
  props.put("acks", "all")
  props.put("retries", 0)
  props.put("batch.size", 16384)
  props.put("linger.ms", 1)
  props.put("buffer.memory", 33554432)
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  // create producer
  val producer = new KafkaProducer(props)

  // create keyed message since we will use the key as id for matching response to a request
  //def kafkaMesssage(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): KeyedMessage[AnyRef, AnyRef] = {
  //  if (partition == null) {
  //    // no partiton specified
  //    new KeyedMessage(topic, key, message)
  //  } else {
  //    // specific partition
  //    new KeyedMessage(topic, key, partition, message)
  //  }
  //}

  implicit val formats = DefaultFormats

  //def send(key: String, request: Map[String, String], partition: String = null): Boolean = {
  //  val message      = Json.encode(request)
  //  // translate strings to utf8 before sending to kafka
  //  send(key.getBytes("UTF8"), message.getBytes("UTF8"), if (partition == null) null else partition.getBytes("UTF8"))
  //}

  def send(key: String, message: String): Boolean = {
    try {
      // actually send the message to kafka
      val record = new ProducerRecord(topic, Some(key), message)
      //producer.send(record)
    } catch {
      //case e: kafka.FailedToSendMessageException =>
      //logger.error("KafkaProducer: Failed to send message")
      //  return false
      case e: Throwable =>
        logger.error("KafkaProducer: Unknown error while trying to send message")
        e.printStackTrace()
        return false
    }
    true
  }

}
*/