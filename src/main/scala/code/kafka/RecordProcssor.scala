package code.kafka

import code.actorsystem.ObpLookupSystem
import code.kafka.actor.RequestResponseActor.Response
import code.util.Helper.MdcLoggable
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
  * This class implements behavior of North Side Consumer
  * i.e. how the consumer processes a received Kafka message
  */
class NorthSideConsumerRecordProcessor extends RecordProcessorTrait[String, String] with MdcLoggable with KafkaHelper {
  override def processRecord(record: ConsumerRecord[String, String]): Unit = {
    val backendRequestId = record.key()
    val payload = record.value()
    logger.debug(s"kafka consumer :$record")
    // Try to find a child actor of "KafkaStreamsHelperActor" with a name equal to value of backendRequestId
    ObpLookupSystem.getKafkaActorChild(actorName, backendRequestId) ! Response(backendRequestId, payload)
  }
}
