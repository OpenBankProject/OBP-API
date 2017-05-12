package code.actorsystem

import akka.actor.ActorSystem
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory


object ObpActorSystem extends MdcLoggable {

  val props_hostname = Helper.getHostname
  var obpActorSystem: ActorSystem = _

  def startLocalActorSystem(): ActorSystem = {
    logger.info("Starting local actor system")
    logger.info(ObpActorConfig.localConf)
    obpActorSystem = ActorSystem.create(s"ObpActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.localConf)))
    obpActorSystem
  }
}