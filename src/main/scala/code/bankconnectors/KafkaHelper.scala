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
  consumerProps.put("enable.auto.commit", "false")
  consumerProps.put("group.id", UUID.randomUUID.toString)
  consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

  var producer = new KafkaProducer[String, String](producerProps)

  val consumer = new KafkaConsumer[String, String](consumerProps)
  consumer.subscribe(util.Arrays.asList(responseTopic))

  implicit val formats = DefaultFormats
/*



We'll use automatic assignment for the example application. Most of our consumer
code will be the same as it was for the simple consumer seen in Part 1. The only difference
is that we'll pass an instance of ConsumerRebalanceListener as a second argument
to our KafkaConsumer.subscribe() method. Kafka will call methods of this class every
time it either assigns or revokes a partition to this consumer. We'll override
ConsumerRebalanceListener's onPartitionsRevoked() and onPartitionsAssigned() methods
and print the list of partitions that were assigned or revoked from this subscriber.

   private static class ConsumerThread extends Thread {
     private String topicName;
     private String groupId;
     private KafkaConsumer<String, String> kafkaConsumer;

     public ConsumerThread(String topicName, String groupId) {
         this.topicName = topicName;
         this.groupId = groupId;
     }

     public void run() {
         Properties configProperties = new Properties();
         configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
         configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
         configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
         configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

         //Figure out where to start processing messages from
         kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
         kafkaConsumer.subscribe(Arrays.asList(topicName), new ConsumerRebalanceListener() {
             public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                 System.out.printf("%s topic-partitions are revoked from this consumer\n", Arrays.toString(partitions.toArray()));
             }
             public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                 System.out.printf("%s topic-partitions are assigned to this consumer\n", Arrays.toString(partitions.toArray()));
             }
         });
         //Start processing messages
         try {
             while (true) {
                 ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                 for (ConsumerRecord<String, String> record : records)
                     System.out.println(record.value());
             }
         } catch (WakeupException ex) {
             System.out.println("Exception caught " + ex.getMessage());
         } finally {
             kafkaConsumer.close();
             System.out.println("After closing KafkaConsumer");
         }
     }

     public KafkaConsumer<String, String> getKafkaConsumer() {
         return this.kafkaConsumer;
     }
}


If you use the value of the last argument equal to 0, the consumer will assume that
you want to start from the beginning, so it will call a kafkaConsumer.seekToBeginning()
method for each of its partitions. If you pass a value of -1 it will assume that you
want to ignore the existing messages and only consume messages published after the
consumer has been restarted. In this case it will call kafkaConsumer.seekToEnd()
on each of the partitions. Finally, if you specify any value other than 0 or -1 it
will assume that you have specified the offset that you want the consumer to start from;
for example, if you pass the third value as 5, then on restart the consumer will consume
messages with an offset greater than 5. For this it would call kafkaConsumer.seek(<topicname>, <startingoffset>).


  private static class ConsumerThread extends Thread{
    private String topicName;
    private String groupId;
    private long startingOffset;
    private KafkaConsumer<String,String> kafkaConsumer;

    public ConsumerThread(String topicName, String groupId, long startingOffset){
        this.topicName = topicName;
        this.groupId = groupId;
        this.startingOffset=startingOffset;
    }
    public void run() {
        Properties configProperties = new Properties();
        configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, "offset123");
        configProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);

        //Figure out where to start processing messages from
        kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
        kafkaConsumer.subscribe(Arrays.asList(topicName), new ConsumerRebalanceListener() {
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                System.out.printf("%s topic-partitions are revoked from this consumer\n", Arrays.toString(partitions.toArray()));
            }
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                System.out.printf("%s topic-partitions are assigned to this consumer\n", Arrays.toString(partitions.toArray()));
                Iterator<TopicPartition> topicPartitionIterator = partitions.iterator();
                while(topicPartitionIterator.hasNext()){
                    TopicPartition topicPartition = topicPartitionIterator.next();
                    System.out.println("Current offset is " + kafkaConsumer.position(topicPartition) + " committed offset is ->" + kafkaConsumer.committed(topicPartition) );
                    if(startingOffset ==0){
                        System.out.println("Setting offset to beginning");
                        kafkaConsumer.seekToBeginning(topicPartition);
                    }else if(startingOffset == -1){
                        System.out.println("Setting it to the end ");
                        kafkaConsumer.seekToEnd(topicPartition);
                    }else {
                        System.out.println("Resetting offset to " + startingOffset);
                        kafkaConsumer.seek(topicPartition, startingOffset);
                    }
                }
            }
        });
        //Start processing messages
        try {
            while (true) {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println(record.value());
                }

            }
        }catch(WakeupException ex){
            System.out.println("Exception caught " + ex.getMessage());
        }finally{
            kafkaConsumer.close();
            System.out.println("After closing KafkaConsumer");
        }
    }
    public KafkaConsumer<String,String> getKafkaConsumer(){
        return this.kafkaConsumer;
    }
}

 */

  def getResponse(reqId: String): json.JValue = {
    println("RECEIVING... " + reqId)
    //val tempProps = consumerProps
    //tempProps.put("group.id", UUID.randomUUID.toString)
    //val consumer = new KafkaConsumer[String, String](tempProps)
    //consumer.subscribe(util.Arrays.asList(responseTopic))
    while (true) {
      val consumerMap = consumer.poll(100)
      val records = consumerMap.records(responseTopic).iterator
      while (records.hasNext) {
      val record = records.next
      println("FILTERING..." + record)
      if (record.key == reqId)
        println("FOUND >>> " + record)
        return json.parse(record.value) \\ "data"
      }
    }
    json.parse("""{"error":"KafkaConsumer could not fetch response"}""")
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


  def processRequest(jsonRequest: JValue, reqId: String): JValue = {
    if (producer == null )
      producer = new KafkaProducer[String, String](producerProps)
    if (producer == null )
      return json.parse("""{"error":"kafka producer unavailable"}""")
    import scala.concurrent.ExecutionContext.Implicits.global
    val futureResponse = Future { getResponse(reqId) }
    try {
      val record = new ProducerRecord(requestTopic, reqId, json.compactRender(jsonRequest))
      producer.send(record).get
    } catch {
      case ex: InterruptedException => return json.parse("""{"error":"sending message to kafka interrupted"}""")
      case ex: ExecutionException => return json.parse("""{"error":"could not send message to kafka"}""")
      case _ =>
    }
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