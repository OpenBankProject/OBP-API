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
package code.retention

import java.util.Date

import code.api.util.APIUtil.generateUUID
import code.api.util.ErrorMessages.{RetentionArchiveIsDisabled, RetentionDeleteIsDisabled}
import code.retention.Retention._
import code.util.Helper.MdcLoggable
import net.liftweb.mapper._
import org.json4s.native.JsonMethods.{compact, render}

import scala.util.control.NonFatal

/** The active legal holds of one space, and the question every run asks of them. */
class RetentionHolds(holds: List[RetentionHold]) {
  /**
   * Is this resource on hold? A hold names one resource by id (and, for a dynamic entity record,
   * optionally the entity), or every record of one dynamic entity by its name alone.
   */
  def isHeld(resourceType: String, resourceId: String, entityName: Option[String]): Boolean =
    holds.exists(hold => hold.resourceType == resourceType && (hold.resourceId match {
      case Some(id) => id == resourceId && (hold.entityName.isEmpty || hold.entityName == entityName)
      case None => hold.entityName.isDefined && hold.entityName == entityName
    }))
}

object RetentionHolds {
  def active(bankId: String): RetentionHolds =
    new RetentionHolds(RetentionHold.findAll(By(RetentionHold.BankId, bankId), NullRef(RetentionHold.ReleasedAt)))
}

/**
 * What one execution of a run does to the archive and the disposal register, and what it counts.
 * The resource handlers call it for every resource they dispose of, the cascaded ones included.
 */
class Disposal(val run: RetentionRun, val policy: Option[RetentionPolicy], holds: RetentionHolds) {
  var archived = 0
  var anonymised = 0
  var deleted = 0
  var held = 0

  def countHeld(): Unit = held += 1

  def isHeld(resourceType: String, resourceId: String, entityName: Option[String]): Boolean =
    holds.isHeld(resourceType, resourceId, entityName)

  def isHeld(candidate: RetentionCandidate): Boolean =
    isHeld(candidate.resourceType, candidate.resourceId, candidate.entityName)

  /** Write the candidate's JSON copy (and a file's bytes) to the retention archive; its archive id. */
  def archive(candidate: RetentionCandidate, content: Option[Array[Byte]], parent: Option[(String, String)]): String = {
    val archiveId = generateUUID()
    val now = new Date()
    RetentionArchive.create
      .RetentionArchiveId(archiveId)
      .RetentionPolicyId(policy.map(_.retentionPolicyId).getOrElse(""))
      .RetentionRunId(run.retentionRunId)
      .BankId(candidate.bankId)
      .ResourceType(candidate.resourceType)
      .ResourceId(candidate.resourceId)
      .EntityName(candidate.entityName.getOrElse(""))
      .OwnerUserId(candidate.ownerUserId.getOrElse(""))
      .ParentResourceType(parent.map(_._1).getOrElse(""))
      .ParentResourceId(parent.map(_._2).getOrElse(""))
      .JsonCopy(compact(render(candidate.jsonCopy())))
      .HasContent(content.isDefined)
      .ContentSizeInBytes(content.map(_.length.toLong).getOrElse(0L))
      .ArchivedAt(now)
      .DeleteAfter(policy.flatMap(_.archiveDays).map(days => plusDays(now, days)).orNull)
      .saveMe()
    content.foreach(bytes => RetentionArchiveContent.create.RetentionArchiveId(archiveId)
      .ContentBase64(java.util.Base64.getEncoder.encodeToString(bytes)).saveMe())
    archiveId
  }

  /** Write one row of the disposal register, and count it. */
  def register(candidate: RetentionCandidate, action: String, archiveId: Option[String], parent: Option[(String, String)]): Unit = {
    writeItem(candidate.bankId, candidate.resourceType, candidate.resourceId, candidate.entityName, candidate.ownerUserId,
      action, archiveId, parent)
    action match {
      case ActionArchive => archived += 1
      case ActionAnonymise => anonymised += 1
      case _ => deleted += 1
    }
  }

  /** Register an archived resource deleted from the archive at the end of its archive period. */
  def registerDeletedFromArchive(entry: RetentionArchive): Unit = {
    writeItem(entry.bankId, entry.resourceType, entry.resourceId, entry.entityName, entry.ownerUserId,
      ActionDeleteFromArchive, Some(entry.retentionArchiveId),
      entry.parentResourceType.flatMap(t => entry.parentResourceId.map(t -> _)))
    deleted += 1
  }

  private def writeItem(bankId: String, resourceType: String, resourceId: String, entityName: Option[String],
                        ownerUserId: Option[String], action: String, archiveId: Option[String],
                        parent: Option[(String, String)]): Unit =
    RetentionRunItem.create
      .RetentionRunItemId(generateUUID())
      .RetentionRunId(run.retentionRunId)
      .RetentionPolicyId(policy.map(_.retentionPolicyId).getOrElse(""))
      .BankId(bankId)
      .ResourceType(resourceType)
      .ResourceId(resourceId)
      .EntityName(entityName.getOrElse(""))
      .OwnerUserId(ownerUserId.getOrElse(""))
      .Action(action)
      .ParentResourceType(parent.map(_._1).getOrElse(""))
      .ParentResourceId(parent.map(_._2).getOrElse(""))
      .RetentionArchiveId(archiveId.getOrElse(""))
      .DisposedAt(new Date())
      .saveMe()
}

/**
 * This object carries out retention: previews, runs, approvals and the deletion of archived
 * resources whose archive period has ended. The endpoints and RetentionScheduler both call it, so a
 * run started by hand and one started by the scheduler obey the same rules.
 */
object RetentionEngine extends MdcLoggable {

  /** The most resources a preview reads before it stops counting. */
  val PreviewScanLimit = 100000
  val SampleSize = 10
  /** A run still marked running after this long was cut short (the JVM died) and no longer blocks the next one. */
  val StaleRunMillis: Long = 6L * 3600L * 1000L

  case class PreviewResult(cutoff: Date, matched: Int, held: Int, oldestTriggerDate: Option[Date],
                           sampleResourceIds: List[String], archiveEntriesDue: Int, scanLimitReached: Boolean)

  def handlerOf(policy: RetentionPolicy): RetentionResourceHandler = RetentionResources.handlers(policy.resourceType)

  /** The retain days a run uses: the policy's, but never fewer than retention.minimum_retain_days. */
  def retainDaysOf(policy: RetentionPolicy): Int = math.max(policy.retainDays, RetentionProps.minimumRetainDays)

  /** What a run of the policy would dispose of now. Changes nothing. */
  def preview(policy: RetentionPolicy): PreviewResult = {
    val cutoff = Retention.cutoff(retainDaysOf(policy))
    val holds = RetentionHolds.active(policy.bankId)
    var matched = 0
    var held = 0
    var oldest: Option[Date] = None
    var samples = List.empty[String]
    handlerOf(policy).scan(policy, cutoff) { candidate =>
      matched += 1
      if (holds.isHeld(candidate.resourceType, candidate.resourceId, candidate.entityName)) held += 1
      else if (samples.size < SampleSize) samples = samples :+ candidate.resourceId
      val trigger = Option(candidate.triggerDate)
      if (trigger.exists(t => oldest.forall(t.before))) oldest = trigger
      matched < PreviewScanLimit
    }
    PreviewResult(cutoff, matched, held, oldest, samples, archiveEntriesDue(policy), matched >= PreviewScanLimit)
  }

  def archiveEntriesDue(policy: RetentionPolicy): Int =
    RetentionArchive.count(By(RetentionArchive.RetentionPolicyId, policy.retentionPolicyId),
      By_<=(RetentionArchive.DeleteAfter, new Date())).toInt

  /**
   * Why an execution of this policy must be refused on this instance: archiving or deleting is
   * switched off in the props. Anonymising is irreversible, so it counts as deleting. A preview is
   * never refused.
   */
  def refusal(policy: RetentionPolicy): Option[String] = policy.action match {
    case ActionArchive if !RetentionProps.archiveEnabled => Some(RetentionArchiveIsDisabled)
    case ActionDelete | ActionAnonymise if !RetentionProps.deleteEnabled => Some(RetentionDeleteIsDisabled)
    case _ => None
  }

  /** Is an execution of this policy under way? */
  def isRunning(policyId: String): Boolean =
    RetentionRun.find(By(RetentionRun.RetentionPolicyId, policyId), By(RetentionRun.Status, StatusRunning),
      By_>(RetentionRun.StartedAt, new Date(System.currentTimeMillis() - StaleRunMillis))).isDefined

  private def newRun(bankId: String, policyId: Option[String], mode: String, status: String,
                     requestedByUserId: String, message: String): RetentionRun = {
    val now = new Date()
    RetentionRun.create
      .RetentionRunId(generateUUID())
      .RetentionPolicyId(policyId.getOrElse(""))
      .BankId(bankId)
      .Mode(mode)
      .Status(status)
      .RequestedByUserId(requestedByUserId)
      .RequestedAt(now)
      .StartedAt(if (status == StatusRunning) now else null)
      .EndedAt(if (status == StatusRefused) now else null)
      .Message(message)
      .saveMe()
  }

  /**
   * Run the policy now, for `requestedByUserId` (empty for the scheduler). A preview records its
   * counts and changes nothing. An execution is refused when the props switch its action off, waits
   * as pending when the policy requires approval, and otherwise runs straight away.
   */
  def requestRun(policy: RetentionPolicy, mode: String, requestedByUserId: String): RetentionRun =
    if (mode == ModePreview) {
      val run = newRun(policy.bankId, Some(policy.retentionPolicyId), ModePreview, StatusRunning, requestedByUserId, "")
      val result = preview(policy)
      run.MatchedCount(result.matched).HeldCount(result.held).Status(StatusDone).EndedAt(new Date())
        .Message(s"Preview only: ${result.matched - result.held} resource(s) would be disposed of by '${policy.action}', ${result.held} on hold." +
          (if (result.scanLimitReached) s" Counting stopped at $PreviewScanLimit." else ""))
        .saveMe()
    } else refusal(policy) match {
      case Some(reason) =>
        newRun(policy.bankId, Some(policy.retentionPolicyId), ModeExecute, StatusRefused, requestedByUserId, reason)
      case None if policy.requiresApproval =>
        newRun(policy.bankId, Some(policy.retentionPolicyId), ModeExecute, StatusPending, requestedByUserId,
          "This policy requires approval: the run starts when a different user approves it.")
      case None =>
        execute(newRun(policy.bankId, Some(policy.retentionPolicyId), ModeExecute, StatusRunning, requestedByUserId, ""), policy)
    }

  /**
   * Approve or reject a pending run. The caller has already checked that the approver is not the
   * user who requested it. An approved run executes now, unless the props have switched its action
   * off since it was requested, in which case it is refused.
   */
  def decide(run: RetentionRun, policy: Option[RetentionPolicy], approverUserId: String, approve: Boolean,
             comment: Option[String]): RetentionRun = {
    val decided = run.ApprovedByUserId(approverUserId)
      .ApprovalDecision(if (approve) DecisionApproved else DecisionRejected)
      .ApprovalComment(comment.getOrElse(""))
    if (!approve) decided.Status(StatusRefused).EndedAt(new Date()).Message("Rejected by the approver.").saveMe()
    else policy match {
      case None =>
        decided.Status(StatusRefused).EndedAt(new Date()).Message("The policy of this run has been deleted.").saveMe()
      case Some(p) => refusal(p) match {
        case Some(reason) => decided.Status(StatusRefused).EndedAt(new Date()).Message(reason).saveMe()
        case None => execute(decided.Status(StatusRunning).StartedAt(new Date()).saveMe(), p)
      }
    }
  }

  /**
   * Execute a run: dispose of what is due, up to retention.run.max_items resources, skipping what is
   * on hold; then delete from the archive what this policy archived and whose archive period has
   * ended. A resource that fails is left in place and counted; the run then ends as failed.
   */
  def execute(run: RetentionRun, policy: RetentionPolicy): RetentionRun = {
    val disposal = new Disposal(run, Some(policy), RetentionHolds.active(policy.bankId))
    var matched = 0
    var failed = 0
    var limitReached = false
    val maxItems = RetentionProps.maxItemsPerRun
    try {
      handlerOf(policy).scan(policy, Retention.cutoff(retainDaysOf(policy))) { candidate =>
        matched += 1
        if (disposal.isHeld(candidate)) disposal.countHeld()
        else try {
          if (!candidate.dispose(policy.action, disposal)) disposal.countHeld()
        } catch {
          case NonFatal(e) =>
            failed += 1
            logger.error(s"RetentionEngine: could not ${policy.action} ${candidate.resourceType} ${candidate.resourceId} " +
              s"(policy ${policy.retentionPolicyId}, run ${run.retentionRunId})", e)
        }
        limitReached = matched >= maxItems
        !limitReached
      }
      val archiveNote =
        if (RetentionProps.deleteEnabled) {
          deleteDueFromArchive(disposal, List(By(RetentionArchive.RetentionPolicyId, policy.retentionPolicyId)))
          ""
        } else if (archiveEntriesDue(policy) > 0)
          " Archived resources whose archive period has ended were kept, because retention.delete.enabled is false."
        else ""
      val message =
        (if (failed > 0) s"$failed resource(s) could not be disposed of and were left in place." else "Done.") +
          (if (limitReached) s" The run stopped at retention.run.max_items ($maxItems); the next run continues." else "") +
          archiveNote
      finish(run, disposal, matched, failed, if (failed > 0) StatusFailed else StatusDone, message)
    } catch {
      case NonFatal(e) =>
        logger.error(s"RetentionEngine: run ${run.retentionRunId} of policy ${policy.retentionPolicyId} failed", e)
        finish(run, disposal, matched, failed, StatusFailed, Option(e.getMessage).getOrElse(e.toString))
    }
  }

  private def finish(run: RetentionRun, disposal: Disposal, matched: Int, failed: Int, status: String, message: String): RetentionRun =
    run.MatchedCount(matched)
      .ArchivedCount(disposal.archived)
      .AnonymisedCount(disposal.anonymised)
      .DeletedCount(disposal.deleted)
      .HeldCount(disposal.held)
      .FailedCount(failed)
      .Status(status)
      .EndedAt(new Date())
      .Message(message)
      .saveMe()

  /** Delete the archive entries matching `filter` whose archive period has ended, unless on hold. */
  private def deleteDueFromArchive(disposal: Disposal, filter: List[QueryParam[RetentionArchive]]): Unit = {
    val due = RetentionArchive.findAll((filter ::: List(By_<=(RetentionArchive.DeleteAfter, new Date()),
      OrderBy(RetentionArchive.DeleteAfter, Ascending), MaxRows[RetentionArchive](RetentionProps.maxItemsPerRun))): _*)
    due.foreach { entry =>
      val parentHeld = entry.parentResourceType.exists(t => entry.parentResourceId.exists(id => disposal.isHeld(t, id, entry.entityName)))
      if (disposal.isHeld(entry.resourceType, entry.resourceId, entry.entityName) || parentHeld) disposal.countHeld()
      else {
        RetentionArchiveContent.bulkDelete_!!(By(RetentionArchiveContent.RetentionArchiveId, entry.retentionArchiveId))
        entry.delete_!
        disposal.registerDeletedFromArchive(entry)
      }
    }
  }

  /**
   * Delete from the archive every entry whose archive period has ended, whichever policy archived it,
   * so that deleting a policy never leaves its archive behind. One run per space is recorded, with no
   * policy. Only called with retention.delete.enabled true.
   */
  def deleteAllDueFromArchive(): List[RetentionRun] = {
    val banks = RetentionArchive.findAll(By_<=(RetentionArchive.DeleteAfter, new Date()),
      MaxRows[RetentionArchive](RetentionProps.maxItemsPerRun)).map(_.bankId).distinct
    banks.map { bankId =>
      val run = newRun(bankId, None, ModeExecute, StatusRunning, "", "")
      val disposal = new Disposal(run, None, RetentionHolds.active(bankId))
      try {
        deleteDueFromArchive(disposal, List(By(RetentionArchive.BankId, bankId)))
        finish(run, disposal, 0, 0, StatusDone, "Archived resources whose archive period has ended were deleted from the archive.")
      } catch {
        case NonFatal(e) =>
          logger.error(s"RetentionEngine: deleting from the archive of $bankId failed", e)
          finish(run, disposal, 0, 1, StatusFailed, Option(e.getMessage).getOrElse(e.toString))
      }
    }
  }

  /** Has the policy anything to do now? Reads no further than the first resource not on hold. */
  def hasWork(policy: RetentionPolicy): Boolean = {
    val holds = RetentionHolds.active(policy.bankId)
    var found = false
    handlerOf(policy).scan(policy, Retention.cutoff(retainDaysOf(policy))) { candidate =>
      found = !holds.isHeld(candidate.resourceType, candidate.resourceId, candidate.entityName)
      !found
    }
    found || (RetentionProps.deleteEnabled && archiveEntriesDue(policy) > 0)
  }
}
