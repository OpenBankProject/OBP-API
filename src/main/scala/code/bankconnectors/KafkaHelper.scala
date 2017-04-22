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
import org.apache.kafka.common.errors.{TimeoutException, WakeupException}
import org.apache.kafka.connect.json.JsonConverter

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionException, Future}

object KafkaHelper extends KafkaHelper {

}


class KafkaHelper extends MdcLoggable {

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

  implicit val formats = DefaultFormats

  def getResponse(reqId: String): json.JValue = {
    //if (consumer == null )
    //  consumerProps.remove("group.id")
    //  consumerProps.put("group.id", UUID.randomUUID.toString)
    //  consumer = new KafkaConsumer[String, String](consumerProps)
    //if (consumer == null )
    //  return json.parse("""{"error":"kafka consumer unavailable"}""")
    var res = json.parse("""{"error":"KafkaConsumer could not fetch response"}""")
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
            res = json.parse(record.value) \\ "data"
          }
	}
      }
    } catch {
      case e: WakeupException => logger.error(e)
    }
    res
  }


  def processRequest(jsonRequest: JValue, reqId: String): JValue = {
    //if (producer == null )
    //  producer = new KafkaProducer[String, String](producerProps)
    //if (producer == null )
    //  return json.parse("""{"error":"kafka producer unavailable"}""")
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
    Await.result(futureResponse, Duration("3 seconds"))
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
}

