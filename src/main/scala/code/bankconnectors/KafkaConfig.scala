package code.bankconnectors

import java.util.UUID

import net.liftweb.util.Props

import scala.concurrent.duration.{FiniteDuration, SECONDS, MILLISECONDS}

/**
  * Basic kafka configuration utility
  */
trait KafkaConfig {

  val bootstrapServers = Props.get("kafka.bootstrap_hosts")openOr("localhost:9092")

  val clientId = UUID.randomUUID().toString
  val groupId = UUID.randomUUID().toString

  val autoOffsetResetConfig = "earliest"
  val maxWakeups = 50
  //should be less then container's timeout
  val completionTimeout =  FiniteDuration(1000, MILLISECONDS)//Props.getInt("kafka.akka.timeout", 3), SECONDS)
}