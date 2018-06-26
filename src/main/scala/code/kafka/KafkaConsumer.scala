package code.kafka

object KafkaConsumer extends KafkaConfig {
    lazy val consumer001 = NorthSideConsumer(bootstrapServers, "", groupId + "-north.side.consumer", new NorthSideConsumerRecordProcessor())
}
