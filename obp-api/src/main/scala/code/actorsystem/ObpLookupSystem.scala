package code.actorsystem

import akka.actor.{ActorSelection, ActorSystem}
import code.api.util.APIUtil
import code.bankconnectors.LocalMappedOutInBoundTransfer
import code.bankconnectors.akka.actor.{AkkaConnectorActorConfig, AkkaConnectorHelperActor}
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.webhook.WebhookHelperActors
import com.openbankproject.adapter.akka.commons.config.AkkaConfig
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Full


object ObpLookupSystem extends ObpLookupSystem {
  this.init
}

trait ObpLookupSystem extends MdcLoggable {
  var obpLookupSystem: ActorSystem = null
  val props_hostname = Helper.getHostname

  def init (): ActorSystem = {
    if (obpLookupSystem == null ) {
      val system = ActorSystem("ObpLookupSystem", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.lookupConf)))
      logger.info(ObpActorConfig.lookupConf)
      obpLookupSystem = system
    }
    obpLookupSystem
  }

  def getKafkaActor(actorName: String) = {

    val actorPath: String = {
      val hostname = ObpActorConfig.localHostname
      val port = ObpActorConfig.localPort
      val props_hostname = Helper.getHostname
      if (port == 0) {
        logger.error("Failed to connect to local Kafka actor")
      }
      s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }

    this.obpLookupSystem.actorSelection(actorPath)
  }

  def getKafkaActorChild(actorName: String, actorChildName: String) = {
    val actorPath: String = {
      val hostname = ObpActorConfig.localHostname
      val port = ObpActorConfig.localPort
      val props_hostname = Helper.getHostname
      if (port == 0) {
        logger.error("Failed to connect to local Kafka actor")
      }
      s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}/${actorChildName}"
    }
    this.obpLookupSystem.actorSelection(actorPath)
  }

  def getRemotedataActor(actorName: String) = {

    val actorPath: String = APIUtil.getPropsAsBoolValue("remotedata.enable", false) match {
    case true =>
      val hostname = ObpActorConfig.remoteHostname
      val port = ObpActorConfig.remotePort
      val remotedata_hostname = Helper.getRemotedataHostname
      s"akka.tcp://RemotedataActorSystem_${remotedata_hostname}@${hostname}:${port}/user/${actorName}"

    case false =>
      val hostname = ObpActorConfig.localHostname
      val port = ObpActorConfig.localPort
      val props_hostname = Helper.getHostname
      if (port == 0) {
        logger.error("Failed to connect to local Remotedata actor")
      }
      s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }

    this.obpLookupSystem.actorSelection(actorPath)
  }

  /**
    * This function is a Single Point Of Entry for Webhook's Actor
    * I.e. we can obtain te Actor all over the code in next way:
    * {
    *   val actor: ActorSelection = ObpLookupSystem.getWebhookActor()
    * }
    *
    * @return An ActorSelection which is a logical view of a section of an ActorSystem's tree of Actors,
    *         allowing for broadcasting of messages to that section.
    */
  def getWebhookActor(): ActorSelection = {
    val name = WebhookHelperActors.actorName
    val actorPath: String = {
      val hostname = ObpActorConfig.localHostname
      val port = ObpActorConfig.localPort
      val props_hostname = Helper.getHostname
      if (port == 0) {
        logger.error("Failed to connect to local Webhook's actor")
      }
      s"akka.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${name}"
    }
    this.obpLookupSystem.actorSelection(actorPath)
  }


  def getAkkaConnectorActor(actorName: String) = {

    val hostname = APIUtil.getPropsValue("akka_connector.hostname")
    val port = APIUtil.getPropsValue("akka_connector.port")
    val embeddedAdapter = APIUtil.getPropsAsBoolValue("akka_connector.embedded_adapter", false)

    val actorPath: String = (hostname, port) match {
      case (Full(h), Full(p)) if !embeddedAdapter =>
        val hostname = h
        val port = p
        val akka_connector_hostname = Helper.getAkkaConnectorHostname
        s"akka.tcp://SouthSideAkkaConnector_${akka_connector_hostname}@${hostname}:${port}/user/${actorName}"

      case _ =>
        val hostname = AkkaConnectorActorConfig.localHostname
        val port = AkkaConnectorActorConfig.localPort
        val props_hostname = Helper.getHostname
        if (port == 0) {
          logger.error("Failed to find an available port.")
        }

        if(embeddedAdapter) {
          AkkaConfig(LocalMappedOutInBoundTransfer, Some(ObpActorSystem.northSideAkkaConnectorActorSystem))
        } else {
          AkkaConnectorHelperActor.startAkkaConnectorHelperActors(ObpActorSystem.northSideAkkaConnectorActorSystem)
        }

        s"akka.tcp://SouthSideAkkaConnector_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }
    this.obpLookupSystem.actorSelection(actorPath)
  }

}
