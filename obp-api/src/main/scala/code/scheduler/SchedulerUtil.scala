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


import code.actorsystem.ObpActorSystem

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}
import scala.concurrent.duration._
object SchedulerUtil {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler

  // Generic method to schedule a task
  def startTask(interval: Long, task: () => Unit, initialDelay: Long = 0): Unit = {
    scheduler.schedule(
      initialDelay = Duration(initialDelay, TimeUnit.SECONDS),
      interval = Duration(interval, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = task()
      }
    )
  }

  // Calculate the timestamp 5 minutes ago
  def someSecondsAgo(seconds: Int): Date = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.SECOND, -seconds)
    cal.getTime
  }

}
