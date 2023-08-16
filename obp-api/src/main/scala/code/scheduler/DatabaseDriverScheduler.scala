package code.scheduler

import java.sql.SQLException
import java.util.concurrent.TimeUnit

import code.actorsystem.ObpLookupSystem
import code.util.Helper.MdcLoggable
import net.liftweb.db.{DB, SuperConnection}

import scala.concurrent.duration._


object DatabaseDriverScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpLookupSystem.obpLookupSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler

  def start(interval: Long): Unit = {
    scheduler.schedule(
      initialDelay = Duration(interval, TimeUnit.SECONDS),
      interval = Duration(interval, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = clearAllMessages()
      }
    )
  }
  
  def logWarnings(conn: SuperConnection) = {
    var warning = conn.getWarnings()
    if (warning != null) {
      logger.warn("---Warning---")
      while (warning != null)
      {
        logger.warn("Message: " + warning.getMessage())
        logger.warn("SQLState: " + warning.getSQLState())
        logger.warn("Vendor error code: " + warning.getErrorCode())
        warning = warning.getNextWarning()
      }
    }
  }

  def clearAllMessages() = {
    DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
      conn => 
        try {
          logWarnings(conn)
          conn.clearWarnings()
          logger.warn("DatabaseDriverScheduler.clearAllMessages - DONE")
        } catch {
          case e: SQLException => 
            logger.warn("DatabaseDriverScheduler.clearAllMessages - UNSUCCESSFUL")
            logger.error(e)
        }
    }
  }


}
