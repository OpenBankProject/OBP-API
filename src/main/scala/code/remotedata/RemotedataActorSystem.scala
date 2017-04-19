package code.remotedata

import akka.actor.ActorSystem
import code.util.Helper
import com.typesafe.config.ConfigFactory
import net.liftweb.util.Props
import code.util.Helper.MdcLoggable


object RemotedataActorSystem extends MdcLoggable {

  var obpActorSystem: ActorSystem = null
  val props_hostname = Helper.getHostname

  def init () = {
    if (obpActorSystem == null ) {
      val system = ActorSystem("LookupSystem", ConfigFactory.load(ConfigFactory.parseString(RemotedataConfig.lookupConf)))
      logger.info(RemotedataConfig.lookupConf)
      obpActorSystem = system
    }
    obpActorSystem
  }


  def getActor(actorName: String) = {
    this.init

    val actorPath: String = Props.getBool("remotedata.enable", false) match {
    case true =>
      val hostname = RemotedataConfig.remoteHostname 
      val port = RemotedataConfig.remotePort
      s"akka.tcp://RemotedataActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"

    case false =>
      val hostname = RemotedataConfig.localHostname 
      var port = RemotedataConfig.localPort
      if (port == 0) {
        logger.error("Failed to connect to local Remotedata actor")
      }
      s"akka.tcp://RemotedataActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }

    this.obpActorSystem.actorSelection(actorPath)
  }

}

