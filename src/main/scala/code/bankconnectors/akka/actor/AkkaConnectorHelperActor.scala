package code.bankconnectors.akka.actor

import akka.actor.{ActorSystem, Props}
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

object AkkaConnectorHelperActor extends MdcLoggable {
  
  val actorName = APIUtil.getPropsValue("akka_connector.name_of_actor", "akka-connector-actor")

  //This method is called in Boot.scala
  def startAkkaConnectorHelperActors(actorSystem: ActorSystem): Unit = {
    logger.info("***** Starting " + actorName + " at the North side *****")
    val actorsHelper = Map(
      Props[SouthSideActorOfAkkaConnector] -> actorName
    )
    actorsHelper.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

}
