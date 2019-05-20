package code.actorsystem

import akka.actor.ActorSystem
import code.bankconnectors.akka.actor.AkkaConnectorActorConfig
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory


object ObpActorSystem extends MdcLoggable {

  val props_hostname = Helper.getHostname
  var obpActorSystem: ActorSystem = _
  var northSideAkkaConnectorActorSystem: ActorSystem = _

  def startLocalActorSystem() = localActorSystem

  lazy val localActorSystem: ActorSystem = {
    logger.info("Starting local actor system")
    val localConf = ObpActorConfig.localConf
    logger.info(localConf)
    obpActorSystem = ActorSystem.create(s"ObpActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(localConf)))
    obpActorSystem
  }
  
  def startNorthSideAkkaConnectorActorSystem(): ActorSystem = {
    logger.info("Starting North Side Akka Connector actor system")
    val localConf = AkkaConnectorActorConfig.localConf
    logger.info(localConf)
    northSideAkkaConnectorActorSystem = ActorSystem.create(s"SouthSideAkkaConnector_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(localConf)))
    northSideAkkaConnectorActorSystem
  }
}