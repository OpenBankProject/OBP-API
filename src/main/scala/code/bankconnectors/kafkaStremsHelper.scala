package code.bankconnectors

import java.util.UUID

import akka.actor.Actor
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import code.actorsystem.{ObpActorHelper, ObpActorInit}
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction}
import net.liftweb.util.Props
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.concurrent.{ExecutionException, Future, TimeoutException}

/**
  * Actor for accessing kafka from North side.
  */
class KafkaStreamsHelperActor extends Actor with ObpActorInit with ObpActorHelper with MdcLoggable with KafkaConfig {

  implicit val formats = DefaultFormats

  implicit val materializer = ActorMaterializer()

  import materializer._

  private def makeKeyFuture = Future(UUID.randomUUID().toString)

  private val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId(groupId)
    .withClientId(clientId)
    .withMaxWakeups(maxWakeups)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig)

  private lazy val consumer: (String => Source[ConsumerRecord[String, String], Consumer.Control]) = { topic =>
    val assignment = Subscriptions.assignmentWithOffset(new TopicPartition(topic, 0), 0)
    Consumer.plainSource[String, String](consumerSettings, assignment)
      .completionTimeout(completionTimeout)
  }

  private val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServers)
  //    .withProperty("batch.size", "0")
  //.withProperty("auto.create.topics.enable", "true")

  private val producer = producerSettings.createKafkaProducer()

  private val flow: ((String, String, String) => Source[String, Consumer.Control]) = { (topic, key, value) =>
    consumer(topic)
      .filter(msg => msg.key() == key)
      .map { msg =>
        logger.info(s"$topic with $msg")
        msg.value
      }
  }

  private val sendRequest: ((Topic, String, String) => Future[String]) = { (topic, key, value) =>
    producer.send(new ProducerRecord[String, String](topic.request, key, value))
    flow(topic.response, key, value).runWith(Sink.head)
  }

  def receive = {
    case request: Map[String, String] =>
      logger.info("kafka_request: " + request)
      val orgSender = sender
      val f = for {
        key <- makeKeyFuture
        r <- sendRequest(Topics.getBanksTopic, key, json.compactRender(Extraction.decompose(request)))
      } yield {
        json.parse(r) \\ "data"
      }

      f.recover {
        case ie: InterruptedException => json.parse(s"""{"error":"sending message to kafka interrupted: ${ie}"}""")
        case ex: ExecutionException => json.parse(s"""{"error":"could not send message to kafka: ${ex}"}""")
        case te: TimeoutException => json.parse(s"""{"error":"receiving message from kafka timed out: ${te}"}""")
        case t: Throwable => json.parse(s"""{"error":"unexpected error sending message to kafka: ${t}"}""")
      }
      f pipeTo orgSender
  }
}

case class Topic(request: String, response: String)

object Topics {
  private val requestTopic = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set")
  private val responseTopic = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set")

  val getBanksTopic = Topic(requestTopic, responseTopic)

}



