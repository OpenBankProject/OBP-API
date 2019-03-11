package code.kafka

import akka.actor.{ActorSystem, Props => ActorProps}
import code.util.Helper
import code.util.Helper.MdcLoggable

object KafkaHelperActors extends MdcLoggable with KafkaHelper{

  val props_hostname = Helper.getHostname

  def startKafkaHelperActors(actorSystem: ActorSystem) = {
    // List all the ActorSystems used in Kafka, for now, we have Kafka and KafkaStreams
    val actorsKafkaHelper = Map(
      //ActorProps[KafkaHelperActor]       -> actorName //KafkaHelper.actorName, we use kafka-steam now.
      ActorProps[KafkaStreamsHelperActor]       -> actorName //KafkaHelper.actorName
    )
    //Create the actorSystem for all up list Kafka
    actorsKafkaHelper.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

  //This method is called in Boot.scala, when the OBP-API start, if the connector is Kafka_*, it will create the ActorSystem for Kafka
  def startLocalKafkaHelperWorkers(system: ActorSystem): Unit = {
    logger.info("Starting local KafkaHelper workers")
     startKafkaHelperActors(system)
  }

}
