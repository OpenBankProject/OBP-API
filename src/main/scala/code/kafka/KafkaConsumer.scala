package code.kafka

object KafkaConsumer extends KafkaConfig {
    lazy val primaryConsumer = NorthSideConsumer(bootstrapServers, "", groupId + "-north.side.consumer", new NorthSideConsumerMessageProcessor())
}
