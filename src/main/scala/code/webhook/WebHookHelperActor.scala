package code.webhook

import akka.actor.{ActorSystem, Props => ActorProps}
import code.util.Helper
import code.util.Helper.MdcLoggable

object WebHookHelperActors extends MdcLoggable {

  val props_hostname = Helper.getHostname
  val actorName = "Web-Hook-Actor"

  //This method is called in Boot.scala
  def startLocalWebHookHelperWorkers(system: ActorSystem): Unit = {
    logger.info("Starting local WebHookHelperActors workers")
    startWebHookHelperActors(system)
  }

  def startWebHookHelperActors(actorSystem: ActorSystem): Unit = {
    val actorsHelper = Map(
      ActorProps[WebHookActor] -> actorName
    )
    actorsHelper.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

}
