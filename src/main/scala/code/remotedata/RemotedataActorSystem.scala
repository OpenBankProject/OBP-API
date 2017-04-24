package code.remotedata

import code.actorsystem.{ObpActorConfig, ObpActorSystem}
import net.liftweb.util.Props
import code.util.Helper.MdcLoggable


object RemotedataActorSystem extends ObpActorSystem with MdcLoggable {

  def getRemotedataActor(actorName: String) = {
    this.init

    val actorPath: String = Props.getBool("remotedata.enable", false) match {
    case true =>
      val hostname = ObpActorConfig.remoteHostname
      val port = ObpActorConfig.remotePort
      s"akka.tcp://RemotedataActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"

    case false =>
      val hostname = ObpActorConfig.localHostname
      var port = ObpActorConfig.localPort
      if (port == 0) {
        logger.error("Failed to connect to local Remotedata actor")
      }
      s"akka.tcp://RemotedataActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }

    this.obpActorSystem.actorSelection(actorPath)
  }

}



object KafkaHelperActorSystem extends ObpActorSystem with MdcLoggable {

  def getKafkaHelperActor(actorName: String) = {
    this.init

    val actorPath: String = {
        val hostname = ObpActorConfig.localHostname
        var port = ObpActorConfig.localPort
        if (port == 0) {
          logger.error("Failed to connect to local KafkaHelper actor")
        }
        s"akka.tcp://KafkaHelperActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }
    this.obpActorSystem.actorSelection(actorPath)
  }

}
