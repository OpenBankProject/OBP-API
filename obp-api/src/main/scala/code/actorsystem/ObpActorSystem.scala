package code.actorsystem

import org.apache.pekko.actor.ActorSystem
import code.bankconnectors.akka.actor.AkkaConnectorActorConfig
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory


object ObpActorSystem extends MdcLoggable {

  val props_hostname = Helper.getHostname
  // @volatile so the single assignment of each actor system is visible to all reader threads
  // (the JVM memory model does not guarantee visibility of a non-volatile write across threads).
  @volatile var obpActorSystem: ActorSystem = _
  @volatile var northSideAkkaConnectorActorSystem: ActorSystem = _

  def startLocalActorSystem() = localActorSystem

  lazy val localActorSystem: ActorSystem = {
    logger.info("Starting local actor system")
    val localConf = ObpActorConfig.localConf
    logger.info(localConf)
    obpActorSystem = ActorSystem.create(s"ObpActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(localConf)))
    obpActorSystem
  }

  // synchronized double-checked init so concurrent callers start the connector system exactly once.
  def startNorthSideAkkaConnectorActorSystem(): ActorSystem = {
    if (northSideAkkaConnectorActorSystem == null) {
      synchronized {
        if (northSideAkkaConnectorActorSystem == null) {
          logger.info("Starting North Side Akka Connector actor system")
          val localConf = AkkaConnectorActorConfig.localConf
          logger.info(localConf)
          northSideAkkaConnectorActorSystem = ActorSystem.create(s"SouthSideAkkaConnector_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(localConf)))
        }
      }
    }
    northSideAkkaConnectorActorSystem
  }
}