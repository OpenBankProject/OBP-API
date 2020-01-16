package code.setup

import code.actorsystem.ObpActorSystem
import code.api.util.CustomJsonFormats
import code.kafka._
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.Extraction
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FeatureSpec, _}

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, _}

trait KafkaSetup extends FeatureSpec with EmbeddedKafka with KafkaHelper
  with GivenWhenThen with BeforeAndAfterAll
  with Matchers with MdcLoggable {



  implicit val formats = CustomJsonFormats.formats
  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181) //TODO the port should read from test.default.props, but fail
  implicit val stringSerializer = new StringSerializer
  implicit val stringDeserializer = new StringDeserializer

  val requestMapResponseTopics:Map[String, String] =  NorthSideConsumer.listOfTopics
    .map(Topics.createTopicByClassName)
    .map(pair => (pair.request, pair.response))
    .toMap
  val requestTopics = requestMapResponseTopics.keySet

  override def beforeAll(): Unit = {
    super.beforeAll()

    EmbeddedKafka.start()

    if(!OBPKafkaConsumer.primaryConsumer.started){
      val actorSystem = ObpActorSystem.startLocalActorSystem
      KafkaHelperActors.startLocalKafkaHelperWorkers(actorSystem)
      // Start North Side Consumer if it's not already started
      OBPKafkaConsumer.primaryConsumer.start()
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    OBPKafkaConsumer.primaryConsumer.complete()
    EmbeddedKafka.stop()
  }

  /**
    * send an object to kafka as response
    *
    * @param inBound inBound object that will send to kafka as a response
    * @tparam T Outbound type
    */
  def dispathResponse(inBound: AnyRef): Unit = {
    val inBoundStr = inBound match {
      case str: String => str
      case _ =>json.compactRender(Extraction.decompose(inBound))
    }
    Future{
      val requestKeyValue = consumeNumberKeyedMessagesFromTopics(requestTopics, 1, true)
      val (requestTopic, keyValueList) = requestKeyValue.find(_._2.nonEmpty).get
      val (key, _) = keyValueList.head
      val responseTopic = requestMapResponseTopics(requestTopic)
      publishToKafka(responseTopic, key, inBoundStr)
    }
  }

    implicit class FutureExtract[T](future: Future[T]) {
      def getContent: T = Await.result(future, 10 seconds)
    }
}
