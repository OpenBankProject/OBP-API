package code.actorsystem

import akka.actor.ActorSystem
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory


trait ObpActorSystem extends MdcLoggable {
  var obpActorSystem: ActorSystem = null
  val props_hostname = Helper.getHostname

  def init () = {
    if (obpActorSystem == null ) {
      val system = ActorSystem("LookupSystem", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.lookupConf)))
      logger.info(ObpActorConfig.lookupConf)
      obpActorSystem = system
    }
    obpActorSystem
  }

}