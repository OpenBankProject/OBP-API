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

import code.api.berlin.group.v1_3.model.TransactionStatus
import code.api.util.APIUtil
import code.transactionrequests.MappedTransactionRequest
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.{By, By_<}

import scala.util.{Failure, Success, Try}


object TransactionScheduler extends MdcLoggable {

  // Starts multiple scheduled tasks with different intervals
  def startAll(): Unit = {
    var initialDelay = 0

    // Berlin Group
    APIUtil.getPropsAsIntValue("berlin_group_outdated_transactions_interval_in_seconds") match {
      case Full(interval) if interval > 0 =>
        val time = APIUtil.getPropsAsIntValue("berlin_group_outdated_transactions_time_in_seconds", 300)
        SchedulerUtil.startTask(interval = interval, () => outdatedBerlinGroupTransactions(time)) // Runs periodically
        initialDelay = initialDelay + 10
      case _ =>
        logger.warn("|---> Skipping outdatedBerlinGroupTransactions task: berlin_group_outdated_transactions_interval_in_seconds not set or invalid")
    }
  }

  private def outdatedBerlinGroupTransactions(seconds: Int): Unit = {
    Try {
      logger.debug("|---> Checking for OUTDATED Berlin Group TRANSACTIONS...")

      val outdatedTransactions = MappedTransactionRequest.findAll(
        By(MappedTransactionRequest.mStatus, TransactionStatus.RCVD.toString),
        By_<(MappedTransactionRequest.updatedAt, SchedulerUtil.someSecondsAgo(seconds))
      )

      logger.debug(s"|---> Found ${outdatedTransactions.size} outdated transactions")

      outdatedTransactions.foreach { transaction =>
        Try {
          transaction.mStatus(TransactionStatus.RJCT.toString).save
          logger.warn(s"|---> Changed status to ${TransactionStatus.RJCT.toString} for transaction ID: ${transaction.id}")
        } match {
          case Failure(ex) => logger.error(s"Failed to update transaction ID: ${transaction.id}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in outdatedBerlinGroupTransactions!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }

}
