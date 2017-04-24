package code.bankconnectors

import akka.actor.{ActorSystem, Props => ActorProps}
import code.actorsystem.ActorUtils.ActorUtils
import code.actorsystem.ObpActorConfig
import code.remotedata._
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory
import net.liftweb.util.Props

trait KafkaHelperActorInit extends ActorUtils {

  // Deafult is 3 seconds, which should be more than enough for slower systems
  ACTOR_TIMEOUT = Props.getLong("connector.timeout").openOr(3)

  val actorName = CreateActorNameFromClassName(this.getClass.getName)
  val actor = KafkaHelperActorSystem.getKafkaHelperActor(actorName)

  def CreateActorNameFromClassName(c: String): String = {
    val n = c.replaceFirst("^.*KafkaHelper", "")
    Character.toLowerCase(n.charAt(0)) + n.substring(1)
  }
}

object KafkaHelperActors extends MdcLoggable {

  val props_hostname = Helper.getHostname

  def startKafkaHelperActors(actorSystem: ActorSystem) = {
    val actorsKafkaHelper = Map(
      ActorProps[KafkaHelperActor]       -> KafkaHelperActor.actorName
    )
    actorsKafkaHelper.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

  def startLocalKafkaHelperWorkerSystem(): Unit = {
    logger.info("Starting local KafkaHelperActorSystem")
    logger.info(ObpActorConfig.localConf)
    val system = ActorSystem.create(s"KafkaHelperActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.localConf)))
    startKafkaHelperActors(system)
  }


}