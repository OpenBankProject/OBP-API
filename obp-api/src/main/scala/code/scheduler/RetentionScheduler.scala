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

import java.util.concurrent.TimeUnit

import code.actorsystem.ObpActorSystem
import code.api.Constant
import code.retention.Retention.{ModeExecute, StatusPending}
import code.retention.{RetentionEngine, RetentionPolicy, RetentionProps, RetentionRun}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * Runs the enabled retention policies on a timer, modelled on [[MetricsArchiveScheduler]]. Started
 * from Boot only when retention.scheduler.enabled is true, every
 * retention.scheduler.interval_in_seconds.
 *
 * Each tick, for every enabled policy with something due: a policy that does not require approval
 * is executed, unless the props switch its action off (then it is skipped and logged, not recorded
 * as a refused run every tick); a policy that requires approval gets one pending run, which waits
 * for a user to approve it. Then, with retention.delete.enabled, archived resources whose archive
 * period has ended are deleted from the archive.
 *
 * A JobScheduler lock row keeps two nodes, or two ticks, from running at once.
 */
object RetentionScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
  implicit lazy val executor: scala.concurrent.ExecutionContext = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val jobName = "RetentionScheduler"
  private val apiInstanceId = Constant.ApiInstanceId

  /** Start the scheduler when retention.scheduler.enabled is true (it is false by default). Returns whether it started. */
  def startIfEnabled(): Boolean =
    if (RetentionProps.schedulerEnabled) {
      start(intervalInSeconds = RetentionProps.schedulerIntervalInSeconds)
      true
    } else {
      logger.info("RetentionScheduler not started: retention.scheduler.enabled is false")
      false
    }

  def start(intervalInSeconds: Long): Unit = {
    logger.info(s"RetentionScheduler.start: every $intervalInSeconds seconds")
    // This instance cannot have a run in progress at boot, so a lock row of its own is a leftover.
    JobScheduler.findAllByApiInstanceId(apiInstanceId).filter(_.name == jobName).foreach(JobScheduler.delete)
    scheduler.schedule(
      initialDelay = Duration(intervalInSeconds, TimeUnit.SECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = { runOnce(); () }
      }
    )
  }

  /** One tick. Returns false when it was skipped because another tick holds the lock. */
  def runOnce(): Boolean =
    JobScheduler.findByName(jobName) match {
      case Full(job) =>
        logger.info(s"RetentionScheduler.runOnce skipped: job ${job.jobId} started at ${job.createdAt} on ${job.apiInstanceId} is in progress")
        false
      case _ =>
        val job = JobScheduler.createJob(jobName, apiInstanceId)
        try {
          RetentionPolicy.findAllEnabled().foreach(runPolicy)
          if (RetentionProps.deleteEnabled) RetentionEngine.deleteAllDueFromArchive()
        } catch {
          case NonFatal(e) => logger.error("RetentionScheduler.runOnce failed", e)
        } finally {
          JobScheduler.delete(job)
        }
        true
    }

  private def runPolicy(policy: RetentionPolicy): Unit =
    try {
      val id = policy.retentionPolicyId
      if (RetentionEngine.isRunning(id)) logger.info(s"RetentionScheduler: policy $id skipped, a run is in progress")
      else if (RetentionEngine.refusal(policy).isDefined)
        logger.info(s"RetentionScheduler: policy $id skipped: ${RetentionEngine.refusal(policy).getOrElse("")}")
      else if (!RetentionEngine.hasWork(policy)) ()
      else if (policy.requiresApproval) {
        val alreadyPending = RetentionRun.existsByRetentionPolicyIdAndStatus(id, StatusPending)
        if (!alreadyPending) {
          val run = RetentionEngine.requestRun(policy, ModeExecute, "")
          logger.info(s"RetentionScheduler: policy $id has resources due; run ${run.retentionRunId} is waiting for approval")
        }
      } else {
        val run = RetentionEngine.requestRun(policy, ModeExecute, "")
        logger.info(s"RetentionScheduler: policy $id run ${run.retentionRunId} ended ${run.status}: ${run.message}")
      }
    } catch {
      case NonFatal(e) => logger.error(s"RetentionScheduler: policy ${policy.retentionPolicyId} failed", e)
    }
}
