package code.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

trait MessageProcessorTrait[K, V] {
  def processMessage(record: ConsumerRecord[K, V]): Unit
}
