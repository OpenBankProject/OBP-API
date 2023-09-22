package code.scheduler

import bootstrap.liftweb.CustomProtoDBVendor
import code.actorsystem.ObpLookupSystem
import code.util.Helper.MdcLoggable
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._


object DatabaseConnectionPoolScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpLookupSystem.obpLookupSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler

  def start(vendor: CustomProtoDBVendor, interval: Long): Unit = {
    scheduler.schedule(
      initialDelay = Duration(interval, TimeUnit.SECONDS),
      interval = Duration(interval, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          clearAllConnections(vendor)
//          vendor.logAllConnectionsStatus //This is only to be used for debugging .
        }
      }
    )
  }

  def clearAllConnections(vendor: CustomProtoDBVendor) = {
    val connectionBox = vendor.createOne
    try {
      if (connectionBox.isEmpty) {
        vendor.closeAllConnections_!()
        logger.debug("ThreadPoolConnectionsScheduler.clearAllConnections")
      }
    } catch {
      case e => logger.debug(s"ThreadPoolConnectionsScheduler.clearAllConnections() method throwed exception, details is $e")
    }finally {
      try {
        if (connectionBox.isDefined)
          connectionBox.head.close()
      } catch {
        case e =>logger.debug(s"ThreadPoolConnectionsScheduler.clearAllConnections.close method throwed exception, details is $e")
      }
    }
  }


}
