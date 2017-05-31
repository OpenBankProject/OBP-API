package code.bankconnectors

import akka.actor.{ActorSystem, Props => ActorProps}
import code.util.Helper
import code.util.Helper.MdcLoggable

object KafkaHelperActors extends MdcLoggable {

  val props_hostname = Helper.getHostname

  def startKafkaHelperActors(actorSystem: ActorSystem) = {
    val actorsKafkaHelper = Map(
      //TODO remove KafkaHelperActor when KafkaStreamsHelperActor is stable
      //ActorProps[KafkaHelperActor]       -> "kafkaHelper" //KafkaHelper.actorName
      ActorProps[KafkaStreamsHelperActor]       -> "kafkaHelper" //KafkaHelper.actorName
    )
    actorsKafkaHelper.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

  def startLocalKafkaHelperWorkers(system: ActorSystem): Unit = {
    logger.info("Starting local KafkaHelper workers")
     startKafkaHelperActors(system)
  }

}
