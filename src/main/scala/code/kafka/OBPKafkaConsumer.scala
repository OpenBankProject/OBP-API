package code.kafka

object OBPKafkaConsumer extends KafkaConfig {
    lazy val primaryConsumer = NorthSideConsumer(bootstrapServers, "", groupId + "-north.side.consumer", new NorthSideConsumerMessageProcessor())
}
