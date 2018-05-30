package code.kafka

import code.api.util.{APIUtil, ErrorMessages}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

/**
  * Basic kafka configuration utility
  */
trait KafkaConfig {

  val bootstrapServers = APIUtil.getPropsValue("kafka.bootstrap_hosts")openOr("localhost:9092")
  val groupId = APIUtil.getPropsValue("Kafka.group.id").openOr("obp-api")
  val clientId = APIUtil.getPropsValue("Kafka.client.id").openOrThrowException(s"${ErrorMessages.MissingPropsValueAtThisInstance} Kafka.client.id") 
  val partitions = APIUtil.getPropsAsIntValue("kafka.partitions", 10)

  val autoOffsetResetConfig = "earliest"
  val maxWakeups = 50
  //TODO should be less then container's timeout
  val completionTimeout =  FiniteDuration(APIUtil.getPropsAsIntValue("kafka.akka.timeout", 2)*1000 - 450, MILLISECONDS)
}