package code.actorsystem

import akka.actor.ActorSystem
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory


object ObpLookupSystem extends ObpLookupSystem {

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

}
