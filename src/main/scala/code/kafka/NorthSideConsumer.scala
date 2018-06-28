package code.kafka


import akka.actor.ActorContext
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer

object NorthSideConsumer {
  private val AUTOCOMMITINTERVAL = "1000" // Frequency off offset commits
  private val SESSIONTIMEOUT = "30000"    // The timeout used to detect failures - should be greater then processing time
  private val MAXPOLLRECORDS = "10"       // Max number of records consumed in a single poll

  val listOfTopics = List(
    "OutboundGetAdapterInfo",
    "OutboundGetBanks",
    "OutboundGetBank",
    "OutboundGetUserByUsernamePassword",
    "OutboundGetAccounts",
    "OutboundGetAccountbyAccountID",
    "OutboundCheckBankAccountExists",
    "OutboundGetCoreBankAccounts",
    "OutboundGetCoreBankOutboundGetTransactionsAccounts",
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
  )

  def consumerProperties(brokers: String, group: String, keyDeserealizer: String, valueDeserealizer: String): Map[String, String] = {
    if (APIUtil.getPropsValue("kafka.use.ssl").getOrElse("false") == "true") {
      Map[String, String](
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
        ConsumerConfig.GROUP_ID_CONFIG -> group,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> KafkaConsumer.autoOffsetResetConfig,
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
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> KafkaConsumer.autoOffsetResetConfig,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> keyDeserealizer,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> valueDeserealizer
      )
    }
  }

  def apply[K, V](brokers: String, topic: String, group: String, processor: RecordProcessorTrait[K, V]): NorthSideConsumer[K, V] =
    new NorthSideConsumer[K, V](brokers, topic, group, classOf[StringDeserializer].getName, classOf[StringDeserializer].getName, processor)
}

class NorthSideConsumer[K, V](brokers: String, topic: String, group: String, keyDeserealizer: String, valueDeserealizer: String,
                              processor: RecordProcessorTrait[K, V]) extends Runnable with MdcLoggable with KafkaConfig {

  import NorthSideConsumer._
  import scala.collection.JavaConversions._

  val consumer = new KafkaConsumer[K, V](consumerProperties(brokers, group, keyDeserealizer, valueDeserealizer))

  /*
  import java.util
  import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
  import org.apache.kafka.common.TopicPartition
  val listener: ConsumerRebalanceListener = new ConsumerRebalanceListener() {
  public void onPartitionsAssigned(Collection<TopicPartition>
      partitions) { 2
    }

    public void onPartitionsRevoked(Collection<TopicPartition>
      partitions) {
        System.out.println("Lost partitions in rebalance.
          Committing current
        offsets:" + currentOffsets);
        consumer.commitSync(currentOffsets); 3
    }


    override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = {}

    override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = consumer.commitSync()
  }

  import java.util.regex.Pattern
  val string = s"to.obp.api.1.caseclass.*"
  val pattern: Pattern = Pattern.compile(string)
  
  consumer.subscribe(pattern, listener)
  pattern.matcher("to.obp.api.1.caseclass.*")
  */

  consumer.subscribe(listOfTopics.map(t => s"to.${clientId}.caseclass.$t"))

  var completed = false
  var started = false
  var actorContext: ActorContext = null

  def complete(): Unit = {
    completed = true
  }

  override def run(): Unit = {
    while (!completed) {
      val records = consumer.poll(100)
      for (record <- records) {
        processor.processRecord(record, actorContext)
      }
    }
    consumer.close()
    logger.info("Consumer closed")
  }

  def start(cnt: ActorContext): Unit = {
    if(!started) {
      actorContext = cnt
      logger.info("Consumer started")
      val t = new Thread(this)
      t.start()
      started = true
    }
  }

}
