package code.kafka

import akka.actor.ActorContext
import code.kafka.actor.RequestResponseActor.Response
import code.util.Helper.MdcLoggable
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
  * This class implements behavior of North Side Consumer
  * i.e. how the consumer processes a received Kafka message
  */
class NorthSideConsumerRecordProcessor extends RecordProcessorTrait[String, String] with MdcLoggable {
  override def processRecord(record: ConsumerRecord[String, String], actorContext: ActorContext): Unit = {
    val backendRequestId = record.key()
    val payload = record.value()
    logger.debug(s"kafka consumer :$record")
    // Try to find a child actor of "KafkaStreamsHelperActor" with a name equal to value of backendRequestId
    actorContext.child(backendRequestId) match {
      case  Some(c) => // In case it exists send the payload as a message to it
        c ! Response(backendRequestId, payload)
      case _ => // Otherwise log an error
        logger.error(s"Actor with name" + s" $backendRequestId cannot be found")
    }
  }
}
