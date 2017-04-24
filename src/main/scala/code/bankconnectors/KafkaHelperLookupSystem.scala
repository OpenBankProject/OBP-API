package code.bankconnectors

import code.actorsystem.{ObpActorConfig, ObpLookupSystem}
import code.util.Helper.MdcLoggable


object KafkaHelperLookupSystem extends ObpLookupSystem with MdcLoggable {

  def getKafkaHelperActor(actorName: String) = {

    val actorPath: String = {
        val hostname = ObpActorConfig.localHostname
        val port = ObpActorConfig.localPort
        if (port == 0) {
          logger.error("Failed to connect to local KafkaHelper actor")
        }
        s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }
    this.obpLookupSystem.actorSelection(actorPath)
  }

}