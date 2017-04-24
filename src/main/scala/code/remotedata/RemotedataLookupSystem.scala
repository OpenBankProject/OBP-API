package code.remotedata

import code.actorsystem.{ObpActorConfig, ObpLookupSystem}
import net.liftweb.util.Props
import code.util.Helper.MdcLoggable


object RemotedataLookupSystem extends ObpLookupSystem with MdcLoggable {

  def getRemotedataActor(actorName: String) = {

    val actorPath: String = Props.getBool("remotedata.enable", false) match {

    case true =>
      val hostname = ObpActorConfig.remoteHostname
      val port = ObpActorConfig.remotePort
      s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"

    case false =>
      val hostname = ObpActorConfig.localHostname
      var port = ObpActorConfig.localPort
      if (port == 0) {
        logger.error("Failed to connect to local Remotedata actor")
      }
      s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }

    this.obpLookupSystem.actorSelection(actorPath)
  }

}


