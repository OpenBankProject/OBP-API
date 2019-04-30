package code.kafka

import java.util.UUID

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.kafka.ProducerSettings
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import code.actorsystem.{ObpActorHelper, ObpActorInit}
import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.util.ErrorMessages._
import code.bankconnectors.AvroSerializer
import code.kafka.actor.RequestResponseActor
import code.kafka.actor.RequestResponseActor.Request
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Failure, Full}
import net.liftweb.json
import net.liftweb.json.{Extraction, JsonAST}
import org.apache.kafka.clients.producer.{Callback, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.{ExecutionException, Future, TimeoutException}

/**
  * Actor for accessing kafka from North side.
  */
class KafkaStreamsHelperActor extends Actor with ObpActorInit with ObpActorHelper with MdcLoggable with KafkaConfig with AvroSerializer {

  implicit val formats = CustomJsonFormats.formats

  implicit val materializer = ActorMaterializer()

  import materializer._
  /**
    *Random select the partitions number from 0 to kafka.partitions value
    *The specified partition number will be inside the Key.
    */
  private def keyAndPartition = scala.util.Random.nextInt(partitions) + "_" + APIUtil.generateUUID()

  private val producerSettings = if (APIUtil.getPropsValue("kafka.use.ssl").getOrElse("false") == "true") {
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty("batch.size", "0")
      .withParallelism(3)
      .withProperty("security.protocol","SSL")
      .withProperty("ssl.truststore.location", APIUtil.getPropsValue("truststore.path").getOrElse(""))
      .withProperty("ssl.truststore.password", APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd))
      .withProperty("ssl.keystore.location",APIUtil.getPropsValue("keystore.path").getOrElse(""))
      .withProperty("ssl.keystore.password", APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd))
  } else {
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty("batch.size", "0")
      .withParallelism(3)
  }

  private val producer = producerSettings.createKafkaProducer()

  /**
    * communication with Kafka, send and receive message.
    * This method will send message to Kafka, using the specified key and partition for each topic
    * And get the message from the specified partition and filter by key
    */
  private val sendRequestAndGetResponseFromKafka: ((TopicPair, String, String) => Future[String]) = { (topic, key, value) =>
    //When we send RequestTopic message, contain the partition in it, and when we get the ResponseTopic according to the partition.
    val requestTopic = topic.request
    val responseTopic = topic.response
    if (NorthSideConsumer.listOfTopics.exists(_ == responseTopic)) {
      logger.error(s"North Kafka Consumer is not subscribed to a topic: $responseTopic")
    }
    // This actor is used to listen to a message which will be sent by NorthSideConsumer
    val actorListener = context.actorOf(Props[RequestResponseActor], key)

    /**
      * This function is used o send Kafka message in Async way to a Kafka broker
      * In case the the broker cannot accept the message an error is logged
      * @param requestTopic A topic used to send Kafka message to Adapter side
      * @param key Kafka Message key
      * @param value Kafka Message value
      */
    def sendAsync(requestTopic: String, key: String, value: String): Unit = {
      val message = new ProducerRecord[String, String](requestTopic, key, value)
      logger.debug(s" kafka producer : $message")
      producer.send(message, new Callback {
        override def onCompletion(metadata: RecordMetadata, e: Exception): Unit = {
          if (e != null) {
            val msg = e.printStackTrace()
            logger.error(s"unknown error happened in kafka producer,the following message to do producer properly: $message")
            actorListener ! PoisonPill
          }
        }
      })

    }
    //producer publishes the message to a broker
    sendAsync(requestTopic, key, value)

    import akka.pattern.ask
    // Listen to a message which will be sent by NorthSideConsumer
    (actorListener ? Request(key, value)).mapTo[String]
  }

  private val stringToJValueF: (String => Future[JsonAST.JValue]) = { r =>
    logger.debug("kafka-consumer-stringToJValueF:" + r)
    Future(json.parse(r))
  }

  val extractJValueToAnyF: (JsonAST.JValue => Future[Any]) = { r =>
    logger.debug("kafka-consumer-extractJValueToAnyF:" + r)
    Future(extractResult(r))
  }

  val anyToJValueF: (Any => Future[json.JValue]) = { m =>
    logger.debug("kafka-produce-anyToJValueF:" + m)
    Future(Extraction.decompose(m))
  }

  val serializeF: (json.JValue => Future[String]) = { m =>
    logger.debug("kafka-produce-serializeF:" + m)
    Future(json.compactRender(m))
  }

  //private val RESP: String = "{\"count\": \"\", \"data\": [], \"state\": \"\", \"pager\": \"\", \"target\": \"banks\"}"

  override def preStart(): Unit = {
    super.preStart()
    val conn = {

      val c = APIUtil.getPropsValue("connector").openOr("June2017")
      if (c.contains("_")) c.split("_")(1) else c
    }
    //configuration optimization is postponed
    //self ? conn
  }

  /**
    * Check the Future, if there are Exceptions, recover the Exceptions to specific JValue 
    * @param sender the sender who send the message to the Actor
    * @param future the future need to be checked 
    *
    * @return If there is no exception, pipeTo sender
    *         If there is exception, recover to JValue to sender 
    */
  def pipeToSender(sender: ActorRef, future: Future[Any]) = future recover {
    case e: InterruptedException =>
      logger.error(KafkaInterruptedException,e)
      Failure(KafkaInterruptedException+e.toString,Full(e),None)
    case e: ExecutionException =>
      logger.error(KafkaExecutionException,e)
      Failure(KafkaExecutionException+e.toString,Full(e),None)
    case e: TimeoutException =>
      logger.error(KafkaStreamTimeoutException,e)
      Failure(KafkaStreamTimeoutException+e.toString,Full(e),None)
    case e: Throwable =>
      logger.error(KafkaUnknownError,e)
      Failure(KafkaUnknownError+e.toString,Full(e),None)
  } pipeTo sender

  def receive = {
    case value: String =>
      logger.debug("kafka_request[value]: " + value)
      for {
        t <- Future(Topics.topicPairHardCode) // Just have two Topics: obp.request.version and obp.response.version
        r <- sendRequestAndGetResponseFromKafka(t, keyAndPartition, value)
        jv <- stringToJValueF(r)
        any <- extractJValueToAnyF(jv)
      } yield {
        logger.debug("South Side recognises version info")
        any
      }

    // This is for KafkaMappedConnector_vJune2017, the request is TopicTrait  
    case request: TopicTrait =>
      logger.debug("kafka_request[TopicCaseClass]: " + request)
      val f = for {
        t <- Future(Topics.createTopicByClassName(request.getClass.getSimpleName))
        d <- anyToJValueF(request)
        s <- serializeF(d)
        r <- sendRequestAndGetResponseFromKafka(t,keyAndPartition, s)
        jv <- stringToJValueF(r)
        any <- extractJValueToAnyF(jv)
      } yield {
        any
      }
      pipeToSender(sender, f)

    // This is for KafkaMappedConnector_JVMcompatible, KafkaMappedConnector_vMar2017 and KafkaMappedConnector, the request is Map[String, String]  
    case request: Map[_, _] =>
      logger.debug("kafka_request[Map[String, String]]: " + request)
      val orgSender = sender
      val f = for {
        t <- Future(Topics.topicPairFromProps) // Just have two Topics: Request and Response
        d <- anyToJValueF(request)
        v <- serializeF(d)
        r <- sendRequestAndGetResponseFromKafka(t, keyAndPartition, v)
        jv <- stringToJValueF(r)
        any <- extractJValueToAnyF(jv)
      } yield {
        any
      }
      pipeToSender(orgSender, f)
      
    // This is used to send Outbound Adapter Error to Kafka topic responsable for it
    case request: OutboundAdapterError =>
      val key = APIUtil.generateUUID()
      val value = request.error
      val topic = s"from.obp.api.${apiInstanceId}.to.adapter.mf.caseclass.OutboundAdapterError"
      val message = new ProducerRecord[String, String](topic, key, value)
      logger.debug(s" kafka producer's OutboundAdapterError : $message")
      producer.send(message, new Callback {
        override def onCompletion(metadata: RecordMetadata, e: Exception): Unit = {
          if (e != null) {
            val msg = e.printStackTrace()
            logger.error(s"unknown error happened in kafka producer's OutboundAdapterError, the following message to do producer properly: $message")
          }
        }
      })
  }
}

/**
  * This case class design an error send to Kafka topic "from.obp.api.${apiInstanceId}.to.adapter.mf.caseclass.OutboundAdapterError
  * @param error the error message sent to Kafka
  */
case class OutboundAdapterError(error: String)

/**
  * This case class design a pair of Topic, for both North and South side.
  * They are a pair
  * @param request  eg: obp.June2017.N.GetBanks
  * @param response eg: obp.June2017.S.GetBanks
  */
case class TopicPair(request: String, response: String)

object Topics extends KafkaConfig {

  /**
    * Two topics:
    * Request : North is producer, South is the consumer. North --> South
    * Response: South is producer, North is the consumer. South --> North
    */
  private val requestTopic = APIUtil.getPropsValue("kafka.request_topic").openOr("Request")
  private val responseTopic = APIUtil.getPropsValue("kafka.response_topic").openOr("Response")

  /**
    * set in props, we have two topics: Request and Response
    */
  val topicPairFromProps = TopicPair(requestTopic, responseTopic)

  def topicPairHardCode = TopicPair("obp.Request.version", "obp.Response.version")

  def createTopicByClassName(className: String): TopicPair = {

    /**
      *  eg: 
      *  from.obp.api.1.to.adapter.mf.caseclass.GetBank
      *  to.obp.api.1.caseclass.GetBank
      */
    TopicPair(
      s"from.obp.api.${apiInstanceId}.to.adapter.mf.caseclass.${className.replace("$", "")}",
      s"to.obp.api.${apiInstanceId}.caseclass.${className.replace("$", "")}"
    )
  }

}
