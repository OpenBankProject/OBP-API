package code.webhook

import akka.actor.{ActorSystem, Props => ActorProps}
import code.util.Helper
import code.util.Helper.MdcLoggable

object WebhookHelperActors extends MdcLoggable {

  val props_hostname = Helper.getHostname
  val actorName = "Webhook-Actor"

  //This method is called in Boot.scala
  def startLocalWebhookHelperWorkers(system: ActorSystem): Unit = {
    logger.info("Starting local WebhookHelperActors workers")
    startWebhookHelperActors(system)
  }

  def startWebhookHelperActors(actorSystem: ActorSystem): Unit = {
    val actorsHelper = Map(
      ActorProps[WebhookActor] -> actorName
    )
    actorsHelper.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

}
