package code.bankconnectors

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import code.actorsystem.{ObpActorHelper, ObpActorInit}
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction, JsonAST}
import net.liftweb.util.Props
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.concurrent.{ExecutionException, Future, TimeoutException}

/**
  * Actor for accessing kafka from North side.
  */
class KafkaStreamsHelperActor extends Actor with ObpActorInit with ObpActorHelper with MdcLoggable with KafkaConfig with AvroSerializer {

  implicit val formats = DefaultFormats

  implicit val materializer = ActorMaterializer()

  import materializer._

  private def makeKeyFuture = Future(scala.util.Random.nextInt(partitions) + "_" + UUID.randomUUID().toString)

  private val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId(groupId)
    .withClientId(clientId)
    .withMaxWakeups(maxWakeups)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig)

  private val consumer: ((String, Int) => Source[ConsumerRecord[String, String], Consumer.Control]) = { (topic, partition) =>
    val assignment = Subscriptions.assignmentWithOffset(new TopicPartition(topic, partition), 0)
    Consumer.plainSource(consumerSettings, assignment)
      .completionTimeout(completionTimeout)
  }

  private val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServers)
    .withProperty("batch.size", "0")
    .withParallelism(3)
  //.withProperty("auto.create.topics.enable", "true")

  private val producer = producerSettings
    .createKafkaProducer()

  private val flow: ((String, String) => Source[String, Consumer.Control]) = { (topic, key) =>
    consumer(topic, key.split("_")(0).toInt)
      .filter(msg => msg.key() == key)
      .map { msg =>
        logger.debug(s"$topic with $msg")
        msg.value
      }
  }

  private val sendRequest: ((Topic, String, String) => Future[String]) = { (topic, key, value) =>
    producer.send(new ProducerRecord[String, String](topic.request, key.split("_")(0).toInt, key, value))
    flow(topic.response, key)
      // .throttle(1, FiniteDuration(10, MILLISECONDS), 1, Shaping)
      .runWith(Sink.head)
  }

  private val parseF: (String => Future[JsonAST.JValue]) = { r =>
    Future(json.parse(r) \\ "data")
  }

  val extractF: (JsonAST.JValue => Future[Any]) = { r =>
    logger.info("kafka-response:" + r)
    Future(extractResult(r))
  }

  val decomposeF: (Map[String, String] => Future[json.JValue]) = { m =>
    Future(Extraction.decompose(m))
  }

  val decomposeF1: (Any => Future[json.JValue]) = { m =>
    Future(Extraction.decompose(m))
  }

  val serializeF: (json.JValue => Future[String]) = { m =>
    Future(json.compactRender(m))
  }

  //private val RESP: String = "{\"count\": \"\", \"data\": [], \"state\": \"\", \"pager\": \"\", \"target\": \"banks\"}"

  override def preStart(): Unit = {
    super.preStart()
    val conn = {

      val c = Props.get("connector").openOr("Jun2017")
      if (c.contains("_")) c.split("_")(1) else c
    }
    //configuration optimization is postponed
    //self ? conn
  }

  def pipeToSender(sender: ActorRef, f: Future[Any]) = f recover {
    case e: InterruptedException => json.parse(s"""{"error":"sending message to kafka interrupted"}""")
      logger.error(s"""{"error":"sending message to kafka interrupted,"${e}"}""")
    case e: ExecutionException => json.parse(s"""{"error":"could not send message to kafka"}""")
      logger.error(s"""{"error":"could not send message to kafka, "${e}"}""")
    case e: TimeoutException => json.parse(s"""{"error":"receiving message from kafka timed out"}""")
      logger.error(s"""{"error":"receiving message from kafka timed out", "${e}" "}""")
    case e: Throwable => json.parse(s"""{"error":"unexpected error sending message to kafka"}""")
      logger.error(s"""{"error":"unexpected error sending message to kafka , "${e}"}""")
  } pipeTo sender

  def receive = {
    case v: String =>
      for {
        key <- makeKeyFuture
        r <- sendRequest(Topics.version, key, v)
        jv <- parseF(r)
        any <- extractF(jv)
      } yield {
        logger.info("South Side recognises version info")
        any
      }

    case request: TopicCaseClass =>
      logger.info("kafka_request: " + request)
      val f = for {
        key <- makeKeyFuture
        d <- decomposeF1(request)
        s <- serializeF(d)
        r: String <- sendRequest(Topics.caseClassToTopic(request.getClass.getSimpleName), key, s)
        jv: JsonAST.JValue <- parseF(r)
        any: Any <- extractF(jv)
      } yield {
        any
      }
      pipeToSender(sender, f)

    case request: Map[String, String] =>
      logger.info("kafka_request: " + request)
      val orgSender = sender
      val f = for {
        key <- makeKeyFuture
        d <- decomposeF(request)
        v <- serializeF(d)
        r <- sendRequest(Topics.connectorTopic, key, v)
        jv <- parseF(r)
        any <- extractF(jv)
      } yield {
        any
      }
      pipeToSender(orgSender, f)
  }
}

case class Topic(request: String, response: String)

object Topics {
  private val requestTopic = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set")
  private val responseTopic = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set")

  val connectorTopic = Topic(requestTopic, responseTopic)

  def version = Topic("obp.q.version", "obp.R.version")

  def caseClassToTopic(className: String): Topic = {
    val version = {
      val v = Props.get("connector").openOr("Jun2017")
      val c = if (v.contains("_")) v.split("_")(1) else v
      c.replaceFirst("v", "")
    }

    Topic(s"obp.${version}.N." + className.replace("$", ""),
      s"obp.${version}.S." + className.replace("$", ""))
  }

}
