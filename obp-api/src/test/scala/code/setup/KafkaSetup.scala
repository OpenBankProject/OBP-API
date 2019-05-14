package code.setup

import code.actorsystem.ObpActorSystem
import code.api.util.CustomJsonFormats
import code.kafka._
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FeatureSpec, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait KafkaSetup extends FeatureSpec with EmbeddedKafka with KafkaHelper
  with BeforeAndAfterEach with GivenWhenThen
  with Matchers with MdcLoggable {



  implicit val formats = CustomJsonFormats.formats
  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181) //TODO the port should read from test.default.props, but fail
  implicit val stringSerializer = new StringSerializer
  implicit val stringDeserializer = new StringDeserializer

  lazy val requestMapResponseTopics:Map[String, String] =  NorthSideConsumer.listOfTopics
    .map(Topics.createTopicByClassName)
    .map(pair => (pair.request, pair.response))
    .toMap
  lazy val requestTopics = requestMapResponseTopics.keySet

  override def beforeEach(): Unit = {
    super.beforeEach()

    EmbeddedKafka.start()

    createCustomTopic("Request", Map.empty, 10, 1)
    createCustomTopic("Response", Map.empty, 10, 1)
    if(!OBPKafkaConsumer.primaryConsumer.started){
      val actorSystem = ObpActorSystem.startLocalActorSystem
      KafkaHelperActors.startLocalKafkaHelperWorkers(actorSystem)
      // Start North Side Consumer if it's not already started
      OBPKafkaConsumer.primaryConsumer.start()
    }
  }

  override def afterEach(): Unit = {
    super.afterEach()
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
    val inBoundStr = json.compactRender(Extraction.decompose(inBound))
    Future{
      val requestKeyValue = consumeNumberKeyedMessagesFromTopics(requestTopics, 1, true)
      val (requestTopic, keyValueList) = requestKeyValue.find(_._2.nonEmpty).get
      val (key, _) = keyValueList.head
      val responseTopic = requestMapResponseTopics(requestTopic)
      publishToKafka(responseTopic, key, inBoundStr)
    }
  }

}
