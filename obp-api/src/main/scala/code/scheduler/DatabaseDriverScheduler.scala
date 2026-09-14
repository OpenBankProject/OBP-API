/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.scheduler

import java.sql.SQLException
import java.util.concurrent.TimeUnit

import code.actorsystem.ObpActorSystem
import code.util.Helper.MdcLoggable
import net.liftweb.db.{DB, SuperConnection}

import scala.concurrent.duration._


object DatabaseDriverScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
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
