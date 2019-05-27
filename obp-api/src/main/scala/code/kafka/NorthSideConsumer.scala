package code.kafka


import java.util.regex.Pattern

import code.api.util.APIUtil
import code.util.ClassScanUtils
import code.util.Helper.MdcLoggable
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer

object NorthSideConsumer {

  private[this] val outboundNamePattern= Pattern.compile("""com\.openbankproject\.commons\..*(OutBound.+)""")

  val listOfTopics : List[String] = (Set(
    "OutboundGetAdapterInfo",
    "OutboundGetBanks",
    "OutboundGetBank",
    "OutboundGetUserByUsernamePassword",
    "OutboundGetAccounts",
    "OutboundGetAccountbyAccountID",
    "OutboundCheckBankAccountExists",
    "OutboundGetCoreBankAccounts",
    "OutboundGetCoreBankAccounts",
    "OutboundGetTransactions",
    "OutboundGetTransaction",
    "OutboundCreateTransaction",
    "OutboundGetBranches",
    "OutboundGetBranch",
    "OutboundGetAtms",
    "OutboundGetAtm",
    "OutboundCreateChallengeJune2017",
    "OutboundCreateCounterparty",
    "OutboundGetTransactionRequests210",
    "OutboundGetCounterparties",
    "OutboundGetCounterpartyByCounterpartyId",
    "OutboundGetCounterparty",
    "OutboundCounterparty",
    "OutboundGetCounterpartyById",
    "OutboundTransactionRequests",
    "OutboundGetCustomersByUserId",
    "OutboundGetCheckbookOrderStatus",
    "OutboundGetCreditCardOrderStatus",
    "OutboundGetBankAccountsHeld",
    "OutboundGetChallengeThreshold",
    "OutboundCreateChallengeSept2018",
    "ObpApiLoopback" //This topic is tricky now, it is just used in api side: api produce and consumer it. Not used over adapter. Only for test api <--> kafka.
  ) ++ ClassScanUtils.findTypes(classInfo => outboundNamePattern.matcher(classInfo.name).matches())
    .map(outboundNamePattern.matcher(_).replaceFirst("$1"))).toList

  def consumerProperties(brokers: String, group: String, keyDeserealizer: String, valueDeserealizer: String): Map[String, String] = {
    if (APIUtil.getPropsValue("kafka.use.ssl").getOrElse("false") == "true") {
      Map[String, String](
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
        ConsumerConfig.GROUP_ID_CONFIG -> group,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> OBPKafkaConsumer.autoOffsetResetConfig,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> keyDeserealizer,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> valueDeserealizer,
        "security.protocol" -> "SSL",
        "ssl.truststore.location" -> APIUtil.getPropsValue("truststore.path").getOrElse(""),
        "ssl.truststore.password" -> APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd),
        "ssl.keystore.location" -> APIUtil.getPropsValue("keystore.path").getOrElse(""),
        "ssl.keystore.password" -> APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)
      )
    } else {
      Map[String, String](
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
        ConsumerConfig.GROUP_ID_CONFIG -> group,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> OBPKafkaConsumer.autoOffsetResetConfig,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> keyDeserealizer,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> valueDeserealizer
      )
    }
  }

  def apply[K, V](brokers: String, topic: String, group: String, processor: MessageProcessorTrait[K, V]): NorthSideConsumer[K, V] =
    new NorthSideConsumer[K, V](brokers, topic, group, classOf[StringDeserializer].getName, classOf[StringDeserializer].getName, processor)
}

class NorthSideConsumer[K, V](brokers: String, topic: String, group: String, keyDeserealizer: String, valueDeserealizer: String,
                              processor: MessageProcessorTrait[K, V]) extends Runnable with MdcLoggable with KafkaConfig {

  import NorthSideConsumer._

  import scala.collection.JavaConversions._

  val consumer = new KafkaConsumer[K, V](consumerProperties(brokers, group, keyDeserealizer, valueDeserealizer))
  //The following topic is for loopback, only for testing api <--> kafka
  val apiLoopbackTopic = s"from.${clientId}.to.adapter.mf.caseclass.ObpApiLoopback"
  val allTopicsOverAdapter= listOfTopics.map(t => s"to.${clientId}.caseclass.$t")
  //we use the same topic to send to Kakfa and listening the same topic to get the message back.
  //So there is no to.obp.api.1.caseclass..ObpApiLoopback at all. Just use `apiLoopbackTopic` in the response topic.
  val allTopicsApiListening: List[String] = allTopicsOverAdapter :+ apiLoopbackTopic
  consumer.subscribe(allTopicsApiListening)

  @volatile var completed = false
  @volatile var started = false

  def complete(): Unit = {
    completed = true
  }

  override def run(): Unit = {
    while (!completed) {
      val records = consumer.poll(100)
      for (record <- records) {
        processor.processMessage(record)
      }
    }
    consumer.close()
    logger.info("Consumer closed")
  }

  def start(): Unit = {
    if(!started) {
      logger.info("Consumer started")
      val t = new Thread(this)
      t.start()
      started = true
    }
  }

}
