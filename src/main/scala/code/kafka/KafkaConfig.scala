package code.kafka

import java.util.UUID

import net.liftweb.util.Props

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

/**
  * Basic kafka configuration utility
  */
trait KafkaConfig {

  val bootstrapServers = Props.get("kafka.bootstrap_hosts")openOr("localhost:9092")

  val partitions = Props.getInt("kafka.partitions")openOr(10)


  val clientId = UUID.randomUUID().toString
  val groupId = "obp-socgen"//UUID.randomUUID().toString

  val autoOffsetResetConfig = "earliest"
  val maxWakeups = 50
  //TODO should be less then container's timeout
  val completionTimeout =  FiniteDuration(Props.getInt("kafka.akka.timeout", 2)*1000 - 450, MILLISECONDS)
}