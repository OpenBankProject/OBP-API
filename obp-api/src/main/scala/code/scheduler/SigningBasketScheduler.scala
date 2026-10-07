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

import code.api.berlin.group.v1_3.SigningBasketExecution
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Resumes signing basket executions that stopped, for instance because the process that was booking
 * the payments died. See SigningBasketExecution for what resuming does and does not do.
 *
 * The interval is in `signing_basket_resume_interval_in_seconds` (default 593; 0 switches it off) and the
 * lease, the time a basket or member may sit without moving before it counts as stopped, in
 * `signing_basket_execution_lease_in_seconds` (default 300).
 */
object SigningBasketScheduler extends MdcLoggable {

  def startAll(): Unit = {
    val interval = APIUtil.getPropsAsIntValue("signing_basket_resume_interval_in_seconds", 593)
    if (interval > 0) {
      val lease = APIUtil.getPropsAsIntValue("signing_basket_execution_lease_in_seconds", 300)
      SchedulerUtil.startTask(interval = interval, () => resume(lease), initialDelay = 30)
    } else {
      logger.warn("|---> Skipping resumeSigningBasketExecutions task: signing_basket_resume_interval_in_seconds set to 0")
    }
  }

  private def resume(leaseSeconds: Int): Unit =
    Try(Await.result(SigningBasketExecution.resumePending(leaseSeconds, limit = 20), 5.minutes)) match {
      case Success(0) => logger.debug("|---> No signing basket execution to resume")
      case Success(n) => logger.info(s"|---> Looked at $n signing basket execution(s) that had stopped")
      case Failure(error) => logger.error("Error in resumeSigningBasketExecutions!", error)
    }
}
