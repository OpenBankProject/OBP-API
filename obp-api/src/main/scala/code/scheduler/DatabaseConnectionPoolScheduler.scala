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
        }
      }
    )
  }

  def clearAllConnections(vendor: CustomProtoDBVendor) = {
    //if the connection is Failure or empty, both is true
    if (vendor.createOne.isEmpty) {
      vendor.closeAllConnections_!()
      logger.debug("ThreadPoolConnectionsScheduler.clearAllConnections")
    }
  }


}
