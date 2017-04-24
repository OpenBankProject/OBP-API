package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props => ActorProps}
import bootstrap.liftweb.ToSchemify
import code.actorsystem.ActorUtils
import code.actorsystem.ObpActorConfig
import code.util.Helper
import com.typesafe.config.ConfigFactory
import net.liftweb.common._
import net.liftweb.db.StandardDBVendor
import net.liftweb.http.LiftRules
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.Props

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import code.util.Helper.MdcLoggable



trait RemotedataActorInit extends ActorUtils {

  // Deafult is 3 seconds, which should be more than enough for slower systems
  ACTOR_TIMEOUT = Props.getLong("remotedata.timeout").openOr(3)

  val actorName = CreateActorNameFromClassName(this.getClass.getName)
  val actor = RemotedataLookupSystem.getRemotedataActor(actorName)

  def CreateActorNameFromClassName(c: String): String = {
    val n = c.replaceFirst("^.*Remotedata", "")
    Character.toLowerCase(n.charAt(0)) + n.substring(1)
  }
}


object RemotedataActors extends MdcLoggable {

  val props_hostname = Helper.getHostname

  def startRemotedataActors(actorSystem: ActorSystem) = {

    val actorsRemotedata = Map(
      ActorProps[RemotedataAccountHoldersActor]       -> RemotedataAccountHolders.actorName,
      ActorProps[RemotedataCommentsActor]             -> RemotedataComments.actorName,
      ActorProps[RemotedataCounterpartiesActor]       -> RemotedataCounterparties.actorName,
      ActorProps[RemotedataTagsActor]                 -> RemotedataTags.actorName,
      ActorProps[RemotedataUsersActor]                -> RemotedataUsers.actorName,
      ActorProps[RemotedataViewsActor]                -> RemotedataViews.actorName,
      ActorProps[RemotedataWhereTagsActor]            -> RemotedataWhereTags.actorName,
      ActorProps[RemotedataTransactionImagesActor]    -> RemotedataTransactionImages.actorName,
      ActorProps[RemotedataNarrativesActor]           -> RemotedataNarratives.actorName,
      ActorProps[RemotedataCustomersActor]            -> RemotedataCustomers.actorName,
      ActorProps[RemotedataUserCustomerLinksActor]    -> RemotedataUserCustomerLinks.actorName,
      ActorProps[RemotedataConsumersActor]            -> RemotedataConsumers.actorName,
      ActorProps[RemotedataTransactionRequestsActor]  -> RemotedataTransactionRequests.actorName,
      ActorProps[RemotedataMetricsActor]              -> RemotedataMetrics.actorName,
      ActorProps[RemotedataTokensActor]               -> RemotedataTokens.actorName,
      ActorProps[RemotedataNoncesActor]               -> RemotedataNonces.actorName,
      ActorProps[RemotedataConnectorMetricsActor]     -> RemotedataConnectorMetrics.actorName,
      ActorProps[RemotedataSanityCheckActor]          -> RemotedataSanityCheck.actorName
    )

    actorsRemotedata.foreach { a => logger.info(actorSystem.actorOf(a._1, name = a._2)) }
  }

  def startLocalRemotedataWorkers( system: ActorSystem ): Unit = {
    logger.info("Starting local Remotedata actors")
    //logger.info(ObpActorConfig.localConf)
    //val system = ActorSystem.create(s"RemotedataActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.localConf)))
    startRemotedataActors(system)
  }

  def startRemoteWorkerSystem(): Unit = {
    logger.info("Starting remote RemotedataLookupSystem")
    logger.info(ObpActorConfig.remoteConf)
    val system = ActorSystem(s"RemotedataActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.remoteConf)))
    startRemotedataActors(system)
    logger.info("Started")
  }

  def setupRemotedataDB(): Unit = {
    // set up the way to connect to the relational DB we're using (ok if other connector than relational)
    if (!DB.jndiJdbcConnAvailable_?) {
      val driver =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development => Props.get("remotedata.db.driver") openOr "org.h2.Driver"
          case _ => "org.h2.Driver"
        }
      val vendor =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development =>
            new StandardDBVendor(driver,
              Props.get("remotedata.db.url") openOr "jdbc:h2:./lift_proto.remotedata.db;AUTO_SERVER=TRUE",
              Props.get("remotedata.db.user"), Props.get("remotedata.db.password"))
          case _ =>
            new StandardDBVendor(
              driver,
              "jdbc:h2:mem:OBPData;DB_CLOSE_DELAY=-1",
              Empty, Empty)
        }

      logger.debug("Using database driver: " + driver)
      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, vendor)
    }
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)
  }

  // Entry point if running as standalone remote data server, without jetty
  def main (args: Array[String]): Unit = {
    if (args.length >= 1 && args(0) == "standalone") {
      setupRemotedataDB()
      showLogoAfterDelay()
      startRemoteWorkerSystem()
    }
  }

  def showLogoAfterDelay() = {
    val actorSystem = ActorSystem()
    val scheduler = actorSystem.scheduler
    scheduler.scheduleOnce(
      Duration(4, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          println(
            """
              |     ______    _______    _______                 __         _______   __
              |    /    " \  |   _  "\  |   __ "\               /""\       |   __ "\ |" \
              |   // ____  \ (. |_)  :) (. |__) :)   _____     /    \      (. |__) :)||  |
              |  /  /    ) :)|:     \/  |:  ____/   //   ")   /' /\  \     |:  ____/ |:  |
              | (: (____/ // (|  _  \\  (|  /      (_____/   //  __'  \    (|  /     |.  |
              |  \        /  |: |_)  :)/|__/ \              /   /  \\  \  /|__/ \    /\  |\
              |   \"_____/   (_______/(_______)            (___/    \___)(_______)  (__\_|_)
              |       _______    _______  ___      ___     ______  ___________  _______
              |      /"      \  /"     "||"  \    /"  |   /    " \("     _   ")/"     "|
              |     |:        |(: ______) \   \  //   |  // ____  \)__/  \\__/(: ______)
              |     |_____/   ) \/    |   /\\  \/.    | /  /    ) :)  \\_ /    \/    |
              |      //      /  // ___)_ |: \.        |(: (____/ //   |.  |    // ___)_
              |     |:  __   \ (:      "||.  \    /:  | \        /    \:  |   (:      "|
              |     |__|  \___) \_______)|___|\__/|___|  \"_____/      \__|    \_______)
              |               __       ______  ___________  ______     _______
              |              /""\     /" _  "\("     _   ")/    " \   /"      \
              |             /    \   (: ( \___))__/  \\__/// ____  \ |:        |
              |            /' /\  \   \/ \        \\_ /  /  /    ) :)|_____/   )
              |           //  __'  \  //  \ _     |.  | (: (____/ //  //      /
              |          /   /  \\  \(:   _) \    \:  |  \        /  |:  __   \
              |         (___/    \___)\_______)    \__|   \"_____/   |__|  \___)
              |""".stripMargin)
        }
      }
    )
  }

}
