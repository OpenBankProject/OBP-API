package code.kafka

import akka.actor.ActorContext
import org.apache.kafka.clients.consumer.ConsumerRecord

trait RecordProcessorTrait[K, V] {
  def processRecord(record: ConsumerRecord[K, V], actorContext: ActorContext): Unit
}
