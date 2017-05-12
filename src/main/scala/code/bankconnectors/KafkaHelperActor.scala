package code.bankconnectors

import java.util
import java.util.{Properties, UUID}

import akka.actor.Actor
import code.actorsystem.{ObpActorHelper, ObpActorInit}
import code.util.Helper.MdcLoggable
import net.liftweb.json.{DefaultFormats, Extraction, JValue}
import akka.pattern.ask
import net.liftweb.json
import net.liftweb.util.Props
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.errors.WakeupException

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionException, Future}

/**
  * Created by petar on 5/8/17.
  */
class KafkaHelperActor extends Actor with ObpActorInit with ObpActorHelper with MdcLoggable {

  implicit val formats = DefaultFormats

  val requestTopic = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set")
  val responseTopic = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set")

  val producerProps = new Properties()
  producerProps.put("bootstrap.servers", Props.get("kafka.bootstrap_hosts")openOr("localhost:9092"))
  producerProps.put("acks", "all")
  producerProps.put("retries", "0")
  producerProps.put("batch.size", "16384")
  producerProps.put("linger.ms", "1")
  producerProps.put("buffer.memory", "33554432")
  producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
  producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

  val consumerProps = new Properties()
  consumerProps.put("bootstrap.servers", Props.get("kafka.bootstrap_hosts")openOr("localhost:9092"))
  consumerProps.put("enable.auto.commit", "false")
  consumerProps.put("group.id", UUID.randomUUID.toString)
  consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

  var producer = new KafkaProducer[String, String](producerProps)
  var consumer = new KafkaConsumer[String, String](consumerProps)

  def getResponse(reqId: String): String = {
    var res = """{"error":"KafkaConsumer could not fetch response"}"""
    try {
      consumer.synchronized {
        consumer.subscribe(util.Arrays.asList(responseTopic))
        var run = true
        var retries = 1
        while (run && retries > 0) {
          val consumerMap = consumer.poll(100)
          val records = consumerMap.records(responseTopic).iterator
          while (records.hasNext) {
          val record = records.next
          println("FILTERING: " + record + " => " + reqId)
          retries = retries - 1
          if (record.key == reqId)
            println("FOUND >>> " + record)
            run = false
            res = record.value
          }
	      }
      }
    } catch {
      case e: WakeupException => logger.error(e)
    }
    res
  }

  def processRequest(jsonRequest: JValue, reqId: String): JValue = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val futureResponse = Future { getResponse(reqId) }
    try {
      val record = new ProducerRecord(requestTopic, reqId, json.compactRender(jsonRequest))
      producer.send(record).get
    } catch {
      case ie: InterruptedException => return json.parse(s"""{"error":"sending message to kafka interrupted: ${ie}"}""")
      case ex: ExecutionException => return json.parse(s"""{"error":"could not send message to kafka: ${ex}"}""")
      case t:Throwable => return json.parse(s"""{"error":"unexpected error sending message to kafka: ${t}"}""")
    }
    json.parse(Await.result(futureResponse, Duration("3 seconds"))) \\ "data"
  }

  def kafkaProcess(request: Map[String,String]): JValue = {
    val reqId = UUID.randomUUID().toString
    val jsonRequest = Extraction.decompose(request)
    processRequest(jsonRequest, reqId)
  }

  def receive = {
    case request: Map[String, String] =>
      logger.info("kafka_request: " + request )
      sender ! extractResult(kafkaProcess(request))
  }
}


